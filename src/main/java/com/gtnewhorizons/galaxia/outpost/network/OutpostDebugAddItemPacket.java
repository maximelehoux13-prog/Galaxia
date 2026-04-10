package com.gtnewhorizons.galaxia.outpost.network;

import net.minecraft.entity.player.EntityPlayerMP;

import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.outpost.AutomatedOutpostState;
import com.gtnewhorizons.galaxia.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.outpost.persistence.OutpostDataStore;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * Client → Server: creative-mode debug packet that adds a specified amount of a
 * resource to the target outpost's buffer.
 *
 * <p>
 * Only accepted from players in creative mode. All other callers are silently rejected.
 */
public final class OutpostDebugAddItemPacket implements IMessage {

    private String assetId;
    private String resourceKey;
    private long amount;

    public OutpostDebugAddItemPacket() {}

    public OutpostDebugAddItemPacket(String assetId, ItemStackWrapper resource, long amount) {
        this.assetId = assetId;
        this.resourceKey = resource.toKey();
        this.amount = amount;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        writeString(buf, assetId);
        writeString(buf, resourceKey);
        buf.writeLong(amount);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        assetId = readString(buf);
        resourceKey = readString(buf);
        amount = buf.readLong();
    }

    public static final class Handler implements IMessageHandler<OutpostDebugAddItemPacket, IMessage> {

        @Override
        public IMessage onMessage(OutpostDebugAddItemPacket packet, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null) return null;

            // SimpleNetworkWrapper guarantees onMessage runs on the main server thread
            // for SERVER-bound packets, so direct mutation is safe.
            String playerName = player.getGameProfile()
                .getName();
            if (!player.capabilities.isCreativeMode) {
                Galaxia.LOG.warn("[Logistics] DebugAddItem rejected: player {} is not in creative mode.", playerName);
                return null;
            }
            if (packet.amount <= 0) {
                Galaxia.LOG.warn(
                    "[Logistics] DebugAddItem rejected: invalid amount {} from player {}",
                    packet.amount,
                    playerName);
                return null;
            }
            long clampedAmount = Math.min(packet.amount, Integer.MAX_VALUE);

            AutomatedOutpostState state = OutpostDataStore.get()
                .getByAssetId(packet.assetId);
            if (state == null) {
                Galaxia.LOG
                    .warn("[Logistics] DebugAddItem: unknown assetId {} from player {}", packet.assetId, playerName);
                return null;
            }

            ItemStackWrapper resource = ItemStackWrapper.fromKey(packet.resourceKey);
            state.inventory.add(resource, clampedAmount);
            Galaxia.LOG.info(
                "[Logistics] DEBUG: added {} x {} to outpost {} (by {})",
                clampedAmount,
                resource,
                packet.assetId,
                playerName);
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
