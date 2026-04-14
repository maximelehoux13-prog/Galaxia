package com.gtnewhorizons.galaxia.outpost.network;

import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;

import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.outpost.AutomatedOutpost;
import com.gtnewhorizons.galaxia.outpost.persistence.OutpostDataStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;

/**
 * Client → Server: requests a full sync for a single outpost.
 */
public final class OutpostRequestSyncPacket implements IMessage {

    private CelestialAsset.ID assetId;

    public OutpostRequestSyncPacket() {}

    public OutpostRequestSyncPacket(CelestialAsset.ID assetId) {
        this.assetId = assetId;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        writeString(buf, assetId.toString());
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        assetId = CelestialAsset.ID.from(readString(buf));
    }

    public static final class Handler implements IMessageHandler<OutpostRequestSyncPacket, IMessage> {

        @Override
        public IMessage onMessage(OutpostRequestSyncPacket packet, MessageContext ctx) {
            if (ctx.side != Side.SERVER) return null;
            AutomatedOutpost state = OutpostDataStore.get()
                .getByAssetId(packet.assetId);
            if (state == null) {
                // Lazily create state the first time a client opens the management UI
                CelestialAsset asset = CelestialAssetStore.findAsset(packet.assetId);
                if (asset != null && asset.status() == CelestialAsset.Status.OPERATIONAL) {
                    EntityPlayerMP player = ctx.getServerHandler().playerEntity;
                    UUID teamId = player != null ? player.getUniqueID() : new UUID(0L, 0L);
                    CelestialObjectId bodyId = asset.celestialObjectId;
                    state = new AutomatedOutpost(asset.assetId, teamId, bodyId);
                    OutpostDataStore.get()
                        .put(state);
                    Galaxia.LOG.info(
                        "[Outpost] Auto-created state for outpost {} (player {})",
                        packet.assetId,
                        player != null ? player.getGameProfile()
                            .getName() : "unknown");
                }
            }
            if (state != null) {
                return new OutpostFullSyncPacket(state);
            }
            return null;
        }
    }

    private static void writeString(ByteBuf buf, String s) {
        PacketUtil.writeString(buf, s);
    }

    private static String readString(ByteBuf buf) {
        return PacketUtil.readString(buf);
    }
}
