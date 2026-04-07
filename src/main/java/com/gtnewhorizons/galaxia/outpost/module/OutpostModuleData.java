package com.gtnewhorizons.galaxia.outpost.module;

/**
 * Marker interface for static (serializable) configuration data attached to an outpost module.
 * Each {@link com.gtnewhorizons.galaxia.outpost.OutpostModuleKind} has its own concrete record
 * implementing this interface. Runtime-only state (cooldowns, energy counters) lives in
 * {@link com.gtnewhorizons.galaxia.outpost.AutomatedOutpostModule} directly.
 *
 * <p>GSON polymorphic serialization is handled by
 * {@link com.gtnewhorizons.galaxia.outpost.persistence.OutpostPersistenceManager}
 * via a registered {@code TypeAdapter} that writes/reads a {@code "type"} discriminator field.
 */
public interface OutpostModuleData {}
