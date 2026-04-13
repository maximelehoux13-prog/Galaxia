package com.gtnewhorizons.galaxia.outpost.module;

import net.minecraft.util.StatCollector;

/**
 * Simple tag for module types. Actual data is in {@link com.gtnewhorizons.galaxia.outpost.module.OutpostModuleData}.
 */
public enum OutpostModuleKind {

    HAMMER,
    BIG_HAMMER,
    MINER,
    POWER;

    public String getDisplayName() {
        return StatCollector.translateToLocal("galaxia.outpost.module." + this.name().toLowerCase());
    }
}
