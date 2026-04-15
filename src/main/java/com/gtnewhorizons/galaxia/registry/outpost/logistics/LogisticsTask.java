package com.gtnewhorizons.galaxia.registry.outpost.logistics;

import java.util.UUID;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.galaxia.core.persistence.OutpostDataStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.interfaces.WithUUID;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedOutpost;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;

/**
 * An in-flight logistics shipment between two outposts.
 *
 * <p>
 * Tasks are created by {@link OutpostLogisticsEngine} when a supply signal is matched
 * to a request signal. They count down via {@link #remainingTicks} and deliver resources
 * to the destination buffer on arrival (when {@code remainingTicks} reaches zero).
 *
 * <p>
 * Tasks are persisted to JSON so that in-flight shipments survive world restarts.
 * Resources are consumed from the source buffer immediately on task creation and added
 * to the destination on arrival.
 *
 * <h3>Trajectory metadata</h3>
 * {@code fromBodyId}, {@code toBodyId}, {@code departureOrbitalTime} and
 * {@code tofOrbitalSeconds} are optional trajectory metadata used by the client to
 * render the in-flight arc on the starmap. They may be empty/zero for legacy tasks or
 * same-body instant transfers.
 */

public class LogisticsTask {

    public enum TransportType {
        HAMMER,
        BIG_HAMMER,
    }

    /** Unique task id (UUID string). */
    public final ID taskId;
    /**
     * Ticks until delivery. Decremented once per server tick.
     * When it reaches zero the resources are added to {@code toAssetId}'s buffer.
     */
    private int remainingTicks;
    public final Data data;

    private LogisticsTask(ID id, Data data, int duration) {
        this.taskId = id;
        this.remainingTicks = duration;
        this.data = data;
    }

    @Desugar
    public record Data(
        /** Asset id of the outpost sending the resources. */
        CelestialAsset.ID fromAssetId,
        /** Asset id of the outpost receiving the resources. */
        CelestialAsset.ID toAssetId,
        /** What is being transported. */
        ItemStackWrapper resourceId,
        /** Number of resource units in this shipment. */
        long amount,
        /**
         * The transport module type that created this task.
         * Used to distinguish HAMMER tasks from BIG_HAMMER tasks in logging/UI.
         */
        TransportType transportKind,
        /** Celestial body id of the departure outpost (for arc rendering). Empty = unknown. */
        CelestialObjectId fromBodyId,
        /** Celestial body id of the destination outpost (for arc rendering). Empty = unknown. */
        CelestialObjectId toBodyId,
        /**
         * Orbital departure time (in orbital simulation units = world ticks × 2.1).
         * Used to anchor the trajectory arc on the client starmap.
         */
        double departureOrbitalTime, /**
                                      * Lambert time-of-flight in orbital simulation units.
                                      * Convert to real seconds via {@code tofOrbitalSeconds / 42.0}.
                                      */
        double tofOrbitalSeconds) {}

    /** Creates a new task with a freshly generated task id. */
    public static LogisticsTask create(CelestialAsset.ID fromAssetId, CelestialAsset.ID toAssetId,
        ItemStackWrapper resourceId, long amount, int deliveryTicks, TransportType transportKind) {

        AutomatedOutpost from = OutpostDataStore.get()
            .getByAssetId(fromAssetId);
        AutomatedOutpost to = OutpostDataStore.get()
            .getByAssetId(toAssetId);
        // These cases *should* never happen, but you never know :^)
        CelestialObjectId fromBody = from != null ? from.celestialObjectId : CelestialObjectId.INVALID;
        CelestialObjectId toBody = to != null ? to.celestialObjectId : CelestialObjectId.INVALID;

        return createWithTrajectory(
            fromAssetId,
            toAssetId,
            resourceId,
            amount,
            deliveryTicks,
            transportKind,
            fromBody,
            toBody,
            0,
            0);
    }

    /** Creates a new task with trajectory metadata for arc rendering. */
    public static LogisticsTask createWithTrajectory(CelestialAsset.ID fromAssetId, CelestialAsset.ID toAssetId,
        ItemStackWrapper resourceId, long amount, int deliveryTicks, TransportType transportKind,
        CelestialObjectId fromBodyId, CelestialObjectId toBodyId, double departureOrbitalTime,
        double tofOrbitalSeconds) {
        return createWithTrajectory(
            ID.create(),
            fromAssetId,
            toAssetId,
            resourceId,
            amount,
            deliveryTicks,
            transportKind,
            fromBodyId,
            toBodyId,
            departureOrbitalTime,
            tofOrbitalSeconds);
    }

    public static LogisticsTask createWithTrajectory(ID id, CelestialAsset.ID fromAssetId, CelestialAsset.ID toAssetId,
        ItemStackWrapper resourceId, long amount, int deliveryTicks, TransportType transportKind,
        CelestialObjectId fromBodyId, CelestialObjectId toBodyId, double departureOrbitalTime,
        double tofOrbitalSeconds) {
        return new LogisticsTask(
            id,
            new Data(
                fromAssetId,
                toAssetId,
                resourceId,
                amount,
                transportKind,
                fromBodyId,
                toBodyId,
                departureOrbitalTime,
                tofOrbitalSeconds),
            deliveryTicks);
    }

    /** Returns a copy of this task with {@code remainingTicks} decremented by one. */
    public LogisticsTask tick() {
        this.remainingTicks -= 1;
        return this;
    }

    public boolean isArrived() {
        return this.remainingTicks <= 0;
    }

    public int getRemainingTicks() {
        return this.remainingTicks;
    }

    @Desugar
    public record ID(UUID id) implements WithUUID {

        public static ID create() {
            return new ID(UUID.randomUUID());
        }

        public static ID from(String value) {
            if (value == null) return null;
            return new ID(UUID.fromString(value));
        }

        public static ID from(UUID value) {
            return value == null ? null : new ID(value);
        }

        public static ID from(ID id) {
            if (id == null) return null;
            return new ID(id.id());
        }

        @Override
        public String toString() {
            return id.toString();
        }
    }
}
