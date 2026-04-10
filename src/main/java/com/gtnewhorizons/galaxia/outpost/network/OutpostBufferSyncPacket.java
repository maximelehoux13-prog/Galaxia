package com.gtnewhorizons.galaxia.outpost.network;

import java.util.LinkedHashMap;
import java.util.Map;

import com.gtnewhorizons.galaxia.outpost.AutomatedOutpostState;
import com.gtnewhorizons.galaxia.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.outpost.persistence.OutpostDataStore;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

/**
 * Server → Client: synchronizes the buffer contents of a single outpost.
 *
 * <p>
 * Sent whenever the GUI for an outpost is opened, and on each server-side
 * buffer mutation while the GUI is open (future streaming update).
 */
public final class OutpostBufferSyncPacket implements IMessage {

    private String assetId;
    private Map<String, Long> buffer;

    public OutpostBufferSyncPacket() {}

    /** Builds a sync packet from the current server-side buffer of the given outpost. */
    public OutpostBufferSyncPacket(AutomatedOutpostState state) {
        this.assetId = state.assetId;
        this.buffer = new LinkedHashMap<>();
        for (Map.Entry<ItemStackWrapper, Long> e : state.inventory.snapshot()
            .entrySet()) {
            buffer.put(
                e.getKey()
                    .toKey(),
                e.getValue());
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        writeString(buf, assetId);
        buf.writeInt(buffer.size());
        for (Map.Entry<String, Long> e : buffer.entrySet()) {
            writeString(buf, e.getKey());
            buf.writeLong(e.getValue());
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        assetId = readString(buf);
        int count = buf.readInt();
        buffer = new LinkedHashMap<>(count);
        for (int i = 0; i < count; i++) {
            String key = readString(buf);
            long amount = buf.readLong();
            buffer.put(key, amount);
        }
    }

    public static final class Handler implements IMessageHandler<OutpostBufferSyncPacket, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(OutpostBufferSyncPacket packet, MessageContext ctx) {
            // Client-side packet handlers run on the Netty I/O thread.
            // Buffer sync is a read-only replacement of a client-side mirror, safe to execute inline.
            AutomatedOutpostState state = OutpostDataStore.get()
                .getByAssetId(packet.assetId);
            if (state == null) return null;
            Map<ItemStackWrapper, Long> snapshot = new LinkedHashMap<>();
            for (Map.Entry<String, Long> e : packet.buffer.entrySet()) {
                snapshot.put(ItemStackWrapper.fromKey(e.getKey()), e.getValue());
            }
            state.inventory.loadFromSnapshot(snapshot);
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // String encoding helpers (length-prefixed UTF-8)
    // -------------------------------------------------------------------------

    private static void writeString(ByteBuf buf, String s) {
        byte[] bytes = s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        buf.writeShort(bytes.length);
        buf.writeBytes(bytes);
    }

    private static String readString(ByteBuf buf) {
        int len = buf.readUnsignedShort();
        byte[] bytes = new byte[len];
        buf.readBytes(bytes);
        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }
}
