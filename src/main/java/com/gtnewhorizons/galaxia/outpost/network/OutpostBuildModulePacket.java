package com.gtnewhorizons.galaxia.outpost.network;

import net.minecraft.entity.player.EntityPlayerMP;

import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.outpost.AutomatedOutpostModule;
import com.gtnewhorizons.galaxia.outpost.AutomatedOutpostState;
import com.gtnewhorizons.galaxia.outpost.OutpostModuleKind;
import com.gtnewhorizons.galaxia.outpost.module.BigHammerModuleData;
import com.gtnewhorizons.galaxia.outpost.module.HammerModuleData;
import com.gtnewhorizons.galaxia.outpost.module.MinerModuleData;
import com.gtnewhorizons.galaxia.outpost.module.OutpostModuleData;
import com.gtnewhorizons.galaxia.outpost.module.PowerModuleData;
import com.gtnewhorizons.galaxia.outpost.persistence.OutpostDataStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetKind;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialManagedAsset;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * Client → Server: requests that a new module be queued for construction on an outpost.
 *
 * <p>The server creates an {@link AutomatedOutpostModule} in {@code IN_CONSTRUCTION} state and
 * adds it to the outpost. Construction then proceeds tick-by-tick as the module consumes
 * resources from the outpost's inventory.
 *
 * <p>Returns an {@link OutpostFullSyncPacket} so the requesting client immediately sees the
 * new module in the UI.
 */
public final class OutpostBuildModulePacket implements IMessage {

    private String assetId;
    private String moduleKind;
    private boolean instantBuild;

    public OutpostBuildModulePacket() {}

    public OutpostBuildModulePacket(String assetId, OutpostModuleKind kind, boolean instantBuild) {
        this.assetId = assetId;
        this.moduleKind = kind.name();
        this.instantBuild = instantBuild;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        writeString(buf, assetId);
        writeString(buf, moduleKind);
        buf.writeBoolean(instantBuild);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        assetId = readString(buf);
        moduleKind = readString(buf);
        instantBuild = buf.readBoolean();
    }

    public static final class Handler implements IMessageHandler<OutpostBuildModulePacket, IMessage> {

        @Override
        public IMessage onMessage(OutpostBuildModulePacket packet, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null) return null;

            AutomatedOutpostState state = OutpostDataStore.get().getByAssetId(packet.assetId);
            if (state == null) {
                Galaxia.LOG.warn(
                    "[Outpost] BuildModule: unknown assetId {} from player {}",
                    packet.assetId,
                    player.getGameProfile().getName());
                return null;
            }

            OutpostModuleKind kind;
            try {
                kind = OutpostModuleKind.valueOf(packet.moduleKind);
            } catch (IllegalArgumentException e) {
                Galaxia.LOG.warn(
                    "[Outpost] BuildModule: unknown module kind '{}' from player {}",
                    packet.moduleKind,
                    player.getGameProfile().getName());
                return null;
            }

            CelestialManagedAsset asset = CelestialAssetStore.findAsset(packet.assetId);
            if (asset == null) {
                Galaxia.LOG.warn(
                    "[Outpost] BuildModule: missing asset {} for player {}",
                    packet.assetId,
                    player.getGameProfile().getName());
                return null;
            }
            if (kind == OutpostModuleKind.MINER && asset.kind() != CelestialAssetKind.AUTOMATED_OUTPOST) {
                Galaxia.LOG.warn(
                    "[Outpost] BuildModule: rejected MINER on {} ({}) from player {}",
                    packet.assetId,
                    asset.kind(),
                    player.getGameProfile().getName());
                return null;
            }

            OutpostModuleData data = createModuleData(kind);
            if (data == null) {
                Galaxia.LOG.warn(
                    "[Outpost] BuildModule: no module data for kind {} (player {})",
                    kind,
                    player.getGameProfile().getName());
                return null;
            }
            AutomatedOutpostModule module = new AutomatedOutpostModule(kind, data);
            if (packet.instantBuild && player.capabilities.isCreativeMode) {
                module.completeConstructionInstantly();
            }
            state.addModule(module);

            Galaxia.LOG.debug(
                "[Outpost] BuildModule: queued {} construction on outpost {} (by {})",
                kind.displayName,
                packet.assetId,
                player.getGameProfile().getName());

            // Send a full sync back so the requesting client sees the new module immediately.
            return new OutpostFullSyncPacket(state);
        }

        private OutpostModuleData createModuleData(OutpostModuleKind kind) {
            switch (kind) {
                case HAMMER:
                    return HammerModuleData.getDefault();
                case BIG_HAMMER:
                    return BigHammerModuleData.getDefault();
                case MINER:
                    return new MinerModuleData();
                case POWER:
                    return new PowerModuleData();
                default:
                    return null;
            }
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
