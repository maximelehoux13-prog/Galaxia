package com.gtnewhorizons.galaxia.outpost.network;

import net.minecraft.entity.player.EntityPlayerMP;

import com.gtnewhorizons.galaxia.outpost.AutomatedOutpostState;
import com.gtnewhorizons.galaxia.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.outpost.persistence.OutpostDataStore;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public final class OutpostInventoryRemovePacket implements IMessage {

    private String assetId;
    private String resourceKey;

    public OutpostInventoryRemovePacket() {}

    public OutpostInventoryRemovePacket(String assetId, ItemStackWrapper resource) {
        this.assetId = assetId;
        this.resourceKey = resource.toKey();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        writeString(buf, assetId);
        writeString(buf, resourceKey);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        assetId = readString(buf);
        resourceKey = readString(buf);
    }

    public static final class Handler implements IMessageHandler<OutpostInventoryRemovePacket, IMessage> {

        @Override
        public IMessage onMessage(OutpostInventoryRemovePacket packet, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null) return null;

            AutomatedOutpostState state = OutpostDataStore.get()
                .getByAssetId(packet.assetId);
            if (state == null) return null;

            ItemStackWrapper resource = ItemStackWrapper.fromKey(packet.resourceKey);
            if (resource == null) return null;

            long amount = state.inventory.getAmount(resource);
            if (amount > 0) {
                state.inventory.add(resource, -amount);
            }
            return new OutpostFullSyncPacket(state);
        }
    }

    private static void writeString(ByteBuf buf, String s) {
        PacketUtil.writeString(buf, s);
    }

    private static String readString(ByteBuf buf) {
        return PacketUtil.readString(buf);
    }
}
