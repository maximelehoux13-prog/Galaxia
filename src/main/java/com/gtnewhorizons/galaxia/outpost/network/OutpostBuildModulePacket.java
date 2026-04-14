package com.gtnewhorizons.galaxia.outpost.network;

import net.minecraft.entity.player.EntityPlayerMP;

import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.outpost.AutomatedOutpost;
import com.gtnewhorizons.galaxia.outpost.module.AutomatedOutpostModule;
import com.gtnewhorizons.galaxia.outpost.module.ModuleBigHammer;
import com.gtnewhorizons.galaxia.outpost.module.ModuleHammer;
import com.gtnewhorizons.galaxia.outpost.module.ModuleMiner;
import com.gtnewhorizons.galaxia.outpost.module.ModulePower;
import com.gtnewhorizons.galaxia.outpost.module.OutpostModuleKind;
import com.gtnewhorizons.galaxia.outpost.persistence.OutpostDataStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialManagedAsset;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * Client → Server: requests that a new module be queued for construction on an outpost.
 *
 * <p>
 * The server creates an {@link AutomatedOutpostModule} in {@code IN_CONSTRUCTION} state and
 * adds it to the outpost. Construction then proceeds tick-by-tick as the module consumes
 * resources from the outpost's inventory.
 *
 * <p>
 * Returns an {@link OutpostFullSyncPacket} so the requesting client immediately sees the
 * new module in the UI.
 */
public final class OutpostBuildModulePacket implements IMessage {

    private CelestialAsset.ID assetId;
    private String moduleKind;
    private boolean instantBuild;

    public OutpostBuildModulePacket() {}

    public OutpostBuildModulePacket(CelestialAsset.ID assetId, OutpostModuleKind kind, boolean instantBuild) {
        this.assetId = assetId;
        this.moduleKind = kind.name();
        this.instantBuild = instantBuild;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        writeString(buf, String.valueOf(assetId));
        writeString(buf, moduleKind);
        buf.writeBoolean(instantBuild);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        assetId = CelestialAsset.ID.from(readString(buf));
        moduleKind = readString(buf);
        instantBuild = buf.readBoolean();
    }

    public static final class Handler implements IMessageHandler<OutpostBuildModulePacket, IMessage> {

        @Override
        public IMessage onMessage(OutpostBuildModulePacket packet, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null) return null;

            AutomatedOutpost state = OutpostDataStore.get()
                .getByAssetId(packet.assetId);
            if (state == null) {
                Galaxia.LOG.warn(
                    "[Outpost] BuildModule: unknown assetId {} from player {}",
                    packet.assetId,
                    player.getGameProfile()
                        .getName());
                return null;
            }

            OutpostModuleKind kind;
            try {
                kind = OutpostModuleKind.valueOf(packet.moduleKind);
            } catch (IllegalArgumentException e) {
                Galaxia.LOG.warn(
                    "[Outpost] BuildModule: unknown module kind '{}' from player {}",
                    packet.moduleKind,
                    player.getGameProfile()
                        .getName());
                return null;
            }

            CelestialManagedAsset asset = CelestialAssetStore.findAsset(packet.assetId);
            if (asset == null) {
                Galaxia.LOG.warn(
                    "[Outpost] BuildModule: missing asset {} for player {}",
                    packet.assetId,
                    player.getGameProfile()
                        .getName());
                return null;
            }
            if (kind == OutpostModuleKind.MINER && asset.kind() != CelestialAsset.Kind.AUTOMATED_OUTPOST) {
                Galaxia.LOG.warn(
                    "[Outpost] BuildModule: rejected MINER on {} ({}) from player {}",
                    packet.assetId,
                    asset.kind(),
                    player.getGameProfile()
                        .getName());
                return null;
            }

            AutomatedOutpostModule module = createModule(kind);
            if (module == null) {
                Galaxia.LOG.warn(
                    "[Outpost] BuildModule: no module for kind {} (player {})",
                    kind,
                    player.getGameProfile()
                        .getName());
                return null;
            }
            if (packet.instantBuild && player.capabilities.isCreativeMode) {
                module.completeConstructionInstantly();
            }
            state.addModule(module);

            Galaxia.LOG.debug(
                "[Outpost] BuildModule: queued {} construction on outpost {} (by {})",
                kind.getDisplayName(),
                packet.assetId,
                player.getGameProfile()
                    .getName());

            // Send a full sync back so the requesting client sees the new module immediately.
            return new OutpostFullSyncPacket(state);
        }

        private AutomatedOutpostModule createModule(OutpostModuleKind kind) {
            return switch (kind) {
                case HAMMER -> ModuleHammer.getDefault();
                case BIG_HAMMER -> ModuleBigHammer.getDefault();
                case MINER -> ModuleMiner.getDefault();
                case POWER -> ModulePower.getDefault();
            };
        }
    }

    private static void writeString(ByteBuf buf, String s) {
        PacketUtil.writeString(buf, s);
    }

    private static String readString(ByteBuf buf) {
        return PacketUtil.readString(buf);
    }
}
