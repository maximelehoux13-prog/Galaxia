package com.gtnewhorizons.galaxia.outpost.module;

import com.github.bsideup.jabel.Desugar;

/**
 * Static configuration data for a {@link com.gtnewhorizons.galaxia.outpost.OutpostModuleKind#BIG_HAMMER} module.
 *
 * <p>BIG HAMMER operates within the same stellar system as the host outpost without
 * range restrictions, batch-size limits, or EU cost. It has no configurable parameters,
 * but the record exists as a typed placeholder for GSON polymorphic serialization.
 */
@Desugar
public record BigHammerModuleData() implements OutpostModuleData {}
