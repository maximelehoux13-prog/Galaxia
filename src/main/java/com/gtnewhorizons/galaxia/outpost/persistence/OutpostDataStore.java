package com.gtnewhorizons.galaxia.outpost.persistence;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.galaxia.outpost.AutomatedOutpost;
import com.gtnewhorizons.galaxia.outpost.ItemStackWrapper;

/**
 * In-memory store for all {@link AutomatedOutpost} instances.
 *
 * <p>
 * Two access paths:
 * <ul>
 * <li>By asset id – O(1) lookup for logistics engine and packet handlers.</li>
 * <li>By team UUID – used by {@link AutomatedTeamMigrationHandler} and GUI queries.</li>
 * </ul>
 *
 * <p>
 * Populated by {@link OutpostPersistenceManager} on world load;
 * flushed to disk on WorldEvent.Save.
 * All access is from the server thread only (no synchronization needed).
 */
public final class OutpostDataStore {

    private static final OutpostDataStore INSTANCE = new OutpostDataStore();

    /** Primary index: assetId → state. */
    private final Map<String, AutomatedOutpost> byAssetId = new LinkedHashMap<>();

    /** Secondary index: teamId → (assetId → state). */
    private final Map<UUID, Map<String, AutomatedOutpost>> byTeam = new LinkedHashMap<>();

    /**
     * Client-side snapshot of aggregated logistics signals, indexed by system id.
     * Updated by {@link com.gtnewhorizons.galaxia.outpost.network.LogisticsSignalsSyncPacket}.
     * Always empty on the server; never null.
     * <p>
     * Inner map: resourceKey → net signed amount (positive = surplus, negative = deficit).
     */
    private final Map<String, Map<String, Long>> clientSystemSignals = new LinkedHashMap<>();

    /**
     * Client-side snapshot of aggregated logistics signals, indexed by planetary anchor body id.
     * Updated alongside {@link #clientSystemSignals}.
     */
    private final Map<String, Map<String, Long>> clientPlanetSignals = new LinkedHashMap<>();

    private int clientSignalRevision = 0;

    /**
     * Client-side snapshot of in-flight logistics tasks. Updated by
     * {@link com.gtnewhorizons.galaxia.outpost.network.LogisticsTasksSyncPacket}.
     * Always empty on the server; never null.
     */
    private final List<ClientLogisticsTask> clientTasks = new ArrayList<>();
    private int clientTaskRevision = 0;

    private OutpostDataStore() {}

    public static OutpostDataStore get() {
        return INSTANCE;
    }

    // -------------------------------------------------------------------------
    // Write
    // -------------------------------------------------------------------------

    /** Registers a new outpost state in both indexes. */
    public void put(AutomatedOutpost state) {
        byAssetId.put(state.assetId, state);
        byTeam.computeIfAbsent(state.teamId, k -> new LinkedHashMap<>())
            .put(state.assetId, state);
    }

    /** Removes an outpost from both indexes. Returns the removed state or {@code null}. */
    public AutomatedOutpost remove(String assetId) {
        AutomatedOutpost removed = byAssetId.remove(assetId);
        if (removed != null) {
            Map<String, AutomatedOutpost> teamMap = byTeam.get(removed.teamId);
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
        Map<String, AutomatedOutpost> teamMap = byTeam.remove(oldTeamId);
        if (teamMap == null || teamMap.isEmpty()) return;
        // Re-create states with the new team id and rebuild indexes.
        Map<String, AutomatedOutpost> newMap = new LinkedHashMap<>();
        for (AutomatedOutpost old : teamMap.values()) {
            AutomatedOutpost migrated = new AutomatedOutpost(
                old.assetId,
                newTeamId,
                old.celestialBodyId,
                old.systemId,
                old.planetaryAnchorBodyId);
            migrated.setEnergyStored(old.getEnergyStored());
            // Transfer modules.
            for (com.gtnewhorizons.galaxia.outpost.module.AutomatedOutpostModule m : old.modules()) {
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
    public AutomatedOutpost getByAssetId(String assetId) {
        return byAssetId.get(assetId);
    }

    /**
     * Returns an unmodifiable view of all outposts belonging to the given team.
     * Returns an empty collection if the team has no outposts.
     */
    public Collection<AutomatedOutpost> getByTeam(UUID teamId) {
        Map<String, AutomatedOutpost> teamMap = byTeam.get(teamId);
        return teamMap == null ? Collections.emptyList() : Collections.unmodifiableCollection(teamMap.values());
    }

    /** Returns an unmodifiable view of ALL outposts across all teams. */
    public Collection<AutomatedOutpost> allOutposts() {
        return Collections.unmodifiableCollection(byAssetId.values());
    }

    // -------------------------------------------------------------------------
    // Client-side signal snapshot (populated by LogisticsSignalsSyncPacket)
    // -------------------------------------------------------------------------

    /**
     * Replaces the client signal maps and bumps the signal revision counter.
     * Client-side only.
     */
    public void updateClientSignals(Map<String, Map<String, Long>> bySystem, Map<String, Map<String, Long>> byPlanet) {
        clientSystemSignals.clear();
        clientSystemSignals.putAll(bySystem);
        clientPlanetSignals.clear();
        clientPlanetSignals.putAll(byPlanet);
        clientSignalRevision++;
    }

    /**
     * Returns the aggregated net amounts (resourceKey → netAmount) for the given
     * star system, or an empty map if none are available.
     */
    public Map<String, Long> clientSignalsForSystem(String systemId) {
        Map<String, Long> result = clientSystemSignals.get(systemId);
        return result != null ? Collections.unmodifiableMap(result) : Collections.emptyMap();
    }

    /**
     * Returns the aggregated net amounts (resourceKey → netAmount) for the given
     * planetary anchor body, or an empty map if none are available.
     */
    public Map<String, Long> clientSignalsForPlanet(String anchorBodyId) {
        Map<String, Long> result = clientPlanetSignals.get(anchorBodyId);
        return result != null ? Collections.unmodifiableMap(result) : Collections.emptyMap();
    }

    /** Monotonically incrementing counter; bumped each time signal data is replaced. */
    public int clientSignalRevision() {
        return clientSignalRevision;
    }

    // -------------------------------------------------------------------------
    // Client-side task snapshot (populated by LogisticsTasksSyncPacket)
    // -------------------------------------------------------------------------

    /** Replaces the client task list and bumps the revision counter. Client-side only. */
    public void updateClientTasks(List<ClientLogisticsTask> tasks) {
        clientTasks.clear();
        clientTasks.addAll(tasks);
        clientTaskRevision++;
    }

    /** Returns an unmodifiable view of the latest client task snapshot. */
    public List<ClientLogisticsTask> clientTasks() {
        return Collections.unmodifiableList(clientTasks);
    }

    /** Monotonically incrementing counter; bumped each time tasks are replaced. */
    public int clientTaskRevision() {
        return clientTaskRevision;
    }

    /**
     * Lightweight descriptor for an in-flight logistics shipment, held client-side.
     *
     * @param taskId               stable server task id
     * @param resource             the item being transported
     * @param amount               number of units in this shipment
     * @param transportKind        HAMMER/BIG_HAMMER
     * @param fromBodyId           celestial body id of the departure outpost
     * @param toBodyId             celestial body id of the destination outpost
     * @param departureOrbitalTime departure time in orbital simulation units
     * @param tofOrbitalSeconds    time of flight in orbital simulation units
     */
    @Desugar
    public record ClientLogisticsTask(String taskId, ItemStackWrapper resource, long amount, String transportKind,
        String fromBodyId, String toBodyId, double departureOrbitalTime, double tofOrbitalSeconds) {}
}
