package com.gtnewhorizons.galaxia.outpost.logistics;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.galaxia.outpost.ItemStackWrapper;

/**
 * An in-flight logistics shipment between two outposts.
 *
 * <p>Tasks are created by {@link OutpostLogisticsEngine} when a supply signal is matched
 * to a request signal. They count down via {@link #remainingTicks} and deliver resources
 * to the destination buffer on arrival (when {@code remainingTicks} reaches zero).
 *
 * <p>Tasks are persisted to JSON so that in-flight shipments survive world restarts.
 * The task state is the source of truth for "items in transit" – resources are consumed
 * from the source buffer immediately on task creation and added to the destination on arrival.
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
    /** Number of resource units in this shipment (max 64 for HAMMER tasks). */
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
    String transportKind) {

    /** Creates a new task with a freshly generated task id. */
    public static LogisticsTask create(String fromAssetId, String toAssetId, ItemStackWrapper resourceId,
        long amount, int deliveryTicks, String transportKind) {
        String taskId = "task_" + java.util.UUID.randomUUID()
            .toString()
            .replace("-", "")
            .substring(0, 12);
        return new LogisticsTask(taskId, fromAssetId, toAssetId, resourceId, amount, deliveryTicks, transportKind);
    }

    /** Returns a copy of this task with {@code remainingTicks} decremented by one. */
    public LogisticsTask tick() {
        return new LogisticsTask(taskId, fromAssetId, toAssetId, resourceId, amount, remainingTicks - 1, transportKind);
    }

    public boolean isArrived() {
        return remainingTicks <= 0;
    }
}
