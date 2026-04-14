package com.gtnewhorizons.galaxia.registry.celestial;

import java.util.UUID;

import com.github.bsideup.jabel.Desugar;

public class CelestialAsset {

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

        public static ID from(CelestialAsset.ID id) {
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
