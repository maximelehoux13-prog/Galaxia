package com.gtnewhorizons.galaxia.outpost.logistics;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.outpost.AutomatedOutpostModule;
import com.gtnewhorizons.galaxia.outpost.AutomatedOutpostState;
import com.gtnewhorizons.galaxia.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.outpost.LogisticsConfiguration;
import com.gtnewhorizons.galaxia.outpost.LogisticsResourceConfig;
import com.gtnewhorizons.galaxia.outpost.OutpostModuleKind;
import com.gtnewhorizons.galaxia.outpost.module.HammerModuleData;
import com.gtnewhorizons.galaxia.outpost.network.OutpostFullSyncPacket;
import com.gtnewhorizons.galaxia.outpost.persistence.OutpostDataStore;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Server-side tick handler that drives the entire logistics simulation.
 *
 * <h3>Tick sequence (every server tick)</h3>
 * <ol>
 *   <li>Tick all in-flight {@link LogisticsTask}s, delivering resources on arrival.</li>
 *   <li>Rebuild {@link LocalSystemRegistry} and {@link GalacticLogisticsRegistry} from
 *       current buffer + config state of every outpost.</li>
 *   <li>For every stellar system: match supply signals to request signals and dispatch
 *       new tasks via HAMMER or BIG_HAMMER modules.</li>
 * </ol>
 *
 * <p>Registered as an FML event handler in {@link com.gtnewhorizons.galaxia.core.CommonProxy}.
 */
public final class OutpostLogisticsEngine {

    private static final OutpostLogisticsEngine INSTANCE = new OutpostLogisticsEngine();

    /** In-flight task list. Persisted to JSON by {@code OutpostPersistenceManager}. */
    private final List<LogisticsTask> activeTasks = new ArrayList<>();
    private int syncCooldownTicks = 20;

    private OutpostLogisticsEngine() {}

    public static OutpostLogisticsEngine get() {
        return INSTANCE;
    }

    /** Returns the live active task list (used by persistence). */
    public List<LogisticsTask> activeTasksInternal() {
        return activeTasks;
    }

    @SubscribeEvent
    @SideOnly(Side.SERVER)
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        tickOutposts();
        tickModuleCooldowns();
        tickTasks();
        rebuildSignals();
        matchAndDispatch();
        syncClients();
    }

    private void tickOutposts() {
        for (AutomatedOutpostState outpost : OutpostDataStore.get().allOutposts()) {
            outpost.tick();
        }
    }

    // -------------------------------------------------------------------------
    // Step 1 – Tick in-flight tasks
    // -------------------------------------------------------------------------

    private void tickTasks() {
        // Iterate backwards so removals do not shift unprocessed indices.
        for (int i = activeTasks.size() - 1; i >= 0; i--) {
            LogisticsTask ticked = activeTasks.get(i)
                .tick();
            if (ticked.isArrived()) {
                activeTasks.remove(i);
                deliverTask(ticked);
            } else {
                activeTasks.set(i, ticked);
            }
        }
    }

    private void deliverTask(LogisticsTask task) {
        AutomatedOutpostState dest = OutpostDataStore.get()
            .getByAssetId(task.toAssetId());
        if (dest == null) {
            Galaxia.LOG.warn(
                "[Logistics] Task {} arrived but destination outpost {} not found; resources lost.",
                task.taskId(),
                task.toAssetId());
            return;
        }
        dest.inventory.add(task.resourceId(), task.amount());
        Galaxia.LOG.debug(
            "[Logistics] Task {} delivered {} x {} to {}",
            task.taskId(),
            task.amount(),
            task.resourceId(),
            task.toAssetId());
    }

    // -------------------------------------------------------------------------
    // Step 2 – Rebuild signal registries
    // -------------------------------------------------------------------------

    private void rebuildSignals() {
        LocalSystemRegistry local = LocalSystemRegistry.get();
        GalacticLogisticsRegistry galactic = GalacticLogisticsRegistry.get();
        local.clear();
        galactic.clear();

        Collection<AutomatedOutpostState> outposts = OutpostDataStore.get()
            .allOutposts();
        for (AutomatedOutpostState outpost : outposts) {
            emitSignals(outpost, local);
        }
    }

    private void emitSignals(AutomatedOutpostState outpost, LocalSystemRegistry local) {
        Map<ItemStackWrapper, Long> snapshot = outpost.inventory.snapshot();
        LogisticsConfiguration config = outpost.logisticsConfig;

        // Collect all resources that have explicit config or non-zero stock.
        List<ItemStackWrapper> resources = new ArrayList<>(config.snapshot()
            .keySet());
        for (ItemStackWrapper r : snapshot.keySet()) {
            if (!resources.contains(r)) resources.add(r);
        }

        for (ItemStackWrapper resource : resources) {
            long stock = outpost.inventory.getAmount(resource);
            LogisticsResourceConfig cfg = config.get(resource);

            // REQUEST: import enabled AND stock below minReserve → emit negative signal for one orderSize batch.
            if (cfg.isImportEnabled() && stock < cfg.minReserve()) {
                long requestAmount = -Math.min(cfg.orderSize(), cfg.minReserve() - stock);
                local.addSignal(
                    new LogisticsSignal(outpost.assetId, outpost.systemId, resource, requestAmount));
            }

            // SUPPLY: supply enabled AND stock above minReserve → emit positive surplus signal.
            if (cfg.isSupplyEnabled() && stock > cfg.minReserve()) {
                long surplus = stock - cfg.minReserve();
                local.addSignal(new LogisticsSignal(outpost.assetId, outpost.systemId, resource, surplus));
            }
        }
    }

    // -------------------------------------------------------------------------
    // Step 3 – Match signals and dispatch tasks
    // -------------------------------------------------------------------------

    private void matchAndDispatch() {
        for (Map.Entry<String, List<LogisticsSignal>> entry : LocalSystemRegistry.get()
            .allSignals()
            .entrySet()) {
            matchSystemSignals(entry.getKey(), entry.getValue());
        }
    }

    private void matchSystemSignals(String systemId, List<LogisticsSignal> signals) {
        List<LogisticsSignal> requests = new ArrayList<>();
        List<LogisticsSignal> supplies = new ArrayList<>();
        for (LogisticsSignal s : signals) {
            if (s.isRequest()) requests.add(s);
            else if (s.isSupply()) supplies.add(s);
        }

        for (LogisticsSignal request : requests) {
            for (LogisticsSignal supply : supplies) {
                if (!supply.resourceId()
                    .equals(request.resourceId())) continue;
                if (supply.outpostAssetId()
                    .equals(request.outpostAssetId())) continue;

                AutomatedOutpostState supplier = OutpostDataStore.get()
                    .getByAssetId(supply.outpostAssetId());
                AutomatedOutpostState requester = OutpostDataStore.get()
                    .getByAssetId(request.outpostAssetId());
                if (supplier == null || requester == null) continue;

                // Try BIG_HAMMER first (no EU cost, no batch limit).
                if (tryDispatchBigHammer(supplier, requester, request)) break;
                // Fall back to HAMMER (EU cost, batch limit, cooldown).
                if (tryDispatchHammer(supplier, requester, request)) break;
            }
        }
    }

    // -------------------------------------------------------------------------
    // HAMMER dispatch
    // -------------------------------------------------------------------------

    /**
     * Attempts to dispatch a HAMMER task from {@code supplier} to {@code requester}.
     *
     * <p>Rules:
     * <ul>
     *   <li>HAMMER module must be installed in the supplier and off cooldown.</li>
     *   <li>Supplier must have enough EU in the module's energy buffer.</li>
     *   <li>Amount sent = min(orderSize, available surplus above minReserve), capped at 64.</li>
     *   <li>If the order exceeds 64, only one 64-item task is dispatched per invocation;
     *       subsequent ticks will dispatch the remainder.</li>
     *   <li>Cooldown of 100 ticks is applied after each dispatch.</li>
     * </ul>
     *
     * @return {@code true} if a task was dispatched.
     */
    private boolean tryDispatchHammer(AutomatedOutpostState supplier, AutomatedOutpostState requester,
        LogisticsSignal request) {
        AutomatedOutpostModule hammer = supplier.firstOperationalModule(OutpostModuleKind.HAMMER);
        if (hammer == null || hammer.cooldownTicks > 0) return false;

        ItemStackWrapper resource = request.resourceId();
        LogisticsResourceConfig supplierCfg = supplier.logisticsConfig.get(resource);
        long supplierStock = supplier.inventory.getAmount(resource);
        long availableSurplus = supplierStock - supplierCfg.minReserve();
        if (availableSurplus <= 0) return false;

        LogisticsResourceConfig requesterCfg = requester.logisticsConfig.get(resource);
        long sendAmount = Math.min(requesterCfg.orderSize(), availableSurplus);
        sendAmount = Math.min(sendAmount, HammerModuleData.MAX_BATCH_SIZE);
        if (sendAmount <= 0) return false;

        long euRequired = sendAmount * HammerModuleData.EU_PER_ITEM;
        if (hammer.energyBuffer < euRequired) return false;

        // Consume resources from supplier.
        if (!supplier.inventory.tryConsume(resource, sendAmount)) return false;

        // Consume EU.
        hammer.energyBuffer -= euRequired;
        hammer.cooldownTicks = HammerModuleData.COOLDOWN_TICKS;

        // Create and register the in-flight task.
        // Delivery time = 1 tick (instantaneous once EU is paid); future range mechanic TBD.
        LogisticsTask task = LogisticsTask.create(
            supplier.assetId,
            requester.assetId,
            resource,
            sendAmount,
            1,
            "HAMMER");
        activeTasks.add(task);

        Galaxia.LOG.debug(
            "[Logistics] HAMMER dispatched {} x {} from {} to {} (task {})",
            sendAmount,
            resource,
            supplier.assetId,
            requester.assetId,
            task.taskId());
        return true;
    }

    // -------------------------------------------------------------------------
    // BIG_HAMMER dispatch
    // -------------------------------------------------------------------------

    /**
     * Attempts to dispatch a BIG_HAMMER task from {@code supplier} to {@code requester}.
     *
     * <p>BIG_HAMMER has no EU cost, no batch-size limit, and no cooldown.
     *
     * @return {@code true} if a task was dispatched.
     */
    private boolean tryDispatchBigHammer(AutomatedOutpostState supplier, AutomatedOutpostState requester,
        LogisticsSignal request) {
        if (!supplier.hasOperationalModule(OutpostModuleKind.BIG_HAMMER)) return false;

        ItemStackWrapper resource = request.resourceId();
        LogisticsResourceConfig supplierCfg = supplier.logisticsConfig.get(resource);
        long supplierStock = supplier.inventory.getAmount(resource);
        long availableSurplus = supplierStock - supplierCfg.minReserve();
        if (availableSurplus <= 0) return false;

        LogisticsResourceConfig requesterCfg = requester.logisticsConfig.get(resource);
        long sendAmount = Math.min(requesterCfg.orderSize(), availableSurplus);
        if (sendAmount <= 0) return false;

        if (!supplier.inventory.tryConsume(resource, sendAmount)) return false;

        LogisticsTask task = LogisticsTask.create(
            supplier.assetId,
            requester.assetId,
            resource,
            sendAmount,
            1,
            "BIG_HAMMER");
        activeTasks.add(task);

        Galaxia.LOG.debug(
            "[Logistics] BIG_HAMMER dispatched {} x {} from {} to {} (task {})",
            sendAmount,
            resource,
            supplier.assetId,
            requester.assetId,
            task.taskId());
        return true;
    }

    // -------------------------------------------------------------------------
    // HAMMER module cooldown tick (separate from task ticking)
    // -------------------------------------------------------------------------

    /**
     * Decrements the cooldown counter of every HAMMER module across all outposts.
     * Called as part of the server tick before matchAndDispatch.
     */
    private void tickModuleCooldowns() {
        for (AutomatedOutpostState outpost : OutpostDataStore.get()
            .allOutposts()) {
            for (AutomatedOutpostModule module : outpost.modulesInternal()) {
                if (module.cooldownTicks > 0) module.cooldownTicks--;
            }
        }
    }

    private void syncClients() {
        syncCooldownTicks--;
        if (syncCooldownTicks > 0) return;
        syncCooldownTicks = 20;
        for (AutomatedOutpostState outpost : OutpostDataStore.get().allOutposts()) {
            Galaxia.GALAXIA_NETWORK.sendToAll(new OutpostFullSyncPacket(outpost));
        }
    }
}
