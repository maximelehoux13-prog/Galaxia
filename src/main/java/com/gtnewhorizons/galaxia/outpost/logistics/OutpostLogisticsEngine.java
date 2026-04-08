package com.gtnewhorizons.galaxia.outpost.logistics;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.server.MinecraftServer;

import com.gtnewhorizons.galaxia.api.celestial.GalaxiaCelestialAPI;
import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.orbitalGUI.Hierarchy.OrbitalCelestialBody;
import com.gtnewhorizons.galaxia.orbitalGUI.OrbitalTransferPlanner;
import com.gtnewhorizons.galaxia.outpost.AutomatedOutpostModule;
import com.gtnewhorizons.galaxia.outpost.AutomatedOutpostState;
import com.gtnewhorizons.galaxia.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.outpost.LogisticsConfiguration;
import com.gtnewhorizons.galaxia.outpost.LogisticsResourceConfig;
import com.gtnewhorizons.galaxia.outpost.OutpostModuleKind;
import com.gtnewhorizons.galaxia.outpost.module.BigHammerModuleData;
import com.gtnewhorizons.galaxia.outpost.module.HammerModuleData;
import com.gtnewhorizons.galaxia.outpost.network.OutpostFullSyncPacket;
import com.gtnewhorizons.galaxia.outpost.persistence.OutpostDataStore;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Server-side tick handler that drives the entire logistics simulation.
 *
 * <h3>Tick sequence (every server tick)</h3>
 * <ol>
 *   <li>Tick all outposts (modules + passive power).</li>
 *   <li>Decrement HAMMER module cooldowns.</li>
 *   <li>Tick all in-flight {@link LogisticsTask}s, delivering resources on arrival.</li>
 *   <li>Rebuild {@link LogisticsSignalStore} from current buffer + config state of every outpost.</li>
 *   <li>For every scope bucket: match supply signals to request signals and dispatch
 *       new tasks via HAMMER (PLANETARY) or BIG_HAMMER (SYSTEM) modules.</li>
 * </ol>
 *
 * <p>Registered as an FML event handler in {@link com.gtnewhorizons.galaxia.core.CommonProxy}.
 */
public final class OutpostLogisticsEngine {

    private static final OutpostLogisticsEngine INSTANCE = new OutpostLogisticsEngine();

    /**
     * EU cost per item per unit of departure delta-V.
     * Actual EU = 100 × amount × departureDv.
     */
    public static final long EU_PER_ITEM_PER_DV = 100L;

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
        for (int i = activeTasks.size() - 1; i >= 0; i--) {
            LogisticsTask ticked = activeTasks.get(i).tick();
            if (ticked.isArrived()) {
                activeTasks.remove(i);
                deliverTask(ticked);
            } else {
                activeTasks.set(i, ticked);
            }
        }
    }

    private void deliverTask(LogisticsTask task) {
        AutomatedOutpostState dest = OutpostDataStore.get().getByAssetId(task.toAssetId());
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
        LogisticsSignalStore store = LogisticsSignalStore.get();
        store.clear();

        OrbitalCelestialBody root = GalaxiaCelestialAPI.getPrimaryRoot();

        for (AutomatedOutpostState outpost : OutpostDataStore.get().allOutposts()) {
            emitSignals(outpost, store, root);
        }
    }

    private void emitSignals(AutomatedOutpostState outpost, LogisticsSignalStore store,
        OrbitalCelestialBody root) {
        Map<ItemStackWrapper, Long> snapshot = outpost.inventory.snapshot();
        LogisticsConfiguration config = outpost.logisticsConfig;

        List<ItemStackWrapper> resources = new ArrayList<>(config.snapshot().keySet());
        for (ItemStackWrapper r : snapshot.keySet()) {
            if (!resources.contains(r)) resources.add(r);
        }

        // Determine body hierarchy for scope keys
        String bodyId = outpost.celestialBodyId;
        String systemId = outpost.systemId;
        String planetaryAnchorBodyId = resolvePlanetaryAnchor(root, bodyId);

        for (ItemStackWrapper resource : resources) {
            long stock = outpost.inventory.getAmount(resource);
            LogisticsResourceConfig cfg = config.get(resource);

            if (cfg.isImportEnabled() && stock < cfg.minReserve()) {
                long requestAmount = -(cfg.minReserve() - stock);
                // Emit PLANETARY signal
                store.addSignal(new LogisticsSignal(outpost.assetId, systemId, resource, requestAmount,
                    LogisticsSignalScope.PLANETARY, bodyId, planetaryAnchorBodyId));
                // Emit SYSTEM signal
                store.addSignal(new LogisticsSignal(outpost.assetId, systemId, resource, requestAmount,
                    LogisticsSignalScope.SYSTEM, bodyId, planetaryAnchorBodyId));
            }

            if (cfg.isSupplyEnabled() && stock > cfg.minReserve()) {
                long surplus = stock - cfg.minReserve();
                store.addSignal(new LogisticsSignal(outpost.assetId, systemId, resource, surplus,
                    LogisticsSignalScope.PLANETARY, bodyId, planetaryAnchorBodyId));
                store.addSignal(new LogisticsSignal(outpost.assetId, systemId, resource, surplus,
                    LogisticsSignalScope.SYSTEM, bodyId, planetaryAnchorBodyId));
            }
        }
    }

    /**
     * Resolves the planetary anchor body id for a given celestial body id.
     * For planets/gas giants: returns bodyId itself.
     * For moons/stations/asteroids: returns the id of the nearest planet ancestor.
     * Falls back to bodyId if resolution fails.
     */
    private static String resolvePlanetaryAnchor(OrbitalCelestialBody root, String bodyId) {
        if (root == null || bodyId == null) return bodyId;
        OrbitalCelestialBody body = OrbitalTransferPlanner.findBodyById(root, bodyId);
        if (body == null) return bodyId;
        OrbitalCelestialBody anchor = OrbitalTransferPlanner.findPlanetaryAnchor(root, body);
        return anchor != null ? anchor.id() : bodyId;
    }

    // -------------------------------------------------------------------------
    // Step 3 – Match signals and dispatch tasks
    // -------------------------------------------------------------------------

    private void matchAndDispatch() {
        double orbitalTime = currentOrbitalTime();
        OrbitalCelestialBody root = GalaxiaCelestialAPI.getPrimaryRoot();
        LogisticsSignalStore store = LogisticsSignalStore.get();

        // HAMMER: PLANETARY scope
        for (Map.Entry<String, List<LogisticsSignal>> entry : store
            .allSignalsForScope(LogisticsSignalScope.PLANETARY).entrySet()) {
            matchBucket(entry.getValue(), false, orbitalTime, root);
        }

        // BIG_HAMMER: SYSTEM scope
        for (Map.Entry<String, List<LogisticsSignal>> entry : store
            .allSignalsForScope(LogisticsSignalScope.SYSTEM).entrySet()) {
            matchBucket(entry.getValue(), true, orbitalTime, root);
        }
    }

    private void matchBucket(List<LogisticsSignal> signals, boolean bigHammer, double orbitalTime,
        OrbitalCelestialBody root) {
        List<LogisticsSignal> requests = new ArrayList<>();
        List<LogisticsSignal> supplies = new ArrayList<>();
        for (LogisticsSignal s : signals) {
            if (s.isRequest()) requests.add(s);
            else if (s.isSupply()) supplies.add(s);
        }

        for (LogisticsSignal request : requests) {
            for (LogisticsSignal supply : supplies) {
                if (!supply.resourceId().equals(request.resourceId())) continue;
                if (supply.outpostAssetId().equals(request.outpostAssetId())) continue;

                AutomatedOutpostState supplier = OutpostDataStore.get().getByAssetId(supply.outpostAssetId());
                AutomatedOutpostState requester = OutpostDataStore.get().getByAssetId(request.outpostAssetId());
                if (supplier == null || requester == null) continue;

                if (bigHammer) {
                    if (tryDispatchBigHammer(supplier, requester, request, orbitalTime, root)) break;
                } else {
                    if (tryDispatchBigHammer(supplier, requester, request, orbitalTime, root)) break;
                    if (tryDispatchHammer(supplier, requester, request, orbitalTime, root)) break;
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // HAMMER dispatch (PLANETARY scope, EU cost, cooldown)
    // -------------------------------------------------------------------------

    private boolean tryDispatchHammer(AutomatedOutpostState supplier, AutomatedOutpostState requester,
        LogisticsSignal request, double orbitalTime, OrbitalCelestialBody root) {
        AutomatedOutpostModule hammer = supplier.firstOperationalModule(OutpostModuleKind.HAMMER);
        if (hammer == null || hammer.cooldownTicks > 0) return false;

        HammerModuleData hammerData = hammer.getData() instanceof HammerModuleData hd ? hd
            : HammerModuleData.getDefault();

        ItemStackWrapper resource = request.resourceId();
        LogisticsResourceConfig supplierCfg = supplier.logisticsConfig.get(resource);
        long supplierStock = supplier.inventory.getAmount(resource);
        long availableSurplus = supplierStock - supplierCfg.minReserve();
        if (availableSurplus <= 0) return false;

        LogisticsResourceConfig requesterCfg = requester.logisticsConfig.get(resource);
        long requestedAmount = Math.abs(request.amount());
        long sendAmount = Math.min(requestedAmount, availableSurplus);
        sendAmount = Math.min(sendAmount, HammerModuleData.MAX_BATCH_SIZE);
        if (sendAmount < requesterCfg.orderSize() || sendAmount <= 0) return false;

        // Same-body: instant transfer, no trajectory needed
        if (supplier.celestialBodyId.equals(requester.celestialBodyId)) {
            if (!supplier.inventory.tryConsume(resource, sendAmount)) return false;
            hammer.cooldownTicks = HammerModuleData.COOLDOWN_TICKS;
            LogisticsTask task = LogisticsTask.create(supplier.assetId, requester.assetId, resource, sendAmount, 1,
                "HAMMER");
            activeTasks.add(task);
            return true;
        }

        // Cross-body: compute trajectory
        OrbitalCelestialBody srcBody = OrbitalTransferPlanner.findBodyById(root, supplier.celestialBodyId);
        OrbitalCelestialBody dstBody = OrbitalTransferPlanner.findBodyById(root, requester.celestialBodyId);
        OrbitalCelestialBody attractor = srcBody != null
            ? OrbitalTransferPlanner.findPlanetaryAnchor(root, srcBody)
            : null;

        OrbitalTransferPlanner.TransferRoute route = (srcBody != null && dstBody != null && attractor != null)
            ? OrbitalTransferPlanner.computeMinTofRoute(root, attractor, srcBody, dstBody, orbitalTime)
            : null;

        if (route == null) return false; // no valid trajectory in PLANETARY scope

        if (!hammerData.effectiveShooting().allows(route.departureDv(), route.tofSeconds())) return false;

        long euRequired = sendAmount * (long) Math.max(1L, Math.ceil(route.departureDv() * EU_PER_ITEM_PER_DV));
        if (!supplier.tryConsumeEnergy(euRequired)) return false;
        if (!supplier.inventory.tryConsume(resource, sendAmount)) return false;

        hammer.cooldownTicks = HammerModuleData.COOLDOWN_TICKS;

        LogisticsTask task = LogisticsTask.createWithTrajectory(
            supplier.assetId, requester.assetId, resource, sendAmount,
            route.tofTicks(), "HAMMER",
            supplier.celestialBodyId, requester.celestialBodyId,
            orbitalTime, route.tofOsu());
        activeTasks.add(task);

        Galaxia.LOG.debug(
            "[Logistics] HAMMER dispatched {} x {} from {} to {} (dV={} tof={}t task {})",
            sendAmount, resource, supplier.assetId, requester.assetId,
            String.format("%.2f", route.departureDv()), route.tofTicks(), task.taskId());
        return true;
    }

    // -------------------------------------------------------------------------
    // BIG_HAMMER dispatch (SYSTEM scope, no EU cost, no cooldown)
    // -------------------------------------------------------------------------

    private boolean tryDispatchBigHammer(AutomatedOutpostState supplier, AutomatedOutpostState requester,
        LogisticsSignal request, double orbitalTime, OrbitalCelestialBody root) {
        AutomatedOutpostModule bigHammer = supplier.firstOperationalModule(OutpostModuleKind.BIG_HAMMER);
        if (bigHammer == null) return false;

        BigHammerModuleData bigHammerData = bigHammer.getData() instanceof BigHammerModuleData bd ? bd
            : BigHammerModuleData.getDefault();

        ItemStackWrapper resource = request.resourceId();
        LogisticsResourceConfig supplierCfg = supplier.logisticsConfig.get(resource);
        long supplierStock = supplier.inventory.getAmount(resource);
        long availableSurplus = supplierStock - supplierCfg.minReserve();
        if (availableSurplus <= 0) return false;

        LogisticsResourceConfig requesterCfg = requester.logisticsConfig.get(resource);
        long requestedAmount = Math.abs(request.amount());
        long sendAmount = Math.min(requestedAmount, availableSurplus);
        if (sendAmount < requesterCfg.orderSize() || sendAmount <= 0) return false;

        // Same-body: instant transfer
        if (supplier.celestialBodyId.equals(requester.celestialBodyId)) {
            if (!supplier.inventory.tryConsume(resource, sendAmount)) return false;
            LogisticsTask task = LogisticsTask.create(supplier.assetId, requester.assetId, resource, sendAmount, 1,
                "BIG_HAMMER");
            activeTasks.add(task);
            return true;
        }

        // Cross-body: Lambert route (attractor = host star)
        OrbitalCelestialBody srcBody = OrbitalTransferPlanner.findBodyById(root, supplier.celestialBodyId);
        OrbitalCelestialBody dstBody = OrbitalTransferPlanner.findBodyById(root, requester.celestialBodyId);
        OrbitalCelestialBody star = srcBody != null ? OrbitalTransferPlanner.findHostStar(root, srcBody) : null;

        OrbitalTransferPlanner.TransferRoute route = (srcBody != null && dstBody != null && star != null)
            ? OrbitalTransferPlanner.computeMinTofRoute(root, star, srcBody, dstBody, orbitalTime)
            : null;

        if (route == null) return false;

        if (!bigHammerData.effectiveShooting().allows(route.departureDv(), route.tofSeconds())) return false;

        if (!supplier.inventory.tryConsume(resource, sendAmount)) return false;

        LogisticsTask task = LogisticsTask.createWithTrajectory(
            supplier.assetId, requester.assetId, resource, sendAmount,
            route.tofTicks(), "BIG_HAMMER",
            supplier.celestialBodyId, requester.celestialBodyId,
            orbitalTime, route.tofOsu());
        activeTasks.add(task);

        Galaxia.LOG.debug(
            "[Logistics] BIG_HAMMER dispatched {} x {} from {} to {} (dV={} tof={}t task {})",
            sendAmount, resource, supplier.assetId, requester.assetId,
            String.format("%.2f", route.departureDv()), route.tofTicks(), task.taskId());
        return true;
    }

    // -------------------------------------------------------------------------
    // Module cooldown tick
    // -------------------------------------------------------------------------

    private void tickModuleCooldowns() {
        for (AutomatedOutpostState outpost : OutpostDataStore.get().allOutposts()) {
            for (AutomatedOutpostModule module : outpost.modulesInternal()) {
                if (module.cooldownTicks > 0) module.cooldownTicks--;
            }
        }
    }

    // -------------------------------------------------------------------------
    // Client sync
    // -------------------------------------------------------------------------

    private void syncClients() {
        syncCooldownTicks--;
        if (syncCooldownTicks > 0) return;
        syncCooldownTicks = 20;
        for (AutomatedOutpostState outpost : OutpostDataStore.get().allOutposts()) {
            Galaxia.GALAXIA_NETWORK.sendToAll(new OutpostFullSyncPacket(outpost));
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Returns the current orbital simulation time in OSU (orbital simulation units).
     * Server-side: based on world tick count, converted with 42 OSU/s at 20 TPS.
     */
    private static double currentOrbitalTime() {
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null) return 0.0;
        long totalWorldTime = server.getEntityWorld().getTotalWorldTime();
        return totalWorldTime * OrbitalTransferPlanner.OSU_PER_TICK;
    }
}
