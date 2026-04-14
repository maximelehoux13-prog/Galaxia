package com.gtnewhorizons.galaxia.outpost.network;

import java.util.List;

import com.gtnewhorizons.galaxia.outpost.AutomatedOutpost;
import com.gtnewhorizons.galaxia.outpost.module.AutomatedOutpostModule;
import com.gtnewhorizons.galaxia.outpost.persistence.OutpostDataStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * Client → Server: requests an action on a specific module in an outpost.
 */
public final class OutpostModuleActionPacket implements IMessage {

    private CelestialAsset.ID assetId;
    private int moduleIndex;
    private Action action;

    public OutpostModuleActionPacket() {}

    public OutpostModuleActionPacket(CelestialAsset.ID assetId, int moduleIndex, Action action) {
        this.assetId = assetId;
        this.moduleIndex = moduleIndex;
        this.action = action;
    }

    public enum Action {
        ENABLE,
        DISABLE,
        DESTROY,
        CONFIGURE
    }

    @Override
    public void toBytes(ByteBuf buf) {
        PacketUtil.writeAssetId(buf, assetId);
        buf.writeInt(moduleIndex);
        PacketUtil.writeEnum(buf, action);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        assetId = PacketUtil.readAssetId(buf);
        moduleIndex = buf.readInt();
        action = PacketUtil.readEnum(buf, Action.class);
    }

    public static final class Handler implements IMessageHandler<OutpostModuleActionPacket, IMessage> {

        @Override
        public IMessage onMessage(OutpostModuleActionPacket packet, MessageContext ctx) {
            AutomatedOutpost state = OutpostDataStore.get()
                .getByAssetId(packet.assetId);
            if (state == null) return null;

            List<AutomatedOutpostModule> modules = state.modules();
            if (packet.moduleIndex < 0 || packet.moduleIndex >= modules.size()) return null;

            AutomatedOutpostModule module = modules.get(packet.moduleIndex);

            switch (packet.action) {
                case ENABLE:
                    if (module.getLegacyStatus() == AutomatedOutpostModule.Status.DISABLED) {
                        module.setLegacyStatus(AutomatedOutpostModule.Status.OPERATIONAL);
                    }
                    break;
                case DISABLE:
                    module.setLegacyStatus(AutomatedOutpostModule.Status.DISABLED);
                    break;
                case DESTROY:
                    state.removeModule(packet.moduleIndex);
                    break;
                case CONFIGURE:
                    return null;
            }
            return new OutpostFullSyncPacket(state);
        }
    }
}
