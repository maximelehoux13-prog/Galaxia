package com.gtnewhorizons.galaxia.registry.outpost.logistics;

import java.util.Map;
import java.util.Objects;

import com.gtnewhorizons.galaxia.api.GalaxiaCelestialAPI;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalTransferPlanner;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.LogisticsResourceConfig;
import com.gtnewhorizons.galaxia.registry.outpost.module.HammerVariant;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleHammer;

public final class HammerDispatchStatus {

    private HammerDispatchStatus() {}

    public enum Code {
        READY,
        WAITING_FOR_REQUEST,
        NO_EXPORT_CONFIG,
        NO_SURPLUS_AFTER_RESERVE,
        ORDER_BELOW_PACKAGE_SIZE,
        NEED_BIG_HAMMER,
        ROUTE_UNAVAILABLE,
        BLOCKED_BY_DV_LIMIT,
        BLOCKED_BY_TOF_LIMIT,
        NEED_ENERGY
    }

    public record Candidate(boolean sameBody, boolean shareAnchor, boolean routeAvailable, long availableSurplus,
        long requestedAmount, int orderSize, double departureDv, double totalDv, double tofSeconds) {}

    public record Status(Code code, long requiredEnergy, long storedEnergy, long sendAmount, int orderSize) {

        public static Status simple(Code code, ModuleHammer hammer) {
            return new Status(code, 0L, hammer.energyStored(), 0L, 0);
        }
    }

    public static Status evaluate(AutomatedFacility supplier, ModuleInstance hammerModule, double orbitalTime) {
        return evaluate(
            supplier,
            hammerModule,
            CelestialAssetStore.allAssets(),
            LogisticStore.activeDeliveries(),
            orbitalTime);
    }

    public static Status evaluate(AutomatedFacility supplier, ModuleInstance hammerModule, Iterable<?> assets,
        Iterable<LogisticsDelivery> deliveries, double orbitalTime) {
        if (supplier == null || hammerModule == null || !(hammerModule.component() instanceof ModuleHammer hammer)) {
            return new Status(Code.WAITING_FOR_REQUEST, 0L, 0L, 0L, 0);
        }

        Map<ItemStackWrapper, LogisticsResourceConfig> supplierConfigs = supplier.logisticsConfig.snapshot();
        boolean hasExportConfig = supplierConfigs.values()
            .stream()
            .anyMatch(LogisticsResourceConfig::isSupplyEnabled);
        if (!hasExportConfig) return Status.simple(Code.NO_EXPORT_CONFIG, hammer);

        boolean sawSurplusBlocked = false;
        boolean sawAnyRequest = false;
        Status bestBlockedStatus = null;

        for (Map.Entry<ItemStackWrapper, LogisticsResourceConfig> supplierEntry : supplierConfigs.entrySet()) {
            LogisticsResourceConfig supplierCfg = supplierEntry.getValue();
            if (!supplierCfg.isSupplyEnabled()) continue;

            ItemStackWrapper resource = supplierEntry.getKey();
            long availableSurplus = supplier.inventory.getAmount(resource) - supplierCfg.minReserve();
            if (availableSurplus <= 0L) {
                sawSurplusBlocked = true;
                continue;
            }

            for (Object asset : assets) {
                if (!(asset instanceof AutomatedFacility requester)) continue;
                if (supplier.assetId.equals(requester.assetId)) continue;
                if (!Objects.equals(supplier.systemId, requester.systemId)) continue;

                LogisticsResourceConfig requesterCfg = requester.logisticsConfig.get(resource);
                if (requesterCfg == null || !requesterCfg.isImportEnabled()) continue;

                long requesterStock = requester.inventory.getAmount(resource);
                long inboundInTransit = inboundInTransitAmount(deliveries, requester.assetId, resource);
                long requestedAmount = Math.max(0L, requesterCfg.minReserve() - requesterStock - inboundInTransit);
                if (requestedAmount <= 0L) continue;
                Candidate candidate = candidateFor(
                    supplier,
                    requester,
                    availableSurplus,
                    requestedAmount,
                    requesterCfg,
                    hammer,
                    orbitalTime);
                sawAnyRequest = true;

                Status status = evaluateCandidate(hammer, candidate);
                if (status.code() == Code.READY) return status;
                bestBlockedStatus = prefer(status, bestBlockedStatus);
            }
        }

        if (bestBlockedStatus != null) return bestBlockedStatus;
        if (sawSurplusBlocked) return Status.simple(Code.NO_SURPLUS_AFTER_RESERVE, hammer);
        if (!sawAnyRequest) return Status.simple(Code.WAITING_FOR_REQUEST, hammer);
        return Status.simple(Code.WAITING_FOR_REQUEST, hammer);
    }

    public static Status evaluateCandidate(ModuleHammer hammer, Candidate candidate) {
        long sendAmount = dispatchAmount(
            hammer,
            candidate.availableSurplus(),
            candidate.requestedAmount(),
            candidate.orderSize());
        if (sendAmount < candidate.orderSize() || sendAmount <= 0L) {
            return new Status(
                Code.ORDER_BELOW_PACKAGE_SIZE,
                0L,
                hammer.energyStored(),
                sendAmount,
                candidate.orderSize());
        }
        if (!candidate.shareAnchor() && hammer.variant() != HammerVariant.BIG) {
            return new Status(Code.NEED_BIG_HAMMER, 0L, hammer.energyStored(), sendAmount, candidate.orderSize());
        }
        if (!candidate.sameBody() && !candidate.routeAvailable()) {
            return new Status(Code.ROUTE_UNAVAILABLE, 0L, hammer.energyStored(), sendAmount, candidate.orderSize());
        }
        if (!candidate.sameBody() && !hammer.config()
            .allows(candidate.departureDv(), candidate.tofSeconds())) {
            Code code = hammer.config()
                .mode() == AllowShootingConfig.Mode.WHEN_TOF_UNDER ? Code.BLOCKED_BY_TOF_LIMIT
                    : Code.BLOCKED_BY_DV_LIMIT;
            return new Status(code, 0L, hammer.energyStored(), sendAmount, candidate.orderSize());
        }

        long requiredEnergy = ModuleHammer.shotEnergyCost(candidate.sameBody() ? 1.0 : candidate.totalDv());
        if (!hammer.canSpendShotEnergy(requiredEnergy)) {
            return new Status(
                Code.NEED_ENERGY,
                requiredEnergy,
                hammer.energyStored(),
                sendAmount,
                candidate.orderSize());
        }
        return new Status(Code.READY, requiredEnergy, hammer.energyStored(), sendAmount, candidate.orderSize());
    }

    public static long dispatchAmount(ModuleHammer hammer, long availableSurplus, long requestedAmount, int orderSize) {
        return Math.min(Math.min(Math.min(requestedAmount, availableSurplus), orderSize), hammer.maxBatchSize());
    }

    private static Candidate candidateFor(AutomatedFacility supplier, AutomatedFacility requester,
        long availableSurplus, long requestedAmount, LogisticsResourceConfig requesterCfg, ModuleHammer hammer,
        double orbitalTime) {
        boolean sameBody = supplier.celestialObjectId.equals(requester.celestialObjectId);
        CelestialObject root = GalaxiaCelestialAPI.getPrimaryRoot();
        boolean shareAnchor = GalaxiaCelestialAPI
            .sharesPlanetaryAnchor(root, supplier.celestialObjectId, requester.celestialObjectId);

        if (sameBody) {
            return new Candidate(
                true,
                true,
                true,
                availableSurplus,
                requestedAmount,
                requesterCfg.orderSize(),
                1.0,
                1.0,
                0.0);
        }

        OrbitalTransferPlanner.TransferRoute route = routeBetween(root, supplier, requester, orbitalTime, hammer);
        if (route == null) {
            return new Candidate(
                false,
                shareAnchor,
                false,
                availableSurplus,
                requestedAmount,
                requesterCfg.orderSize(),
                0.0,
                0.0,
                0.0);
        }
        return new Candidate(
            false,
            shareAnchor,
            true,
            availableSurplus,
            requestedAmount,
            requesterCfg.orderSize(),
            route.departureDv(),
            route.totalDv(),
            route.tofSeconds());
    }

    private static OrbitalTransferPlanner.TransferRoute routeBetween(CelestialObject root, AutomatedFacility supplier,
        AutomatedFacility requester, double orbitalTime, ModuleHammer hammer) {
        CelestialObject srcBody = GalaxiaCelestialAPI.findBodyById(root, supplier.celestialObjectId);
        CelestialObject dstBody = GalaxiaCelestialAPI.findBodyById(root, requester.celestialObjectId);
        CelestialObject attractor = srcBody != null ? GalaxiaCelestialAPI.findStar(root, srcBody) : null;
        if (srcBody == null || dstBody == null || attractor == null) return null;
        return OrbitalTransferPlanner
            .computeRoute(root, attractor, srcBody, dstBody, orbitalTime, hammer.routePriority());
    }

    private static Status prefer(Status status, Status current) {
        if (current == null) return status;
        return priority(status.code()) > priority(current.code()) ? status : current;
    }

    private static int priority(Code code) {
        return switch (code) {
            case NEED_ENERGY -> 90;
            case BLOCKED_BY_DV_LIMIT, BLOCKED_BY_TOF_LIMIT -> 80;
            case NEED_BIG_HAMMER -> 70;
            case ROUTE_UNAVAILABLE -> 60;
            case ORDER_BELOW_PACKAGE_SIZE -> 50;
            case NO_SURPLUS_AFTER_RESERVE -> 40;
            case NO_EXPORT_CONFIG -> 30;
            case WAITING_FOR_REQUEST -> 20;
            case READY -> 100;
        };
    }

    private static long inboundInTransitAmount(Iterable<LogisticsDelivery> deliveries, CelestialAsset.ID toAssetId,
        ItemStackWrapper resource) {
        long total = 0L;
        for (LogisticsDelivery task : deliveries) {
            if (!toAssetId.equals(task.data.toAssetId())) continue;
            if (!resource.equals(task.data.resourceId())) continue;
            total += task.data.amount();
        }
        return total;
    }
}
