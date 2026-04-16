package com.gtnewhorizons.galaxia.registry.outpost.logistics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedOutpost;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.LogisticsConfiguration;
import com.gtnewhorizons.galaxia.registry.outpost.LogisticsResourceConfig;

public final class LogisticStore {

    private static final List<LogisticsDelivery> activeDeliveries = new ArrayList<>();
    private static final Map<LogisticSignal.Scope, Map<CelestialObjectId, List<LogisticSignal>>> signals = new LinkedHashMap<>();

    private LogisticStore() {}

    public static List<LogisticsDelivery> activeDeliveries() {
        return Collections.unmodifiableList(activeDeliveries);
    }

    public static void addDelivery(LogisticsDelivery delivery) {
        activeDeliveries.add(delivery);
    }

    public static List<LogisticsDelivery> tickDeliveries() {
        List<LogisticsDelivery> arrived = new ArrayList<>();
        for (int i = activeDeliveries.size() - 1; i >= 0; i--) {
            LogisticsDelivery current = activeDeliveries.get(i);
            if (CelestialAssetStore.findAsset(current.data.fromAssetId()) == null
                || CelestialAssetStore.findAsset(current.data.toAssetId()) == null) {
                activeDeliveries.remove(i);
                continue;
            }
            LogisticsDelivery ticked = current.tick();
            if (ticked.isArrived()) {
                activeDeliveries.remove(i);
                arrived.add(ticked);
            } else {
                activeDeliveries.set(i, ticked);
            }
        }
        return arrived;
    }

    public static void rebuildSignals(List<AutomatedOutpost> outposts) {
        signals.clear();
        for (AutomatedOutpost outpost : outposts) {
            emitSignals(outpost);
        }
    }

    private static void emitSignals(AutomatedOutpost outpost) {
        Map<ItemStackWrapper, Long> snapshot = outpost.inventory.snapshot();
        LogisticsConfiguration config = outpost.logisticsConfig;

        List<ItemStackWrapper> resources = new ArrayList<>(
            config.snapshot()
                .keySet());
        for (ItemStackWrapper r : snapshot.keySet()) {
            if (!resources.contains(r)) resources.add(r);
        }

        CelestialObjectId bodyId = outpost.celestialObjectId;
        CelestialObjectId systemId = outpost.systemId;
        CelestialObjectId planetaryAnchorBodyId = outpost.planetaryAnchorBodyId;

        for (ItemStackWrapper resource : resources) {
            LogisticsResourceConfig cfg = config.get(resource);
            long stock = outpost.inventory.getAmount(resource);
            long min = cfg.minReserve();
            long diff = stock - min;

            if (diff == 0) continue;

            boolean importCase = diff < 0 && cfg.isImportEnabled();
            boolean supplyCase = diff > 0 && cfg.isSupplyEnabled();

            if (importCase || supplyCase) {
                addSignal(new LogisticSignal(
                    outpost.assetId,
                    systemId,
                    resource,
                    diff,
                    LogisticSignal.Scope.SYSTEM,
                    bodyId,
                    planetaryAnchorBodyId
                ));
            }
        }
    }

    private static void addSignal(LogisticSignal signal) {
        CelestialObjectId scopeKey = scopeKeyFor(signal);
        if (scopeKey == null) return;
        signals.computeIfAbsent(signal.scope(), s -> new LinkedHashMap<>())
            .computeIfAbsent(scopeKey, k -> new ArrayList<>())
            .add(signal);
    }

    public static List<LogisticSignal> getSignals(LogisticSignal.Scope scope, CelestialObjectId scopeKey) {
        Map<CelestialObjectId, List<LogisticSignal>> byKey = signals.get(scope);
        if (byKey == null) return Collections.emptyList();
        List<LogisticSignal> list = byKey.get(scopeKey);
        return list == null ? Collections.emptyList() : Collections.unmodifiableList(list);
    }

    public static Map<CelestialObjectId, List<LogisticSignal>> allSignalsForScope(LogisticSignal.Scope scope) {
        Map<CelestialObjectId, List<LogisticSignal>> byKey = signals.get(scope);
        if (byKey == null) return Collections.emptyMap();
        Map<CelestialObjectId, List<LogisticSignal>> safe = new LinkedHashMap<>(byKey.size());
        for (Map.Entry<CelestialObjectId, List<LogisticSignal>> e : byKey.entrySet()) {
            safe.put(e.getKey(), Collections.unmodifiableList(e.getValue()));
        }
        return Collections.unmodifiableMap(safe);
    }

    private static CelestialObjectId scopeKeyFor(LogisticSignal signal) {
        return switch (signal.scope()) {
            case PLANETARY -> signal.planetaryAnchorBodyId();
            case SYSTEM -> signal.systemId();
            case GALACTIC -> signal.systemId();
        };
    }

    public static void clear() {
        signals.clear();
        activeDeliveries.clear();
    }
}
