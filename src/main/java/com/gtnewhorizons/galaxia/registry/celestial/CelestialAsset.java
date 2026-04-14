package com.gtnewhorizons.galaxia.registry.celestial;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.github.bsideup.jabel.Desugar;
import net.minecraft.item.ItemStack;

public final class CelestialAsset {
    public final ID assetId;
    public final CelestialObjectId celestialObjectId;
    public final Kind kind;
    public final Location location;

    private Map<ItemStack, Long> requiredResources;
    private Map<ItemStack, Long> constructionInventory;
    private Status status;
    private String displayName;

    public CelestialAsset(ID assetId, CelestialObjectId celestialObjectId, String displayName, Kind kind, Location location, Status status, Map<ItemStack, Long> requiredResources, Map<ItemStack, Long> constructionInventory) {
        requiredResources = requiredResources == null ? Collections.emptyMap()
            : Collections.unmodifiableMap(new LinkedHashMap<>(requiredResources));
        constructionInventory = constructionInventory == null ? Collections.emptyMap()
            : Collections.unmodifiableMap(new LinkedHashMap<>(constructionInventory));

        this.assetId = assetId;
        this.celestialObjectId = celestialObjectId;
        this.displayName = displayName;
        this.kind = kind;
        this.location = location;
        this.status = status;
        this.requiredResources = requiredResources;
        this.constructionInventory = constructionInventory;
    }

    public Map<ItemStack, Long> requiredResources() {
        return requiredResources;
    }

    public Map<ItemStack, Long> constructionInventory() {
        return constructionInventory;
    }

    public boolean isManageble() {
        return this.status() != CelestialAsset.Status.OPERATIONAL;
    }

    public Map<ItemStack, Long> getRequiredResources() {
        return requiredResources;
    }

    public void setRequiredResources(Map<ItemStack, Long> requiredResources) {
        this.requiredResources = requiredResources;
    }

    public Map<ItemStack, Long> getConstructionInventory() {
        return constructionInventory;
    }

    public void setConstructionInventory(Map<ItemStack, Long> constructionInventory) {
        this.constructionInventory = constructionInventory;
    }

    public Status status() {
        return status;
    }

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
        return Objects.hash(assetId, celestialObjectId, displayName, kind, location, status, requiredResources, constructionInventory);
    }

    @Override
    public String toString() {
        return "CelestialAsset[" +
            "assetId=" + assetId + ", " +
            "celestialObjectId=" + celestialObjectId + ", " +
            "displayName=" + displayName + ", " +
            "kind=" + kind + ", " +
            "location=" + location + ", " +
            "status=" + status + ", " +
            "requiredResources=" + requiredResources + ", " +
            "constructionInventory=" + constructionInventory + ']';
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

        public String toString() {
            return id.toString();
        }
    }

    public enum Status {
        CONSTRUCTION_SITE,
        DECONSTRUCTION,
        OPERATIONAL,
        DISABLED,
        DESTROYED
    }
}
