package com.gtnewhorizons.galaxia.outpost.network;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.gtnewhorizons.galaxia.outpost.logistics.LogisticsSignal;
import com.gtnewhorizons.galaxia.outpost.logistics.LogisticsSignalScope;
import com.gtnewhorizons.galaxia.outpost.logistics.LogisticsSignalStore;
import com.gtnewhorizons.galaxia.outpost.persistence.OutpostDataStore;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

/**
 * Server → Client: sends aggregated logistics signal totals, keyed by scope.
 *
 * <p>Sent every 20 ticks alongside {@link OutpostFullSyncPacket}. The client stores
 * the result in {@link OutpostDataStore} so the Signals overlay can display
 * exactly what the server's signal model contains, without re-deriving it locally.
 *
 * <p>Two aggregation levels are included:
 * <ul>
 *   <li><b>bySystem</b> – keyed by star (system) id; used by the SYSTEM-scope Signals view.</li>
 *   <li><b>byPlanet</b> – keyed by planetary anchor body id; used by the PLANETARY-scope view.</li>
 * </ul>
 *
 * <p>Values are signed net amounts (positive = net surplus, negative = net deficit)
 * summed across all outposts in that scope bucket for each resource.
 */
public final class LogisticsSignalsSyncPacket implements IMessage {

    /** systemId → resourceKey → net signed amount */
    private Map<String, Map<String, Long>> bySystem;
    /** planetaryAnchorBodyId → resourceKey → net signed amount */
    private Map<String, Map<String, Long>> byPlanet;

    public LogisticsSignalsSyncPacket() {}

    /**
     * Constructs a snapshot from the live {@link LogisticsSignalStore}.
     * All signals live in SYSTEM scope; byPlanet is derived from each signal's
     * {@code planetaryAnchorBodyId} field.
     */
    public LogisticsSignalsSyncPacket(LogisticsSignalStore store) {
        bySystem = new LinkedHashMap<>();
        byPlanet = new LinkedHashMap<>();

        for (Map.Entry<String, List<LogisticsSignal>> entry : store
            .allSignalsForScope(LogisticsSignalScope.SYSTEM).entrySet()) {
            String systemId = entry.getKey();
            Map<String, Long> systemAgg = new LinkedHashMap<>();
            for (LogisticsSignal sig : entry.getValue()) {
                String key = sig.resourceId().toKey();
                systemAgg.merge(key, sig.amount(), Long::sum);
                // Build planet-level aggregate in parallel
                String anchorId = sig.planetaryAnchorBodyId();
                if (anchorId != null && !anchorId.isEmpty()) {
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
            OutpostDataStore.get().updateClientSignals(packet.bySystem, packet.byPlanet);
            return null;
        }
    }

    // ── Serialization helpers ────────────────────────────────────────────────

    private static void writeAggMap(ByteBuf buf, Map<String, Map<String, Long>> map) {
        buf.writeInt(map.size());
        for (Map.Entry<String, Map<String, Long>> outer : map.entrySet()) {
            writeString(buf, outer.getKey());
            Map<String, Long> inner = outer.getValue();
            buf.writeInt(inner.size());
            for (Map.Entry<String, Long> e : inner.entrySet()) {
                writeString(buf, e.getKey());
                buf.writeLong(e.getValue());
            }
        }
    }

    private static Map<String, Map<String, Long>> readAggMap(ByteBuf buf) {
        int outerCount = buf.readInt();
        Map<String, Map<String, Long>> map = new LinkedHashMap<>(outerCount);
        for (int i = 0; i < outerCount; i++) {
            String outerKey = readString(buf);
            int innerCount = buf.readInt();
            Map<String, Long> inner = new LinkedHashMap<>(innerCount);
            for (int j = 0; j < innerCount; j++) {
                String resourceKey = readString(buf);
                long net = buf.readLong();
                inner.put(resourceKey, net);
            }
            map.put(outerKey, inner);
        }
        return map;
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
