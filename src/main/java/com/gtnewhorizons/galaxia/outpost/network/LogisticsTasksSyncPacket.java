package com.gtnewhorizons.galaxia.outpost.network;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.gtnewhorizons.galaxia.outpost.AutomatedOutpostState;
import com.gtnewhorizons.galaxia.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.outpost.logistics.LogisticsTask;
import com.gtnewhorizons.galaxia.outpost.persistence.OutpostDataStore;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

/**
 * Server → Client: sends the complete list of in-flight logistics tasks.
 *
 * <p>Sent alongside {@link OutpostFullSyncPacket} every 20 ticks. The client stores the
 * result in {@link OutpostDataStore#clientTasks()} so the Signals overlay can show
 * per-item in-transfer counts and the starmap can render active logistics arcs.
 *
 * <p>Body IDs are resolved server-side: if a task has empty {@code fromBodyId}/
 * {@code toBodyId} (same-body instant transfer), the server looks up the outpost's
 * {@code celestialBodyId} before serializing. Tasks that cannot be resolved are skipped.
 */
public final class LogisticsTasksSyncPacket implements IMessage {

    private List<TaskEntry> tasks;

    public LogisticsTasksSyncPacket() {}

    public LogisticsTasksSyncPacket(List<LogisticsTask> activeTasks) {
        this.tasks = new ArrayList<>(activeTasks.size());
        for (LogisticsTask t : activeTasks) {
            if (t.resourceId() == null) continue;

            String fromBodyId = t.fromBodyId();
            String toBodyId = t.toBodyId();

            // Resolve empty body IDs from the outpost store (same-body instant tasks)
            if (fromBodyId.isEmpty()) {
                AutomatedOutpostState from = OutpostDataStore.get().getByAssetId(t.fromAssetId());
                fromBodyId = from != null ? from.celestialBodyId : "";
            }
            if (toBodyId.isEmpty()) {
                AutomatedOutpostState to = OutpostDataStore.get().getByAssetId(t.toAssetId());
                toBodyId = to != null ? to.celestialBodyId : "";
            }
            if (fromBodyId.isEmpty() || toBodyId.isEmpty()) continue;

            tasks.add(new TaskEntry(
                t.taskId(),
                t.resourceId().toKey(),
                t.amount(),
                t.transportKind(),
                fromBodyId,
                toBodyId,
                t.departureOrbitalTime(),
                t.tofOrbitalSeconds()));
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(tasks.size());
        for (TaskEntry e : tasks) {
            writeString(buf, e.taskId);
            writeString(buf, e.resourceKey);
            buf.writeLong(e.amount);
            writeString(buf, e.transportKind);
            writeString(buf, e.fromBodyId);
            writeString(buf, e.toBodyId);
            buf.writeDouble(e.departureOrbitalTime);
            buf.writeDouble(e.tofOrbitalSeconds);
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int count = buf.readInt();
        tasks = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String taskId = readString(buf);
            String resourceKey = readString(buf);
            long amount = buf.readLong();
            String transportKind = readString(buf);
            String fromBodyId = readString(buf);
            String toBodyId = readString(buf);
            double departureOrbitalTime = buf.readDouble();
            double tofOrbitalSeconds = buf.readDouble();
            tasks.add(new TaskEntry(
                taskId,
                resourceKey,
                amount,
                transportKind,
                fromBodyId,
                toBodyId,
                departureOrbitalTime,
                tofOrbitalSeconds));
        }
    }

    public static final class Handler implements IMessageHandler<LogisticsTasksSyncPacket, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(LogisticsTasksSyncPacket packet, MessageContext ctx) {
            List<OutpostDataStore.ClientLogisticsTask> clientTasks = new ArrayList<>(packet.tasks.size());
            for (TaskEntry e : packet.tasks) {
                ItemStackWrapper resource = ItemStackWrapper.fromKey(e.resourceKey);
                if (resource == null) continue;
                clientTasks.add(new OutpostDataStore.ClientLogisticsTask(
                    e.taskId,
                    resource,
                    e.amount,
                    e.transportKind,
                    e.fromBodyId,
                    e.toBodyId,
                    e.departureOrbitalTime,
                    e.tofOrbitalSeconds));
            }
            OutpostDataStore.get().updateClientTasks(clientTasks);
            return null;
        }
    }

    private static final class TaskEntry {

        final String taskId;
        final String resourceKey;
        final long amount;
        final String transportKind;
        final String fromBodyId;
        final String toBodyId;
        final double departureOrbitalTime;
        final double tofOrbitalSeconds;

        TaskEntry(String taskId, String resourceKey, long amount, String transportKind, String fromBodyId,
            String toBodyId, double departureOrbitalTime, double tofOrbitalSeconds) {
            this.taskId = taskId;
            this.resourceKey = resourceKey;
            this.amount = amount;
            this.transportKind = transportKind;
            this.fromBodyId = fromBodyId;
            this.toBodyId = toBodyId;
            this.departureOrbitalTime = departureOrbitalTime;
            this.tofOrbitalSeconds = tofOrbitalSeconds;
        }
    }

    private static void writeString(ByteBuf buf, String s) {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        buf.writeShort(bytes.length);
        buf.writeBytes(bytes);
    }

    private static String readString(ByteBuf buf) {
        int len = buf.readUnsignedShort();
        byte[] bytes = new byte[len];
        buf.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
