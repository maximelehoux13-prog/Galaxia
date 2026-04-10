package com.gtnewhorizons.galaxia.outpost.network;

import java.nio.charset.StandardCharsets;

import io.netty.buffer.ByteBuf;

/** Shared length-prefixed UTF-8 string helpers for outpost network packets. */
final class PacketUtil {

    private PacketUtil() {}

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
}
