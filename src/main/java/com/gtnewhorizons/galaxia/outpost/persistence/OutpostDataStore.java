package com.gtnewhorizons.galaxia.outpost.persistence;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.gtnewhorizons.galaxia.outpost.AutomatedOutpostState;

/**
 * In-memory store for all {@link AutomatedOutpostState} instances.
 *
 * <p>Two access paths:
 * <ul>
 *   <li>By asset id – O(1) lookup for logistics engine and packet handlers.</li>
 *   <li>By team UUID – used by {@link AutomatedTeamMigrationHandler} and GUI queries.</li>
 * </ul>
 *
 * <p>Populated by {@link OutpostPersistenceManager} on world load;
 * flushed to disk on WorldEvent.Save.
 * All access is from the server thread only (no synchronization needed).
 */
public final class OutpostDataStore {

    private static final OutpostDataStore INSTANCE = new OutpostDataStore();

    /** Primary index: assetId → state. */
    private final Map<String, AutomatedOutpostState> byAssetId = new LinkedHashMap<>();

    /** Secondary index: teamId → (assetId → state). */
    private final Map<UUID, Map<String, AutomatedOutpostState>> byTeam = new LinkedHashMap<>();

    private OutpostDataStore() {}

    public static OutpostDataStore get() {
        return INSTANCE;
    }

    // -------------------------------------------------------------------------
    // Write
    // -------------------------------------------------------------------------

    /** Registers a new outpost state in both indexes. */
    public void put(AutomatedOutpostState state) {
        byAssetId.put(state.assetId, state);
        byTeam.computeIfAbsent(state.teamId, k -> new LinkedHashMap<>())
            .put(state.assetId, state);
    }

    /** Removes an outpost from both indexes. Returns the removed state or {@code null}. */
    public AutomatedOutpostState remove(String assetId) {
        AutomatedOutpostState removed = byAssetId.remove(assetId);
        if (removed != null) {
            Map<String, AutomatedOutpostState> teamMap = byTeam.get(removed.teamId);
            if (teamMap != null) {
                teamMap.remove(assetId);
                if (teamMap.isEmpty()) byTeam.remove(removed.teamId);
            }
        }
        return removed;
    }

    /**
     * Moves all outposts that belong to {@code oldTeamId} over to {@code newTeamId}.
     * Used by {@link AutomatedTeamMigrationHandler}.
     */
    public void migrateTeam(UUID oldTeamId, UUID newTeamId) {
        Map<String, AutomatedOutpostState> teamMap = byTeam.remove(oldTeamId);
        if (teamMap == null || teamMap.isEmpty()) return;
        // Re-create states with the new team id and rebuild indexes.
        Map<String, AutomatedOutpostState> newMap = new LinkedHashMap<>();
        for (AutomatedOutpostState old : teamMap.values()) {
            AutomatedOutpostState migrated = new AutomatedOutpostState(
                old.assetId,
                newTeamId,
                old.celestialBodyId,
                old.systemId);
            migrated.setEnergyStored(old.getEnergyStored());
            // Transfer modules.
            for (com.gtnewhorizons.galaxia.outpost.AutomatedOutpostModule m : old.modulesInternal()) {
                migrated.addModule(m);
            }
            // Transfer inventory contents.
            migrated.inventory.loadFromSnapshot(old.inventory.snapshot());
            // Transfer logistics config.
            migrated.logisticsConfig.loadFromSnapshot(old.logisticsConfig.snapshot());
            byAssetId.put(migrated.assetId, migrated);
            newMap.put(migrated.assetId, migrated);
        }
        byTeam.put(newTeamId, newMap);
    }

    /** Clears all stored state (called on world unload). */
    public void clear() {
        byAssetId.clear();
        byTeam.clear();
    }

    // -------------------------------------------------------------------------
    // Read
    // -------------------------------------------------------------------------

    /** Returns the outpost state for the given asset id, or {@code null} if absent. */
    public AutomatedOutpostState getByAssetId(String assetId) {
        return byAssetId.get(assetId);
    }

    /**
     * Returns an unmodifiable view of all outposts belonging to the given team.
     * Returns an empty collection if the team has no outposts.
     */
    public Collection<AutomatedOutpostState> getByTeam(UUID teamId) {
        Map<String, AutomatedOutpostState> teamMap = byTeam.get(teamId);
        return teamMap == null ? Collections.emptyList()
            : Collections.unmodifiableCollection(teamMap.values());
    }

    /** Returns an unmodifiable view of ALL outposts across all teams. */
    public Collection<AutomatedOutpostState> allOutposts() {
        return Collections.unmodifiableCollection(byAssetId.values());
    }
}
