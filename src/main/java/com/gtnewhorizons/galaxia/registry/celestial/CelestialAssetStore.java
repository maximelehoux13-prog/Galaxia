package com.gtnewhorizons.galaxia.registry.celestial;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.galaxia.api.GalaxiaCelestialAPI;
import com.gtnewhorizons.galaxia.core.persistence.OutpostDataStore;

public final class CelestialAssetStore {

    private static final Map<CelestialObjectId, List<CelestialAsset>> STATE_BY_BODY = new LinkedHashMap<>();
    private static final Map<CelestialAsset.ID, CelestialAsset> BY_ID = new LinkedHashMap<>();

    private CelestialAssetStore() {}

    public static List<CelestialAsset> getState(CelestialObjectId celestialObjectId) {
        List<CelestialAsset> celestialAssets = STATE_BY_BODY.computeIfAbsent(celestialObjectId, t -> new ArrayList<>());
        return new ArrayList<>(celestialAssets);
    }

    public static CelestialAsset findAsset(CelestialAsset.ID assetId) {
        return BY_ID.get(assetId);
    }

    public static List<CelestialAsset> allAssets() {
        List<CelestialAsset> all = new ArrayList<>();
        for (List<CelestialAsset> state : STATE_BY_BODY.values()) {
            all.addAll(state);
        }
        return Collections.unmodifiableList(all);
    }

    public static CelestialAsset createAssetInConstruction(CelestialObjectId celestialObjectId, String displayName,
        CelestialAsset.Kind kind, CelestialAsset.Location location) {
        List<CelestialAsset> celestialAssets = STATE_BY_BODY.computeIfAbsent(celestialObjectId, t -> new ArrayList<>());

        CelestialAsset asset = new CelestialAsset(
            CelestialAsset.ID.create(),
            celestialObjectId,
            displayName,
            kind,
            location,
            CelestialAsset.Status.CONSTRUCTION_SITE,
            defaultRequirements(kind),
            Collections.emptyMap());

        celestialAssets.add(asset);
        BY_ID.put(asset.assetId, asset);

        return asset;
    }

    public static CelestialAsset createOperationalAsset(CelestialObjectId celestialObjectId, String displayName,
        CelestialAsset.Kind kind, CelestialAsset.Location location) {
        List<CelestialAsset> celestialAssets = STATE_BY_BODY.computeIfAbsent(celestialObjectId, t -> new ArrayList<>());

        CelestialAsset asset = new CelestialAsset(
            CelestialAsset.ID.create(),
            celestialObjectId,
            displayName,
            kind,
            location,
            CelestialAsset.Status.OPERATIONAL,
            Collections.emptyMap(),
            Collections.emptyMap());

        celestialAssets.add(asset);
        BY_ID.put(asset.assetId, asset);

        return asset;
    }

    public static boolean destroyAsset(CelestialAsset.ID assetId) {
        CelestialAsset asset = BY_ID.remove(assetId);
        if (asset == null) return false;

        List<CelestialAsset> list = STATE_BY_BODY.get(asset.celestialObjectId);
        if (list != null) {
            list.remove(asset);
        }

        OutpostDataStore.get()
            .remove(assetId);
        return true;
    }

    public static boolean cancelConstruction(CelestialAsset.ID assetId) {
        CelestialAsset asset = BY_ID.get(assetId);
        if (asset == null || asset.status() != CelestialAsset.Status.CONSTRUCTION_SITE) {
            return false;
        }
        return destroyAsset(assetId);
    }

    public static boolean startDeconstruction(CelestialAsset.ID assetId) {
        CelestialAsset asset = BY_ID.get(assetId);
        if (asset == null || asset.status() != CelestialAsset.Status.CONSTRUCTION_SITE) {
            return false;
        }
        asset.updateStatus(CelestialAsset.Status.DECONSTRUCTION);
        return true;
    }

    public static boolean completeConstruction(CelestialAsset.ID assetId) {
        CelestialAsset asset = BY_ID.get(assetId);
        if (asset == null || asset.status() != CelestialAsset.Status.CONSTRUCTION_SITE) {
            return false;
        }
        asset.completeConstruction();
        return true;
    }

    public static boolean renameAsset(CelestialAsset.ID assetId, String displayName) {
        if (displayName == null || displayName.trim()
            .isEmpty()) {
            return false;
        }

        CelestialAsset asset = BY_ID.get(assetId);
        if (asset == null) return false;

        asset.setDisplayName(displayName.trim());
        return true;
    }

    public static boolean addToConstructionInventory(CelestialAsset.ID assetId, ItemStack stack, long amount) {
        if (stack == null || amount <= 0) return false;

        CelestialAsset asset = BY_ID.get(assetId);
        if (asset == null || asset.status() != CelestialAsset.Status.CONSTRUCTION_SITE) {
            return false;
        }

        Map<ItemStack, Long> inventory = mergeIntoConstructionInventory(asset.constructionInventory(), stack, amount);

        asset.setConstructionInventory(inventory);

        if (asset.isConstructionSatisfied()) {
            asset.updateStatus(CelestialAsset.Status.OPERATIONAL);
        }

        return true;
    }

    public static void clear() {
        STATE_BY_BODY.clear();
        BY_ID.clear();
    }

    public static void loadAssets(List<CelestialAsset> assets) {
        STATE_BY_BODY.clear();
        BY_ID.clear();

        if (assets == null || assets.isEmpty()) return;

        for (CelestialAsset asset : assets) {
            if (asset == null || asset.celestialObjectId == null || asset.assetId == null) continue;

            List<CelestialAsset> list = STATE_BY_BODY.computeIfAbsent(asset.celestialObjectId, t -> new ArrayList<>());

            list.add(asset);
            BY_ID.put(asset.assetId, asset);
        }
    }

    public static Map<ItemStack, Long> previewRequirements(CelestialAsset.Kind kind) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(defaultRequirements(kind)));
    }

    /// This is just used in the UI, I mark it as deprecated since it's just duplicate copied stuff, but I can't be
    /// bothered to fix the UI
    @Deprecated
    @Desugar
    public record TransferTarget(CelestialAsset.ID assetId, String displayName, CelestialObject hostBody) {}

    public static List<TransferTarget> getTransferTargetsInSystem(CelestialObject root, CelestialObject body) {
        List<TransferTarget> targets = new ArrayList<>();
        if (body == null) return targets;
        CelestialObject hostStar = GalaxiaCelestialAPI.findStar(root, body);
        if (hostStar == null) return targets;
        collectTransferTargets(hostStar, targets);
        return targets;
    }

    private static void collectTransferTargets(CelestialObject current, List<TransferTarget> targets) {
        List<CelestialAsset> state = getState(current.id());
        for (CelestialAsset asset : state) {
            if (asset.isManageable()) {
                targets.add(new TransferTarget(asset.assetId, asset.displayName(), current));
            }
        }
        for (CelestialObject child : GalaxiaCelestialAPI.getChildren(current)) {
            collectTransferTargets(child, targets);
        }
    }

    private static Map<ItemStack, Long> mergeIntoConstructionInventory(Map<ItemStack, Long> constructionInventory,
        ItemStack stack, long amount) {
        Map<ItemStack, Long> merged = new LinkedHashMap<>(constructionInventory);
        merged.merge(stack, amount, Long::sum);
        return merged;
    }

    // TODO: Find a better way to save this
    private static Map<ItemStack, Long> defaultRequirements(CelestialAsset.Kind kind) {
        Map<ItemStack, Long> required = new LinkedHashMap<>();
        switch (kind) {
            case STATION -> {}
            case AUTOMATED_STATION -> {
                required.put(new ItemStack(Blocks.stone), 64L);
                required.put(new ItemStack(Blocks.dirt), 64L);
            }
            case AUTOMATED_OUTPOST -> {
                required.put(new ItemStack(Blocks.stone), 64L);
                required.put(new ItemStack(Blocks.dirt), 64L);
            }
        }
        return required;
    }
}
