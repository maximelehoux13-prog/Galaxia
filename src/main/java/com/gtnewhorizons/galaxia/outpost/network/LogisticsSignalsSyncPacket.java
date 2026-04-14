package com.gtnewhorizons.galaxia.outpost.network;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;

import org.jetbrains.annotations.UnknownNullability;

import com.gtnewhorizons.galaxia.outpost.logistics.LogisticsSignal;
import com.gtnewhorizons.galaxia.outpost.logistics.LogisticsSignalStore;
import com.gtnewhorizons.galaxia.outpost.persistence.OutpostDataStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

/**
 * Server → Client: sends aggregated logistics signal totals, keyed by scope.
 *
 * <p>
 * Sent every 20 ticks alongside {@link OutpostFullSyncPacket}. The client stores
 * the result in {@link OutpostDataStore} so the Signals overlay can display
 * exactly what the server's signal model contains, without re-deriving it locally.
 *
 * <p>
 * Two aggregation levels are included:
 * <ul>
 * <li><b>bySystem</b> – keyed by star (system) id; used by the SYSTEM-scope Signals view.</li>
 * <li><b>byPlanet</b> – keyed by planetary anchor body id; used by the PLANETARY-scope view.</li>
 * </ul>
 *
 * <p>
 * Values are signed net amounts (positive = net surplus, negative = net deficit)
 * summed across all outposts in that scope bucket for each resource.
 */
public final class LogisticsSignalsSyncPacket implements IMessage {

    /** systemId → resourceKey → net signed amount */
    private Map<CelestialObjectId, Map<String, Long>> bySystem;
    /** planetaryAnchorBodyId → resourceKey → net signed amount */
    private Map<CelestialObjectId, Map<String, Long>> byPlanet;

    public LogisticsSignalsSyncPacket() {}

    /**
     * Constructs a snapshot from the live {@link LogisticsSignalStore}.
     * All signals live in SYSTEM scope; byPlanet is derived from each signal's
     * {@code planetaryAnchorBodyId} field.
     */
    public LogisticsSignalsSyncPacket(LogisticsSignalStore store) {
        bySystem = new LinkedHashMap<>();
        byPlanet = new LinkedHashMap<>();

        for (Map.Entry<CelestialObjectId, List<LogisticsSignal>> entry : store
            .allSignalsForScope(LogisticsSignal.Scope.SYSTEM)
            .entrySet()) {
            CelestialObjectId systemId = entry.getKey();
            Map<String, Long> systemAgg = new LinkedHashMap<>();
            for (LogisticsSignal sig : entry.getValue()) {
                String key = sig.resourceId()
                    .toKey();
                systemAgg.merge(key, sig.amount(), Long::sum);
                // Build planet-level aggregate in parallel
                CelestialObjectId anchorId = sig.planetaryAnchorBodyId();
                if (anchorId != null) {
                    byPlanet.computeIfAbsent(anchorId, k -> new LinkedHashMap<>())
                        .merge(key, sig.amount(), Long::sum);
                }
            }
            if (!systemAgg.isEmpty()) bySystem.put(systemId, systemAgg);
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        writeAggMap(buf, bySystem);
        writeAggMap(buf, byPlanet);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        bySystem = readAggMap(buf);
        byPlanet = readAggMap(buf);
    }

    public static final class Handler implements IMessageHandler<LogisticsSignalsSyncPacket, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(LogisticsSignalsSyncPacket packet, MessageContext ctx) {
            Minecraft.getMinecraft()
                .func_152344_a(
                    () -> OutpostDataStore.get()
                        .updateClientSignals(packet.bySystem, packet.byPlanet));
            return null;
        }
    }

    // ── Serialization helpers ────────────────────────────────────────────────

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
