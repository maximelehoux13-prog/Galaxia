package com.gtnewhorizons.galaxia.outpost.persistence;

import java.util.UUID;

import com.gtnewhorizons.galaxia.core.Galaxia;

/**
 * Handles team membership changes from NHLib.
 *
 * <p>
 * When a player moves from one team to another, all outposts owned by the player's
 * old team UUID are re-associated with the new team UUID. This involves:
 * <ol>
 * <li>Moving outpost JSON from {@code galaxiadata/[oldTeamUUID]/} to
 * {@code galaxiadata/[newTeamUUID]/} (handled by merging in-memory state and
 * relying on the next WorldEvent.Save to write the updated folder layout).</li>
 * <li>Updating the in-memory {@link OutpostDataStore} via
 * {@link OutpostDataStore#migrateTeam(UUID, UUID)}.</li>
 * </ol>
 *
 * <h3>NHLib integration</h3>
 * NHLib exposes team-change events via its own event bus. Subscribe to the relevant
 * NHLib event and call {@link #onTeamChanged(UUID, UUID)} from that handler.
 *
 * <pre>
 * // Example (fill in the correct NHLib event class):
 * {@literal @}SubscribeEvent
 * public void onNHLibTeamChange(NHLibTeamChangeEvent event) {
 *     AutomatedTeamMigrationHandler.get().onTeamChanged(event.oldTeamId(), event.newTeamId());
 * }
 * </pre>
 *
 * <p>
 * If NHLib does not expose a suitable event, call {@link #onTeamChanged(UUID, UUID)}
 * from any NHLib callback that fires when a player's team membership is updated.
 *
 * <p>
 * This class does NOT depend on NHLib at compile time; the wiring is done via
 * reflection or by the caller that has NHLib on its classpath.
 */
public final class AutomatedTeamMigrationHandler {

    private static final AutomatedTeamMigrationHandler INSTANCE = new AutomatedTeamMigrationHandler();

    private AutomatedTeamMigrationHandler() {}

    public static AutomatedTeamMigrationHandler get() {
        return INSTANCE;
    }

    /**
     * Migrates all outpost data from {@code oldTeamId} to {@code newTeamId}.
     *
     * <p>
     * Safe to call even when {@code oldTeamId} has no outposts – it becomes a no-op.
     * The old team folder in {@code galaxiadata/} will be absent from the next save,
     * and the new team folder will contain the merged outposts.
     *
     * @param oldTeamId the UUID of the team the player is leaving
     * @param newTeamId the UUID of the team the player is joining (may differ from old)
     */
    public void onTeamChanged(UUID oldTeamId, UUID newTeamId) {
        if (oldTeamId.equals(newTeamId)) return;
        int countBefore = OutpostDataStore.get()
            .getByTeam(oldTeamId)
            .size();
        OutpostDataStore.get()
            .migrateTeam(oldTeamId, newTeamId);
        int countAfter = OutpostDataStore.get()
            .getByTeam(newTeamId)
            .size();
        Galaxia.LOG.info(
            "[Logistics] Team migration: {} → {} | transferred {} outpost(s), destination team now has {}.",
            oldTeamId,
            newTeamId,
            countBefore,
            countAfter);
    }
}
