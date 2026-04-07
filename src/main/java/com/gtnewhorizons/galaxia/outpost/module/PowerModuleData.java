package com.gtnewhorizons.galaxia.outpost.module;

import com.github.bsideup.jabel.Desugar;

/**
 * Static configuration data for a {@link com.gtnewhorizons.galaxia.outpost.OutpostModuleKind#POWER} module.
 */
@Desugar
public record PowerModuleData() implements OutpostModuleData {

    public static final long GENERATION_EU_PER_TICK = 2048L;
}
