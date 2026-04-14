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
    private String action;

    public OutpostModuleActionPacket() {}

    public OutpostModuleActionPacket(CelestialAsset.ID assetId, int moduleIndex, String action) {
        this.assetId = assetId;
        this.moduleIndex = moduleIndex;
        this.action = action;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        writeString(buf, String.valueOf(assetId));
        buf.writeInt(moduleIndex);
        writeString(buf, action);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        assetId = CelestialAsset.ID.from(readString(buf));
        moduleIndex = buf.readInt();
        action = readString(buf);
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
                case "ENABLE":
                    // If it was disabled, set it to OPERATIONAL.
                    // If it was IN_CONSTRUCTION, maybe it should stay that way?
                    // User said "Toggle module status" for DISABLE/ENABLE.
                    if (module.getStatus() == AutomatedOutpostModule.Status.DISABLED) {
                        module.setStatus(AutomatedOutpostModule.Status.OPERATIONAL);
                    }
                    break;
                case "DISABLE":
                    module.setStatus(AutomatedOutpostModule.Status.DISABLED);
                    break;
                case "DESTROY":
                    state.removeModule(packet.moduleIndex);
                    break;
                case "CONFIGURE":
                    return null;
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
