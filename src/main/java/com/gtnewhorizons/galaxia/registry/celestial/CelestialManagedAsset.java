package com.gtnewhorizons.galaxia.registry.celestial;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.item.ItemStack;

import com.github.bsideup.jabel.Desugar;

@Desugar
public record CelestialManagedAsset(CelestialAsset.ID assetId, CelestialObjectId celestialObjectId, String displayName,
    CelestialAsset.Kind kind, CelestialAsset.Location location, CelestialAsset.Status status,
    Map<ItemStack, Long> requiredResources, Map<ItemStack, Long> constructionInventory) {

    public CelestialManagedAsset {
        requiredResources = requiredResources == null ? Collections.emptyMap()
            : Collections.unmodifiableMap(new LinkedHashMap<>(requiredResources));
        constructionInventory = constructionInventory == null ? Collections.emptyMap()
            : Collections.unmodifiableMap(new LinkedHashMap<>(constructionInventory));
    }
}
