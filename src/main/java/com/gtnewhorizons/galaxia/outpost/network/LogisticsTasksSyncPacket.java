package com.gtnewhorizons.galaxia.outpost.network;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.galaxia.outpost.AutomatedOutpost;
import com.gtnewhorizons.galaxia.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.outpost.logistics.LogisticsTask;
import com.gtnewhorizons.galaxia.outpost.persistence.OutpostDataStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

/**
 * Server → Client: sends the complete list of in-flight logistics tasks.
 *
 * <p>
 * Sent alongside {@link OutpostFullSyncPacket} every 20 ticks. The client stores the
 * result in {@link OutpostDataStore#clientTasks()} so the Signals overlay can show
 * per-item in-transfer counts and the starmap can render active logistics arcs.
 *
 * <p>
 * Body IDs are resolved server-side: if a task has empty {@code fromBodyId}/
 * {@code toBodyId} (same-body instant transfer), the server looks up the outpost's
 * {@code celestialBodyId} before serializing. Tasks that cannot be resolved are skipped.
 */
public final class LogisticsTasksSyncPacket implements IMessage {

    private List<TaskEntry> tasks;

    public LogisticsTasksSyncPacket() {}

    public LogisticsTasksSyncPacket(List<LogisticsTask> activeTasks) {
        this.tasks = new ArrayList<>(activeTasks.size());
        for (LogisticsTask t : activeTasks) {
            if (t.data.resourceId() == null) continue;

            // Resolve empty body IDs from the outpost store (same-body instant tasks)
            AutomatedOutpost from = OutpostDataStore.get()
                .getByAssetId(t.data.fromAssetId());
            CelestialObjectId fromBodyId = from.celestialBodyId;
            AutomatedOutpost to = OutpostDataStore.get()
                .getByAssetId(t.data.toAssetId());
            CelestialObjectId toBodyId = to.celestialBodyId;

            tasks.add(
                new TaskEntry(
                    t.taskId,
                    t.data.resourceId()
                        .toKey(),
                    t.data.amount(),
                    t.data.transportKind(),
                    fromBodyId,
                    toBodyId,
                    t.data.departureOrbitalTime(),
                    t.data.tofOrbitalSeconds()));
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(tasks.size());
        for (TaskEntry e : tasks) {
            PacketUtil.writeString(buf, e.taskId.toString());
            PacketUtil.writeString(buf, e.resourceKey);
            buf.writeLong(e.amount);
            PacketUtil.writeEnum(buf, e.transportKind);
            PacketUtil.writeCelestialObjectId(buf, e.fromBodyId);
            PacketUtil.writeCelestialObjectId(buf, e.toBodyId);
            buf.writeDouble(e.departureOrbitalTime);
            buf.writeDouble(e.tofOrbitalSeconds);
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int count = buf.readInt();
        tasks = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            LogisticsTask.ID taskId = LogisticsTask.ID.from(PacketUtil.readString(buf));
            String resourceKey = PacketUtil.readString(buf);
            long amount = buf.readLong();
            LogisticsTask.TransportType transportKind = PacketUtil.readEnum(buf, LogisticsTask.TransportType.class);
            CelestialObjectId fromBodyId = PacketUtil.readCelestialObjectId(buf);
            CelestialObjectId toBodyId = PacketUtil.readCelestialObjectId(buf);
            double departureOrbitalTime = buf.readDouble();
            double tofOrbitalSeconds = buf.readDouble();
            tasks.add(
                new TaskEntry(
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
            Minecraft.getMinecraft()
                .func_152344_a(() -> {
                    List<OutpostDataStore.ClientLogisticsTask> clientTasks = new ArrayList<>(packet.tasks.size());
                    for (TaskEntry e : packet.tasks) {
                        ItemStackWrapper resource = ItemStackWrapper.fromKey(e.resourceKey);
                        if (resource == null) continue;
                        clientTasks.add(
                            new OutpostDataStore.ClientLogisticsTask(
                                e.taskId,
                                resource,
                                e.amount,
                                e.transportKind,
                                e.fromBodyId,
                                e.toBodyId,
                                e.departureOrbitalTime,
                                e.tofOrbitalSeconds));
                    }
                    OutpostDataStore.get()
                        .updateClientTasks(clientTasks);
                });
            return null;
        }
    }

    @Desugar
    private record TaskEntry(LogisticsTask.ID taskId, String resourceKey, long amount,
        LogisticsTask.TransportType transportKind, CelestialObjectId fromBodyId, CelestialObjectId toBodyId,
        double departureOrbitalTime, double tofOrbitalSeconds) {

    }
}
