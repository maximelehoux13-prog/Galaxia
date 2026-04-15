package com.gtnewhorizons.galaxia.outpost.logistics;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.server.MinecraftServer;

import com.gtnewhorizons.galaxia.api.GalaxiaCelestialAPI;
import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.outpost.AutomatedOutpost;
import com.gtnewhorizons.galaxia.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.outpost.LogisticsConfiguration;
import com.gtnewhorizons.galaxia.outpost.LogisticsResourceConfig;
import com.gtnewhorizons.galaxia.outpost.module.IHammer;
import com.gtnewhorizons.galaxia.outpost.module.ModuleHammer;
import com.gtnewhorizons.galaxia.outpost.module.OutpostModuleKind;
import com.gtnewhorizons.galaxia.outpost.network.LogisticsSyncPacket;
import com.gtnewhorizons.galaxia.outpost.network.OutpostSyncPacket;
import com.gtnewhorizons.galaxia.outpost.persistence.OutpostDataStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalTransferPlanner;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Server-side tick handler that drives the entire logistics simulation.
 *
 * <h3>Tick sequence (every server tick)</h3>
 * <ol>
 * <li>Tick all outposts (modules + passive power).</li>
 * <li>Decrement HAMMER module cooldowns.</li>
 * <li>Tick all in-flight {@link LogisticsTask}s, delivering resources on arrival.</li>
 * <li>Rebuild {@link LogisticsSignalStore} from current buffer + config state of every outpost.</li>
 * <li>For every scope bucket: match supply signals to request signals and dispatch
 * new tasks via HAMMER (PLANETARY) or BIG_HAMMER (SYSTEM) modules.</li>
 * </ol>
 *
 * <p>
 * Registered as an FML event handler in {@link com.gtnewhorizons.galaxia.core.CommonProxy}.
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
        CelestialObject root = GalaxiaCelestialAPI.getPrimaryRoot();
        rebuildSignals(root);
        matchAndDispatch(root);
        syncClients();
    }

    private void tickOutposts() {
        for (AutomatedOutpost outpost : OutpostDataStore.get()
            .allOutposts()) {
            outpost.tick();
        }
    }

    // -------------------------------------------------------------------------
    // Step 1 – Tick in-flight tasks
    // -------------------------------------------------------------------------

    private void tickTasks() {
        for (int i = activeTasks.size() - 1; i >= 0; i--) {
            LogisticsTask current = activeTasks.get(i);
            if (CelestialAssetStore.findAsset(current.data.fromAssetId()) == null
                || CelestialAssetStore.findAsset(current.data.toAssetId()) == null) {
                activeTasks.remove(i);
                continue;
            }
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
        AutomatedOutpost dest = OutpostDataStore.get()
            .getByAssetId(task.data.toAssetId());
        if (dest == null) {
            Galaxia.LOG.warn(
                "[Logistics] Task {} arrived but destination outpost {} not found; resources lost.",
                task.taskId,
                task.data.toAssetId());
            return;
        }
        dest.inventory.add(task.data.resourceId(), task.data.amount());
        Galaxia.LOG.debug(
            "[Logistics] Task {} delivered {} x {} to {}",
            task.taskId,
            task.data.amount(),
            task.data.resourceId(),
            task.data.toAssetId());
    }

    // -------------------------------------------------------------------------
    // Step 2 – Rebuild signal registries
    // -------------------------------------------------------------------------

    private void rebuildSignals(CelestialObject root) {
        LogisticsSignalStore store = LogisticsSignalStore.get();
        store.clear();

        for (AutomatedOutpost outpost : OutpostDataStore.get()
            .allOutposts()) {
            emitSignals(outpost, store);
        }
    }

    private void emitSignals(AutomatedOutpost outpost, LogisticsSignalStore store) {
        Map<ItemStackWrapper, Long> snapshot = outpost.inventory.snapshot();
        LogisticsConfiguration config = outpost.logisticsConfig;

        List<ItemStackWrapper> resources = new ArrayList<>(
            config.snapshot()
                .keySet());
        for (ItemStackWrapper r : snapshot.keySet()) {
            if (!resources.contains(r)) resources.add(r);
        }

        CelestialObjectId bodyId = outpost.celestialBodyId;
        CelestialObjectId systemId = outpost.systemId;
        CelestialObjectId planetaryAnchorBodyId = outpost.planetaryAnchorBodyId;

        for (ItemStackWrapper resource : resources) {
            long stock = outpost.inventory.getAmount(resource);
            LogisticsResourceConfig cfg = config.get(resource);

            // Each resource is emitted once, at SYSTEM scope (the broadest range).
            // The engine routes each matched pair to HAMMER or BIG_HAMMER at dispatch
            // time based on whether the two endpoints share a planetary anchor.
            if (cfg.isImportEnabled() && stock < cfg.minReserve()) {
                long requestAmount = -(cfg.minReserve() - stock);
                store.addSignal(
                    new LogisticsSignal(
                        outpost.assetId,
                        systemId,
                        resource,
                        requestAmount,
                        LogisticsSignal.Scope.SYSTEM,
                        bodyId,
                        planetaryAnchorBodyId));
            }

            if (cfg.isSupplyEnabled() && stock > cfg.minReserve()) {
                long surplus = stock - cfg.minReserve();
                store.addSignal(
                    new LogisticsSignal(
                        outpost.assetId,
                        systemId,
                        resource,
                        surplus,
                        LogisticsSignal.Scope.SYSTEM,
                        bodyId,
                        planetaryAnchorBodyId));
            }
        }
    }

    // -------------------------------------------------------------------------
    // Step 3 – Match signals and dispatch tasks
    // -------------------------------------------------------------------------

    private void matchAndDispatch(CelestialObject root) {
        double orbitalTime = currentOrbitalTime();
        LogisticsSignalStore store = LogisticsSignalStore.get();

        // All signals live in SYSTEM scope (one signal per resource per outpost).
        // Dispatch routing is decided at match time:
        // same planetary anchor → HAMMER (then BIG_HAMMER if planetaryTransferHandling is on)
        // different planetary anchors → BIG_HAMMER only
        for (Map.Entry<CelestialObjectId, List<LogisticsSignal>> entry : store
            .allSignalsForScope(LogisticsSignal.Scope.SYSTEM)
            .entrySet()) {
            matchSystemBucket(entry.getValue(), orbitalTime, root);
        }
    }

    private void matchSystemBucket(List<LogisticsSignal> signals, double orbitalTime, CelestialObject root) {
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

                AutomatedOutpost supplier = OutpostDataStore.get()
                    .getByAssetId(supply.outpostAssetId());
                AutomatedOutpost requester = OutpostDataStore.get()
                    .getByAssetId(request.outpostAssetId());
                if (supplier == null || requester == null) continue;

                if (sharesPlanetaryAnchor(root, supplier.celestialBodyId, requester.celestialBodyId)) {
                    // Planetary-range pair: HAMMER first; BIG_HAMMER only when toggle is on
                    if (tryDispatchHammer(supplier, requester, request, orbitalTime, root)) break;
                    if (hasPlanetaryTransferHandling(supplier)
                        && tryDispatchBigHammer(supplier, requester, request, orbitalTime, root)) break;
                } else {
                    // Cross-planetary pair: BIG_HAMMER only
                    if (tryDispatchBigHammer(supplier, requester, request, orbitalTime, root)) break;
                }
            }
        }
    }

    private static boolean hasPlanetaryTransferHandling(AutomatedOutpost supplier) {
        IHammer bh = (IHammer) supplier.firstOperationalModule(OutpostModuleKind.BIG_HAMMER);
        if (bh == null) return false;
        return bh.getPlanetaryHandling();
    }

    /**
     * Returns {@code true} if both bodies share the same planetary anchor
     * (i.e. same planet/gas-giant or both on the same planet's moon system).
     * Used to gate BIG_HAMMER on the {@code planetaryTransferHandling} toggle.
     */
    private static boolean sharesPlanetaryAnchor(CelestialObject root, CelestialObjectId bodyIdA,
        CelestialObjectId bodyIdB) {
        if (root == null || bodyIdA == null || bodyIdB == null) return false;
        CelestialObject a = GalaxiaCelestialAPI.findBodyById(root, bodyIdA);
        CelestialObject b = GalaxiaCelestialAPI.findBodyById(root, bodyIdB);
        if (a == null || b == null) return false;
        CelestialObject anchorA = GalaxiaCelestialAPI.findPlanetaryAnchor(root, a);
        CelestialObject anchorB = GalaxiaCelestialAPI.findPlanetaryAnchor(root, b);
        return anchorA != null && anchorA == anchorB;
    }

    // -------------------------------------------------------------------------
    // HAMMER dispatch (PLANETARY scope, EU cost, cooldown)
    // -------------------------------------------------------------------------

    private boolean tryDispatchHammer(AutomatedOutpost supplier, AutomatedOutpost requester, LogisticsSignal request,
        double orbitalTime, CelestialObject root) {
        IHammer hammer = (IHammer) supplier.firstOperationalModule(OutpostModuleKind.HAMMER);
        if (hammer == null || hammer.canFire()) return false;

        ItemStackWrapper resource = request.resourceId();
        LogisticsResourceConfig supplierCfg = supplier.logisticsConfig.get(resource);
        long supplierStock = supplier.inventory.getAmount(resource);
        long availableSurplus = supplierStock - supplierCfg.minReserve();
        if (availableSurplus <= 0) return false;

        LogisticsResourceConfig requesterCfg = requester.logisticsConfig.get(resource);
        long requesterStock = requester.inventory.getAmount(resource);
        long inboundInTransit = getInboundInTransitAmount(requester.assetId, resource);
        long requestedAmount = Math.max(0L, requesterCfg.minReserve() - requesterStock - inboundInTransit);
        long sendAmount = Math.min(requestedAmount, availableSurplus);
        sendAmount = Math.min(sendAmount, ModuleHammer.MAX_BATCH_SIZE);
        if (sendAmount < requesterCfg.orderSize() || sendAmount <= 0) return false;

        // Same-body: instant transfer, no trajectory needed
        if (supplier.celestialBodyId.equals(requester.celestialBodyId)) {
            if (!supplier.inventory.tryConsume(resource, sendAmount)) return false;
            hammer.fire();
            LogisticsTask task = LogisticsTask.create(
                supplier.assetId,
                requester.assetId,
                resource,
                sendAmount,
                1,
                LogisticsTask.TransportType.HAMMER);
            activeTasks.add(task);
            return true;
        }

        // Cross-body: compute trajectory
        CelestialObject srcBody = GalaxiaCelestialAPI.findBodyById(root, supplier.celestialBodyId);
        CelestialObject dstBody = GalaxiaCelestialAPI.findBodyById(root, requester.celestialBodyId);
        CelestialObject attractor = srcBody != null ? GalaxiaCelestialAPI.findPlanetaryAnchor(root, srcBody) : null;

        OrbitalTransferPlanner.TransferRoute route = (srcBody != null && dstBody != null && attractor != null)
            ? OrbitalTransferPlanner
                .computeRoute(root, attractor, srcBody, dstBody, orbitalTime, hammer.getRoutePriority())
            : null;

        if (route == null) return false; // no valid trajectory in PLANETARY scope

        if (!hammer.getConfig()
            .allows(route.departureDv(), route.tofSeconds())) return false;

        long euPerItem = Math.max(1L, (long) Math.ceil(route.departureDv() * EU_PER_ITEM_PER_DV));
        long affordableAmount = supplier.getEnergyStored() / euPerItem;
        sendAmount = Math.min(sendAmount, affordableAmount);
        if (sendAmount < requesterCfg.orderSize() || sendAmount <= 0) return false;

        long euRequired = sendAmount * euPerItem;
        if (!supplier.tryConsumeEnergy(euRequired)) return false;
        if (!supplier.inventory.tryConsume(resource, sendAmount)) return false;

        hammer.fire();

        LogisticsTask task = LogisticsTask.createWithTrajectory(
            supplier.assetId,
            requester.assetId,
            resource,
            sendAmount,
            route.tofTicks(),
            LogisticsTask.TransportType.HAMMER,
            supplier.celestialBodyId,
            requester.celestialBodyId,
            orbitalTime,
            route.tofOsu());
        activeTasks.add(task);

        Galaxia.LOG.debug(
            "[Logistics] HAMMER dispatched {} x {} from {} to {} (dV={} tof={}t task {})",
            sendAmount,
            resource,
            supplier.assetId,
            requester.assetId,
            String.format("%.2f", route.departureDv()),
            route.tofTicks(),
            task.taskId);
        return true;
    }

    // -------------------------------------------------------------------------
    // BIG_HAMMER dispatch (SYSTEM scope, EU cost = 100 × amount × departureDv)
    // -------------------------------------------------------------------------

    private boolean tryDispatchBigHammer(AutomatedOutpost supplier, AutomatedOutpost requester, LogisticsSignal request,
        double orbitalTime, CelestialObject root) {
        IHammer bigHammer = (IHammer) supplier.firstOperationalModule(OutpostModuleKind.BIG_HAMMER);
        if (bigHammer == null || bigHammer.canFire()) return false;

        ItemStackWrapper resource = request.resourceId();
        LogisticsResourceConfig supplierCfg = supplier.logisticsConfig.get(resource);
        long supplierStock = supplier.inventory.getAmount(resource);
        long availableSurplus = supplierStock - supplierCfg.minReserve();
        if (availableSurplus <= 0) return false;

        LogisticsResourceConfig requesterCfg = requester.logisticsConfig.get(resource);
        long requesterStock = requester.inventory.getAmount(resource);
        long inboundInTransit = getInboundInTransitAmount(requester.assetId, resource);
        long requestedAmount = Math.max(0L, requesterCfg.minReserve() - requesterStock - inboundInTransit);
        long sendAmount = Math.min(requestedAmount, availableSurplus);
        if (sendAmount < requesterCfg.orderSize() || sendAmount <= 0) return false;

        // Same-body: instant transfer
        if (supplier.celestialBodyId.equals(requester.celestialBodyId)) {
            if (!supplier.inventory.tryConsume(resource, sendAmount)) return false;
            bigHammer.fire();
            LogisticsTask task = LogisticsTask.create(
                supplier.assetId,
                requester.assetId,
                resource,
                sendAmount,
                1,
                LogisticsTask.TransportType.BIG_HAMMER);
            activeTasks.add(task);
            return true;
        }

        // Cross-body: Lambert route (attractor = host star)
        CelestialObject srcBody = GalaxiaCelestialAPI.findBodyById(root, supplier.celestialBodyId);
        CelestialObject dstBody = GalaxiaCelestialAPI.findBodyById(root, requester.celestialBodyId);
        CelestialObject star = srcBody != null ? GalaxiaCelestialAPI.findStar(root, srcBody) : null;

        OrbitalTransferPlanner.TransferRoute route = (srcBody != null && dstBody != null && star != null)
            ? OrbitalTransferPlanner
                .computeRoute(root, star, srcBody, dstBody, orbitalTime, bigHammer.getRoutePriority())
            : null;

        if (route == null) return false;

        if (!bigHammer.getConfig()
            .allows(route.departureDv(), route.tofSeconds())) return false;

        long euPerItem = Math.max(1L, (long) Math.ceil(route.departureDv() * EU_PER_ITEM_PER_DV));
        long affordableAmount = supplier.getEnergyStored() / euPerItem;
        sendAmount = Math.min(sendAmount, affordableAmount);
        if (sendAmount < requesterCfg.orderSize() || sendAmount <= 0) return false;

        long euRequired = sendAmount * euPerItem;
        if (!supplier.tryConsumeEnergy(euRequired)) return false;
        if (!supplier.inventory.tryConsume(resource, sendAmount)) return false;
        bigHammer.fire();

        LogisticsTask task = LogisticsTask.createWithTrajectory(
            supplier.assetId,
            requester.assetId,
            resource,
            sendAmount,
            route.tofTicks(),
            LogisticsTask.TransportType.BIG_HAMMER,
            supplier.celestialBodyId,
            requester.celestialBodyId,
            orbitalTime,
            route.tofOsu());
        activeTasks.add(task);

        Galaxia.LOG.debug(
            "[Logistics] BIG_HAMMER dispatched {} x {} from {} to {} (dV={} tof={}t task {})",
            sendAmount,
            resource,
            supplier.assetId,
            requester.assetId,
            String.format("%.2f", route.departureDv()),
            route.tofTicks(),
            task.taskId);
        return true;
    }

    // -------------------------------------------------------------------------
    // Module cooldown tick
    // -------------------------------------------------------------------------

    private void tickModuleCooldowns() {
        /// Already handled in the module
        // for (AutomatedOutpost outpost : OutpostDataStore.get()
        // .allOutposts()) {
        // for (AutomatedOutpostModule module : outpost.modules()) {
        // if (module.cooldownTicks > 0) module.cooldownTicks--;
        // }
        // }
    }

    private long getInboundInTransitAmount(CelestialAsset.ID toAssetId, ItemStackWrapper resource) {
        long total = 0L;
        for (LogisticsTask task : activeTasks) {
            if (!toAssetId.equals(task.data.toAssetId())) continue;
            if (!resource.equals(task.data.resourceId())) continue;
            total += task.data.amount();
        }
        return total;
    }

    // -------------------------------------------------------------------------
    // Client sync
    // -------------------------------------------------------------------------

    private void syncClients() {
        syncCooldownTicks--;
        if (syncCooldownTicks > 0) return;
        syncCooldownTicks = 20;
        for (AutomatedOutpost outpost : OutpostDataStore.get()
            .allOutposts()) {
            Galaxia.GALAXIA_NETWORK.sendToAll(OutpostSyncPacket.fullSync(outpost));
        }
        Galaxia.GALAXIA_NETWORK.sendToAll(LogisticsSyncPacket.from(LogisticsSignalStore.get(), activeTasks));
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
        long totalWorldTime = server.getEntityWorld()
            .getTotalWorldTime();
        return totalWorldTime * OrbitalTransferPlanner.OSU_PER_TICK;
    }
}
