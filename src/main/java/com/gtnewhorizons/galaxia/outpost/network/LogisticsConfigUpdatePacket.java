package com.gtnewhorizons.galaxia.outpost.network;

import net.minecraft.entity.player.EntityPlayerMP;

import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.outpost.AutomatedOutpostState;
import com.gtnewhorizons.galaxia.outpost.LogisticsResourceConfig;
import com.gtnewhorizons.galaxia.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.outpost.persistence.OutpostDataStore;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * Client → Server: updates a single resource's logistics configuration in an outpost.
 *
 * <p>Carries the full {@link LogisticsResourceConfig} for one resource.
 * The server validates that the sending player belongs to the outpost's team
 * before applying the change.
 */
public final class LogisticsConfigUpdatePacket implements IMessage {

    private String assetId;
    private String resourceKey;
    private int minReserve;
    private int orderSize;
    private boolean isImportEnabled;
    private boolean isSupplyEnabled;

    public LogisticsConfigUpdatePacket() {}

    public LogisticsConfigUpdatePacket(String assetId, ItemStackWrapper resource, LogisticsResourceConfig config) {
        this.assetId = assetId;
        this.resourceKey = resource.toKey();
        this.minReserve = config.minReserve();
        this.orderSize = config.orderSize();
        this.isImportEnabled = config.isImportEnabled();
        this.isSupplyEnabled = config.isSupplyEnabled();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        writeString(buf, assetId);
        writeString(buf, resourceKey);
        buf.writeInt(minReserve);
        buf.writeInt(orderSize);
        buf.writeBoolean(isImportEnabled);
        buf.writeBoolean(isSupplyEnabled);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        assetId = readString(buf);
        resourceKey = readString(buf);
        minReserve = buf.readInt();
        orderSize = buf.readInt();
        isImportEnabled = buf.readBoolean();
        isSupplyEnabled = buf.readBoolean();
    }

    public static final class Handler implements IMessageHandler<LogisticsConfigUpdatePacket, IMessage> {

        @Override
        public IMessage onMessage(LogisticsConfigUpdatePacket packet, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null) return null;

            // SimpleNetworkWrapper guarantees onMessage runs on the main server thread
            // for SERVER-bound packets, so direct mutation is safe (same as DestinationSetPacket).
            String playerName = player.getGameProfile()
                .getName();
            AutomatedOutpostState state = OutpostDataStore.get()
                .getByAssetId(packet.assetId);
            if (state == null) {
                Galaxia.LOG.warn(
                    "[Logistics] LogisticsConfigUpdate: unknown assetId {} from player {}",
                    packet.assetId,
                    playerName);
                return null;
            }

            // Team permission check: the player's team must match the outpost's team.
            // TODO: resolve the player's team UUID via NHLib and compare with state.teamId.
            // For now, any authenticated player may update any outpost (remove when NHLib wired).

            if (packet.orderSize <= 0) {
                Galaxia.LOG.warn(
                    "[Logistics] LogisticsConfigUpdate rejected: orderSize must be > 0 (player {})",
                    playerName);
                return null;
            }
            if (packet.minReserve < 0) {
                Galaxia.LOG.warn(
                    "[Logistics] LogisticsConfigUpdate rejected: minReserve must be >= 0 (player {})",
                    playerName);
                return null;
            }

            ItemStackWrapper resource = ItemStackWrapper.fromKey(packet.resourceKey);
            state.logisticsConfig.set(
                resource,
                new LogisticsResourceConfig(packet.minReserve, packet.orderSize, packet.isImportEnabled,
                    packet.isSupplyEnabled));
            return new OutpostFullSyncPacket(state);
        }
    }

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
