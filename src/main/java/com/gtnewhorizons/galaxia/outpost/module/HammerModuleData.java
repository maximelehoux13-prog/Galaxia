package com.gtnewhorizons.galaxia.outpost.module;

import com.github.bsideup.jabel.Desugar;

/**
 * Static configuration data for a {@link com.gtnewhorizons.galaxia.outpost.OutpostModuleKind#HAMMER} module.
 *
 * <p>Operational constants (not stored here – they are hardcoded in the engine):
 * <ul>
 *   <li>EU cost: 1 000 EU per item transferred.</li>
 *   <li>Max batch size: 64 items per {@code LogisticsTask}.</li>
 *   <li>Cooldown: 100 ticks (5 seconds) between operations.</li>
 * </ul>
 *
 * <p>Runtime mutable state (cooldownTicks, energyBuffer) is tracked in
 * {@link com.gtnewhorizons.galaxia.outpost.AutomatedOutpostModule} to avoid
 * record-copy overhead on every server tick.
 */
@Desugar
public record HammerModuleData() implements OutpostModuleData {

    /** The EU cost charged per item dispatched. */
    public static final long EU_PER_ITEM = 1_000L;

    /** Maximum number of items in a single logistics task. Larger orders are split. */
    public static final int MAX_BATCH_SIZE = 64;

    /** Ticks between successive HAMMER dispatches (5 s at 20 TPS). */
    public static final int COOLDOWN_TICKS = 100;
}
