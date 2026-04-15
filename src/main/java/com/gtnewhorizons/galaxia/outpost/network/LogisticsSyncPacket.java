package com.gtnewhorizons.galaxia.outpost.network;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;

import org.jetbrains.annotations.UnknownNullability;

import com.gtnewhorizons.galaxia.outpost.AutomatedOutpost;
import com.gtnewhorizons.galaxia.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.outpost.logistics.LogisticsSignal;
import com.gtnewhorizons.galaxia.outpost.logistics.LogisticsSignalStore;
import com.gtnewhorizons.galaxia.outpost.logistics.LogisticsTask;
import com.gtnewhorizons.galaxia.outpost.persistence.OutpostDataStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

public final class LogisticsSyncPacket implements IMessage {

    private List<LogisticsTask.Data> tasks;
    private Map<CelestialObjectId, Map<String, Long>> bySystem;
    private Map<CelestialObjectId, Map<String, Long>> byPlanet;

    public LogisticsSyncPacket() {}

    public static LogisticsSyncPacket from(LogisticsSignalStore store, List<LogisticsTask> activeTasks) {
        LogisticsSyncPacket pkt = new LogisticsSyncPacket();

        pkt.tasks = new java.util.ArrayList<>(activeTasks.size());
        for (LogisticsTask t : activeTasks) {
            if (t.data.resourceId() == null) continue;
            pkt.tasks.add(t.data);
        }

        pkt.bySystem = new LinkedHashMap<>();
        pkt.byPlanet = new LinkedHashMap<>();

        for (Map.Entry<CelestialObjectId, List<LogisticsSignal>> entry : store
            .allSignalsForScope(LogisticsSignal.Scope.SYSTEM)
            .entrySet()) {
            CelestialObjectId systemId = entry.getKey();
            Map<String, Long> systemAgg = new LinkedHashMap<>();
            for (LogisticsSignal sig : entry.getValue()) {
                String key = sig.resourceId().toKey();
                systemAgg.merge(key, sig.amount(), Long::sum);
                CelestialObjectId anchorId = sig.planetaryAnchorBodyId();
                if (anchorId != null) {
                    pkt.byPlanet.computeIfAbsent(anchorId, k -> new LinkedHashMap<>()).merge(key, sig.amount(), Long::sum);
                }
            }
            if (!systemAgg.isEmpty()) pkt.bySystem.put(systemId, systemAgg);
        }

        return pkt;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(tasks.size());
        for (LogisticsTask.Data d : tasks) {
            PacketUtil.writeUUID(buf, d.fromAssetId().id());
            PacketUtil.writeUUID(buf, d.toAssetId().id());
            PacketUtil.writeString(buf, d.resourceId().toKey());
            buf.writeLong(d.amount());
            PacketUtil.writeEnum(buf, d.transportKind());
            PacketUtil.writeCelestialObjectId(buf, d.fromBodyId());
            PacketUtil.writeCelestialObjectId(buf, d.toBodyId());
            buf.writeDouble(d.departureOrbitalTime());
            buf.writeDouble(d.tofOrbitalSeconds());
        }

        writeAggMap(buf, bySystem);
        writeAggMap(buf, byPlanet);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int taskCount = buf.readInt();
        tasks = new java.util.ArrayList<>(taskCount);
        for (int i = 0; i < taskCount; i++) {
            CelestialAsset.ID fromAssetId = new CelestialAsset.ID(PacketUtil.readUUID(buf));
            CelestialAsset.ID toAssetId = new CelestialAsset.ID(PacketUtil.readUUID(buf));
            String resourceKey = PacketUtil.readString(buf);
            long amount = buf.readLong();
            LogisticsTask.TransportType transportKind = PacketUtil.readEnum(buf, LogisticsTask.TransportType.class);
            CelestialObjectId fromBodyId = PacketUtil.readCelestialObjectId(buf);
            CelestialObjectId toBodyId = PacketUtil.readCelestialObjectId(buf);
            double departureOrbitalTime = buf.readDouble();
            double tofOrbitalSeconds = buf.readDouble();
            tasks.add(new LogisticsTask.Data(
                fromAssetId,
                toAssetId,
                ItemStackWrapper.fromKey(resourceKey),
                amount,
                transportKind,
                fromBodyId,
                toBodyId,
                departureOrbitalTime,
                tofOrbitalSeconds));
        }

        bySystem = readAggMap(buf);
        byPlanet = readAggMap(buf);
    }

    public static final class Handler implements IMessageHandler<LogisticsSyncPacket, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(LogisticsSyncPacket packet, MessageContext ctx) {
            Minecraft.getMinecraft().func_152344_a(() -> {
                List<OutpostDataStore.ClientLogisticsTask> clientTasks = new java.util.ArrayList<>(packet.tasks.size());
                for (LogisticsTask.Data d : packet.tasks) {
                    ItemStackWrapper resource = d.resourceId();
                    if (resource == null) continue;
                    clientTasks.add(new OutpostDataStore.ClientLogisticsTask(
                        LogisticsTask.ID.create(),
                        resource,
                        d.amount(),
                        d.transportKind(),
                        d.fromBodyId(),
                        d.toBodyId(),
                        d.departureOrbitalTime(),
                        d.tofOrbitalSeconds()));
                }
                OutpostDataStore store = OutpostDataStore.get();
                store.updateClientTasks(clientTasks);
                store.updateClientSignals(packet.bySystem, packet.byPlanet);
            });
            return null;
        }
    }

    private static void writeAggMap(ByteBuf buf, @UnknownNullability Map<CelestialObjectId, Map<String, Long>> map) {
        buf.writeInt(map.size());
        for (Map.Entry<CelestialObjectId, Map<String, Long>> outer : map.entrySet()) {
            PacketUtil.writeCelestialObjectId(buf, outer.getKey());
            Map<String, Long> inner = outer.getValue();
            buf.writeInt(inner.size());
            for (Map.Entry<String, Long> e : inner.entrySet()) {
                PacketUtil.writeString(buf, e.getKey());
                buf.writeLong(e.getValue());
            }
        }
    }

    private static Map<CelestialObjectId, Map<String, Long>> readAggMap(ByteBuf buf) {
        int outerCount = buf.readInt();
        Map<CelestialObjectId, Map<String, Long>> map = new LinkedHashMap<>(outerCount);
        for (int i = 0; i < outerCount; i++) {
            CelestialObjectId outerKey = PacketUtil.readCelestialObjectId(buf);
            int innerCount = buf.readInt();
            Map<String, Long> inner = new LinkedHashMap<>(innerCount);
            for (int j = 0; j < innerCount; j++) {
                String resourceKey = PacketUtil.readString(buf);
                long net = buf.readLong();
                inner.put(resourceKey, net);
            }
            map.put(outerKey, inner);
        }
        return map;
    }
}
