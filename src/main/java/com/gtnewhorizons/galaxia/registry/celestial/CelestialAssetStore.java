package com.gtnewhorizons.galaxia.registry.celestial;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.gtnewhorizons.galaxia.outpost.module.AutomatedOutpostModule;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;

import com.gtnewhorizons.galaxia.outpost.persistence.OutpostDataStore;

public final class CelestialAssetStore {

    private static final Map<CelestialObjectId, MutableBodyState> STATE_BY_BODY = new LinkedHashMap<>();

    private CelestialAssetStore() {}

    public static CelestialBodyAssetState getState(CelestialObjectId celestialObjectId) {
        MutableBodyState state = STATE_BY_BODY.computeIfAbsent(celestialObjectId, MutableBodyState::new);
        return state.snapshot();
    }

    public static CelestialBodyAssetState getStateIfPresent(CelestialObjectId celestialObjectId) {
        MutableBodyState state = STATE_BY_BODY.get(celestialObjectId);
        if (state == null) {
            return new CelestialBodyAssetState(celestialObjectId, Collections.emptyList());
        }
        return state.snapshot();
    }

    public static CelestialManagedAsset createAssetInConstruction(CelestialObjectId celestialObjectId,
        String displayName, CelestialAsset.Kind kind, CelestialAsset.Location location) {
        MutableBodyState state = STATE_BY_BODY.computeIfAbsent(celestialObjectId, MutableBodyState::new);
        Map<ItemStack, Long> required = defaultRequirements(kind);
        CelestialManagedAsset asset = new CelestialManagedAsset(
            CelestialAsset.ID.create(),
            celestialObjectId,
            displayName,
            kind,
            location,
            CelestialAsset.Status.CONSTRUCTION_SITE,
            required,
            Collections.emptyMap());
        state.assets.add(asset);
        return asset;
    }

    public static CelestialManagedAsset createOperationalAsset(CelestialObjectId celestialObjectId, String displayName,
        CelestialAsset.Kind kind, CelestialAsset.Location location) {
        MutableBodyState state = STATE_BY_BODY.computeIfAbsent(celestialObjectId, MutableBodyState::new);
        CelestialManagedAsset asset = new CelestialManagedAsset(
            CelestialAsset.ID.create(),
            celestialObjectId,
            displayName,
            kind,
            location,
            CelestialAsset.Status.OPERATIONAL,
            Collections.emptyMap(),
            Collections.emptyMap());
        state.assets.add(asset);
        return asset;
    }

    public static boolean cancelConstruction(CelestialAsset.ID assetId) {
        for (MutableBodyState state : STATE_BY_BODY.values()) {
            for (int i = 0; i < state.assets.size(); i++) {
                CelestialManagedAsset asset = state.assets.get(i);
                if (asset.assetId()
                    .equals(assetId) && asset.status() == CelestialAsset.Status.CONSTRUCTION_SITE) {
                    state.assets.remove(i);
                    cleanupLogisticsForAsset(assetId);
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean startDeconstruction(CelestialAsset.ID assetId) {
        for (MutableBodyState state : STATE_BY_BODY.values()) {
            for (int i = 0; i < state.assets.size(); i++) {
                CelestialManagedAsset asset = state.assets.get(i);
                if (!asset.assetId()
                    .equals(assetId) || asset.status() != CelestialAsset.Status.CONSTRUCTION_SITE) {
                    continue;
                }
                state.assets.set(
                    i,
                    new CelestialManagedAsset(
                        asset.assetId(),
                        asset.celestialObjectId(),
                        asset.displayName(),
                        asset.kind(),
                        asset.location(),
                        CelestialAsset.Status.DECONSTRUCTION,
                        asset.requiredResources(),
                        asset.constructionInventory()));
                return true;
            }
        }
        return false;
    }

    public static boolean completeConstruction(String assetId) {
        for (MutableBodyState state : STATE_BY_BODY.values()) {
            for (int i = 0; i < state.assets.size(); i++) {
                CelestialManagedAsset asset = state.assets.get(i);
                if (!asset.assetId()
                    .equals(assetId) || asset.status() != CelestialAsset.Status.CONSTRUCTION_SITE) {
                    continue;
                }
                state.assets.set(i, toOperationalAsset(asset));
                return true;
            }
        }
        return false;
    }

    public static boolean addToConstructionInventory(String assetId, ItemStack stack, long amount) {
        if (stack == null || amount <= 0) {
            return false;
        }

        for (MutableBodyState state : STATE_BY_BODY.values()) {
            for (int i = 0; i < state.assets.size(); i++) {
                CelestialManagedAsset asset = state.assets.get(i);
                if (!asset.assetId()
                    .equals(assetId) || asset.status() != CelestialAsset.Status.CONSTRUCTION_SITE) {
                    continue;
                }

                Map<ItemStack, Long> inventory = mergeIntoConstructionInventory(
                    asset.constructionInventory(),
                    stack,
                    amount);
                CelestialManagedAsset updated = new CelestialManagedAsset(
                    asset.assetId(),
                    asset.celestialObjectId(),
                    asset.displayName(),
                    asset.kind(),
                    asset.location(),
                    asset.status(),
                    asset.requiredResources(),
                    inventory);
                state.assets.set(i, isConstructionSatisfied(updated) ? toOperationalAsset(updated) : updated);
                return true;
            }
        }
        return false;
    }

    public static CelestialManagedAsset findAsset(CelestialAsset.ID assetId) {
        for (MutableBodyState state : STATE_BY_BODY.values()) {
            for (CelestialManagedAsset asset : state.assets) {
                if (asset.assetId()
                    .equals(assetId)) return asset;
            }
        }
        return null;
    }

    public static boolean destroyAsset(CelestialAsset.ID assetId) {
        for (MutableBodyState state : STATE_BY_BODY.values()) {
            for (int i = 0; i < state.assets.size(); i++) {
                if (state.assets.get(i)
                    .assetId()
                    .equals(assetId)) {
                    state.assets.remove(i);
                    cleanupLogisticsForAsset(assetId);
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean renameAsset(CelestialAsset.ID assetId, String displayName) {
        if (displayName == null || displayName.trim()
            .isEmpty()) {
            return false;
        }
        String trimmedName = displayName.trim();
        for (MutableBodyState state : STATE_BY_BODY.values()) {
            for (int i = 0; i < state.assets.size(); i++) {
                CelestialManagedAsset asset = state.assets.get(i);
                if (!asset.assetId()
                    .equals(assetId)) {
                    continue;
                }
                state.assets.set(
                    i,
                    new CelestialManagedAsset(
                        asset.assetId(),
                        asset.celestialObjectId(),
                        trimmedName,
                        asset.kind(),
                        asset.location(),
                        asset.status(),
                        asset.requiredResources(),
                        asset.constructionInventory()));
                return true;
            }
        }
        return false;
    }

    public static void clear() {
        STATE_BY_BODY.clear();
    }

    public static List<CelestialManagedAsset> allAssets() {
        List<CelestialManagedAsset> all = new ArrayList<>();
        for (MutableBodyState state : STATE_BY_BODY.values()) {
            all.addAll(state.assets);
        }
        return Collections.unmodifiableList(all);
    }

    public static void loadAssets(List<CelestialManagedAsset> assets) {
        STATE_BY_BODY.clear();
        if (assets == null || assets.isEmpty()) return;
        for (CelestialManagedAsset asset : assets) {
            if (asset == null || asset.celestialObjectId() == null || asset.assetId() == null) continue;
            CelestialObjectId objectId = asset.celestialObjectId();
            MutableBodyState state = STATE_BY_BODY.computeIfAbsent(objectId, MutableBodyState::new);
            state.assets.add(asset);
        }
    }

    public static Map<ItemStack, Long> previewRequirements(CelestialAsset.Kind kind) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(defaultRequirements(kind)));
    }

    private static CelestialManagedAsset toOperationalAsset(CelestialManagedAsset asset) {
        return new CelestialManagedAsset(
            asset.assetId(),
            asset.celestialObjectId(),
            asset.displayName(),
            asset.kind(),
            asset.location(),
            CelestialAsset.Status.OPERATIONAL,
            asset.requiredResources(),
            asset.constructionInventory());
    }

    private static boolean isConstructionSatisfied(CelestialManagedAsset asset) {
        for (Map.Entry<ItemStack, Long> required : asset.requiredResources().entrySet()) {
            long available = asset.constructionInventory().getOrDefault(required.getKey(), 0L);
            if (available < required.getValue()) {
                return false;
            }
        }
        return true;
    }

    private static Map<ItemStack, Long> mergeIntoConstructionInventory(
        Map<ItemStack, Long> constructionInventory, ItemStack stack, long amount) {
        Map<ItemStack, Long> merged = new LinkedHashMap<>(constructionInventory);
        merged.merge(stack, amount, Long::sum);
        return merged;
    }

    private static Map<ItemStack, Long> defaultRequirements(CelestialAsset.Kind kind) {
        Map<ItemStack, Long> required = new LinkedHashMap<>();
        switch (kind) {
            case STATION -> {}
            case AUTOMATED_STATION -> {
                required.put(new net.minecraft.item.ItemStack(Blocks.stone), 64L);
                required.put(new net.minecraft.item.ItemStack(Blocks.dirt), 64L);
            }
            case AUTOMATED_OUTPOST -> {
                required.put(new net.minecraft.item.ItemStack(Blocks.stone), 64L);
                required.put(new net.minecraft.item.ItemStack(Blocks.dirt), 64L);
            }
        }
        return required;
    }

    private static void cleanupLogisticsForAsset(CelestialAsset.ID assetId) {
        OutpostDataStore.get()
            .remove(assetId);
    }

    private static final class MutableBodyState {

        private final CelestialObjectId celestialObjectId;
        private final List<CelestialManagedAsset> assets = new ArrayList<>();

        private MutableBodyState(CelestialObjectId celestialObjectId) {
            this.celestialObjectId = celestialObjectId;
        }

        private CelestialBodyAssetState snapshot() {
            return new CelestialBodyAssetState(celestialObjectId, assets);
        }
    }
}
