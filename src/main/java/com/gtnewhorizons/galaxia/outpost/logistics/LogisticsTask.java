package com.gtnewhorizons.galaxia.outpost.logistics;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.galaxia.outpost.ItemStackWrapper;

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
@Desugar
public record LogisticsTask(
    /** Unique task id (UUID string). */
    String taskId,
    /** Asset id of the outpost sending the resources. */
    String fromAssetId,
    /** Asset id of the outpost receiving the resources. */
    String toAssetId,
    /** What is being transported. */
    ItemStackWrapper resourceId,
    /** Number of resource units in this shipment. */
    long amount,
    /**
     * Ticks until delivery. Decremented once per server tick.
     * When it reaches zero the resources are added to {@code toAssetId}'s buffer.
     */
    int remainingTicks,
    /**
     * The transport module type that created this task.
     * Used to distinguish HAMMER tasks from BIG_HAMMER tasks in logging/UI.
     */
    String transportKind,
    /** Celestial body id of the departure outpost (for arc rendering). Empty = unknown. */
    String fromBodyId,
    /** Celestial body id of the destination outpost (for arc rendering). Empty = unknown. */
    String toBodyId,
    /**
     * Orbital departure time (in orbital simulation units = world ticks × 2.1).
     * Used to anchor the trajectory arc on the client starmap.
     */
    double departureOrbitalTime,
    /**
     * Lambert time-of-flight in orbital simulation units.
     * Convert to real seconds via {@code tofOrbitalSeconds / 42.0}.
     */
    double tofOrbitalSeconds) {

    /** Creates a new task with a freshly generated task id. */
    public static LogisticsTask create(String fromAssetId, String toAssetId, ItemStackWrapper resourceId, long amount,
        int deliveryTicks, String transportKind) {
        String taskId = makeId();
        return new LogisticsTask(
            taskId,
            fromAssetId,
            toAssetId,
            resourceId,
            amount,
            deliveryTicks,
            transportKind,
            "",
            "",
            0.0,
            0.0);
    }

    /** Creates a new task with trajectory metadata for arc rendering. */
    public static LogisticsTask createWithTrajectory(String fromAssetId, String toAssetId, ItemStackWrapper resourceId,
        long amount, int deliveryTicks, String transportKind, String fromBodyId, String toBodyId,
        double departureOrbitalTime, double tofOrbitalSeconds) {
        return new LogisticsTask(
            makeId(),
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

    private static String makeId() {
        return "task_" + java.util.UUID.randomUUID()
            .toString()
            .replace("-", "")
            .substring(0, 12);
    }

    /** Returns a copy of this task with {@code remainingTicks} decremented by one. */
    public LogisticsTask tick() {
        return new LogisticsTask(
            taskId,
            fromAssetId,
            toAssetId,
            resourceId,
            amount,
            remainingTicks - 1,
            transportKind,
            fromBodyId,
            toBodyId,
            departureOrbitalTime,
            tofOrbitalSeconds);
    }

    public boolean isArrived() {
        return remainingTicks <= 0;
    }
}
