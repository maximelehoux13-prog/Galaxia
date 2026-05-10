package com.gtnewhorizons.galaxia.core.network;

import net.minecraft.client.Minecraft;

import com.gtnewhorizons.galaxia.client.CelestialClient;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

public final class HammerTrajectoryLoadSyncPacket implements IMessage {

    private double ownMsPerTick;
    private double allMsPerTick;

    public HammerTrajectoryLoadSyncPacket() {}

    public HammerTrajectoryLoadSyncPacket(double ownMsPerTick, double allMsPerTick) {
        this.ownMsPerTick = ownMsPerTick;
        this.allMsPerTick = allMsPerTick;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeDouble(ownMsPerTick);
        buf.writeDouble(allMsPerTick);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        ownMsPerTick = buf.readDouble();
        allMsPerTick = buf.readDouble();
    }

    public static final class Handler implements IMessageHandler<HammerTrajectoryLoadSyncPacket, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(HammerTrajectoryLoadSyncPacket packet, MessageContext ctx) {
            Minecraft.getMinecraft()
                .func_152344_a(
                    () -> CelestialClient.updateHammerTrajectoryLoad(packet.ownMsPerTick, packet.allMsPerTick));
            return null;
        }
    }
}
