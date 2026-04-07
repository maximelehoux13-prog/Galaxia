package com.gtnewhorizons.galaxia.outpost.network;

import java.nio.charset.StandardCharsets;

import com.gtnewhorizons.galaxia.outpost.AutomatedOutpostModule;
import com.gtnewhorizons.galaxia.outpost.AutomatedOutpostState;
import com.gtnewhorizons.galaxia.outpost.module.MinerModuleData;
import com.gtnewhorizons.galaxia.outpost.persistence.OutpostDataStore;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public final class OutpostModuleConfigPacket implements IMessage {

    private String assetId;
    private int moduleIndex;
    private String action;
    private String payload;

    public OutpostModuleConfigPacket() {}

    public OutpostModuleConfigPacket(String assetId, int moduleIndex, String action, String payload) {
        this.assetId = assetId;
        this.moduleIndex = moduleIndex;
        this.action = action;
        this.payload = payload == null ? "" : payload;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        writeString(buf, assetId);
        buf.writeInt(moduleIndex);
        writeString(buf, action);
        writeString(buf, payload);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        assetId = readString(buf);
        moduleIndex = buf.readInt();
        action = readString(buf);
        payload = readString(buf);
    }

    public static final class Handler implements IMessageHandler<OutpostModuleConfigPacket, IMessage> {

        @Override
        public IMessage onMessage(OutpostModuleConfigPacket packet, MessageContext ctx) {
            AutomatedOutpostState state = OutpostDataStore.get().getByAssetId(packet.assetId);
            if (state == null) return null;
            if (packet.moduleIndex < 0 || packet.moduleIndex >= state.modules().size()) return null;
            AutomatedOutpostModule module = state.modules().get(packet.moduleIndex);

            switch (packet.action) {
                case "ADD_MINER_BLACKLIST" -> {
                    if (!(module.getData() instanceof MinerModuleData minerData)) return null;
                    module.setData(minerData.withAddedBlacklist(packet.payload));
                }
                case "REMOVE_MINER_BLACKLIST" -> {
                    if (!(module.getData() instanceof MinerModuleData minerData)) return null;
                    module.setData(minerData.withRemovedBlacklist(packet.payload));
                }
                default -> {
                    return null;
                }
            }
            return new OutpostFullSyncPacket(state);
        }
    }

    private static void writeString(ByteBuf buf, String s) {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        buf.writeShort(bytes.length);
        buf.writeBytes(bytes);
    }

    private static String readString(ByteBuf buf) {
        int len = buf.readUnsignedShort();
        byte[] bytes = new byte[len];
        buf.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
