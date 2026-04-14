package com.gtnewhorizons.galaxia.registry.celestial;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import net.minecraft.item.ItemStack;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;

public final class CelestialAsset implements Buildable {

    public final ID assetId;
    public final CelestialObjectId celestialObjectId;
    public final Kind kind;
    public final Location location;

    private Status status;
    private Map<ItemStack, Long> requiredResources;
    private Map<ItemStack, Long> constructionInventory;
    private String displayName;

    public CelestialAsset(ID assetId, CelestialObjectId celestialObjectId, String displayName, Kind kind,
        Location location, Status status, Map<ItemStack, Long> requiredResources,
        Map<ItemStack, Long> constructionInventory) {
        requiredResources = requiredResources == null ? Collections.emptyMap()
            : Collections.unmodifiableMap(new LinkedHashMap<>(requiredResources));
        constructionInventory = constructionInventory == null ? Collections.emptyMap()
            : Collections.unmodifiableMap(new LinkedHashMap<>(constructionInventory));

        this.assetId = assetId;
        this.status = status;
        this.celestialObjectId = celestialObjectId;
        this.displayName = displayName;
        this.kind = kind;
        this.location = location;
        this.requiredResources = requiredResources;
        this.constructionInventory = constructionInventory;
    }

    public Map<ItemStack, Long> requiredResources() {
        return requiredResources;
    }

    public Map<ItemStack, Long> constructionInventory() {
        return constructionInventory;
    }

    @Override
    public Map<ItemStack, Long> getRequiredResources() {
        return requiredResources;
    }

    @Override
    public Map<ItemStack, Long> getConstructionInventory() {
        return constructionInventory;
    }

    public void setConstructionInventory(Map<ItemStack, Long> constructionInventory) {
        this.constructionInventory = constructionInventory;
    }

    @Override
    public Status status() {
        return status;
    }

    @Override
    public void updateStatus(Status status) {
        this.status = status;
    }

    public String displayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (CelestialAsset) obj;
        return Objects.equals(this.assetId, that.assetId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
            assetId,
            celestialObjectId,
            displayName,
            kind,
            location,
            status,
            requiredResources,
            constructionInventory);
    }

    public enum Kind {
        STATION,
        AUTOMATED_STATION,
        AUTOMATED_OUTPOST
    }

    public enum Location {
        ORBIT,
        SURFACE
    }

    @Desugar
    public record ID(UUID id) {

        public static ID create() {
            return new ID(UUID.randomUUID());
        }

        public static ID from(String value) {
            if (value == null) return null;
            return new ID(UUID.fromString(value));
        }

        public static ID from(UUID value) {
            return value == null ? null : new ID(value);
        }

        public static ID from(ID id) {
            if (id == null) return null;
            return new ID(id.id());
        }

        @Override
        public String toString() {
            return id.toString();
        }
    }
}
