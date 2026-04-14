package com.gtnewhorizons.galaxia.registry.celestial;

import java.util.UUID;

import org.jetbrains.annotations.NotNull;

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

        public static @NotNull ID create() {
            return new ID(UUID.randomUUID());
        }

        public boolean equals(@NotNull ID id) {
            return this.id.equals(id.id);
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
