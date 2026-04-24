package com.gtnewhorizons.galaxia.core.network;

import net.minecraft.entity.player.EntityPlayerMP;

import com.gtnewhorizons.galaxia.compat.TempTeamCompat;
import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public final class AssetCreateRequestPacket implements IMessage {

    private CelestialObjectId celestialObjectId;
    private String displayName;
    private CelestialAsset.Kind kind;
    private boolean operational;

    public AssetCreateRequestPacket() {}

    public AssetCreateRequestPacket(CelestialObjectId celestialObjectId, String displayName, CelestialAsset.Kind kind,
        boolean operational) {
        this.celestialObjectId = celestialObjectId;
        this.displayName = displayName;
        this.kind = kind;
        this.operational = operational;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        PacketUtil.writeEnum(buf, celestialObjectId);
        PacketUtil.writeString(buf, displayName);
        PacketUtil.writeEnum(buf, kind);
        buf.writeBoolean(operational);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        celestialObjectId = PacketUtil.readEnum(buf, CelestialObjectId.class);
        displayName = PacketUtil.readString(buf);
        kind = PacketUtil.readEnum(buf, CelestialAsset.Kind.class);
        operational = buf.readBoolean();
    }

    public static final class Handler implements IMessageHandler<AssetCreateRequestPacket, IMessage> {

        @Override
        public IMessage onMessage(AssetCreateRequestPacket packet, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null) return null;

            var teamId = TempTeamCompat.getTeam(player);

            CelestialAsset asset;
            asset = CelestialAssetStore
                .createAsset(teamId, packet.celestialObjectId, packet.displayName, packet.kind, packet.operational);

            Galaxia.LOG.info(
                "[Outpost] Created asset {} ({}) at {} for player {}",
                asset.assetId,
                packet.kind,
                packet.celestialObjectId,
                player.getGameProfile()
                    .getName());

            return AssetSyncPacket.fullSync(asset);
        }
    }
}
