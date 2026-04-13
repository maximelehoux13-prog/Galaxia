package com.gtnewhorizons.galaxia.outpost.module;

import java.util.Map;

import com.gtnewhorizons.galaxia.outpost.AutomatedOutpostModule;
import com.gtnewhorizons.galaxia.outpost.AutomatedOutpostState;
import com.gtnewhorizons.galaxia.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.outpost.OutpostModuleKind;

/**
 * Static configuration data and behavior for an outpost module.
 * Each module type has a concrete record implementing this interface.
 *
 * <p>
 * GSON polymorphic serialization is handled by
 * {@link com.gtnewhorizons.galaxia.outpost.persistence.OutpostPersistenceManager}
 * via a registered {@code TypeAdapter} that writes/reads a {@code "type"} discriminator field.
 */
public interface OutpostModuleData {

    /** Module type identifier. */
    OutpostModuleKind moduleKind();

    /** Default energy capacity in EU. */
    long baseEnergyCapacity();

    /** EU consumed per tick while operational (positive) or generated (negative). */
    int powerDrawEuPerTick();

    /** Resources required for construction. */
    Map<ItemStackWrapper, Integer> requiredResources();

    /** Tick the module logic. Called each server tick when module is OPERATIONAL. */
    void tick(AutomatedOutpostModule module, AutomatedOutpostState outpost);
}
