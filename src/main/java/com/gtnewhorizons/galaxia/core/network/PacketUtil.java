package com.gtnewhorizons.galaxia.core.network;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import com.gtnewhorizons.galaxia.registry.outpost.logistics.LogisticsTask;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.interfaces.WithUUID;

import io.netty.buffer.ByteBuf;

/** Shared serialization helpers for outpost network packets. */
final class PacketUtil {

    private PacketUtil() {}

    // ── String helpers ─────────────────────────────────────────────────────

    static void writeString(ByteBuf buf, String s) {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        buf.writeShort(bytes.length);
        buf.writeBytes(bytes);
    }

    static String readString(ByteBuf buf) {
        int len = buf.readUnsignedShort();
        byte[] bytes = new byte[len];
        buf.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    // ── ID helpers ──────────────────────────────────────────────────

    static <T extends WithUUID> void writeId(ByteBuf buf, T with) {
        UUID uuid = with.id();
        buf.writeLong(uuid.getMostSignificantBits());
        buf.writeLong(uuid.getLeastSignificantBits());
    }

    static UUID readId(ByteBuf buf) {
        long mostSig = buf.readLong();
        long leastSig = buf.readLong();
        return new UUID(mostSig, leastSig);
    }

    static CelestialAsset.ID readAssetId(ByteBuf buf) {
        return new CelestialAsset.ID(readId(buf));
    }

    public static LogisticsTask.ID readTaskId(ByteBuf buf) {
        return new LogisticsTask.ID(readId(buf));
    }

    static void writeCelestialObjectId(ByteBuf buf, CelestialObjectId id) {
        buf.writeByte(id.ordinal());
    }

    static CelestialObjectId readCelestialObjectId(ByteBuf buf) {
        int ordinal = buf.readUnsignedByte();
        CelestialObjectId[] values = CelestialObjectId.values();
        return ordinal < values.length ? values[ordinal] : CelestialObjectId.INVALID;
    }

    // ── Enum helpers ───────────────────────────────────────────────────────

    static <T extends Enum<T>> void writeEnum(ByteBuf buf, T enumValue) {
        buf.writeByte(enumValue.ordinal());
    }

    @SuppressWarnings("unchecked")
    static <T extends Enum<T>> T readEnum(ByteBuf buf, Class<T> enumClass) {
        int ordinal = buf.readUnsignedByte();
        T[] values = enumClass.getEnumConstants();
        return ordinal < values.length ? values[ordinal] : values[0];
    }

}
