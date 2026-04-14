package com.gtnewhorizons.galaxia.outpost.network;

import com.gtnewhorizons.galaxia.outpost.AutomatedOutpost;
import com.gtnewhorizons.galaxia.outpost.logistics.AllowShootingConfig;
import com.gtnewhorizons.galaxia.outpost.module.AutomatedOutpostModule;
import com.gtnewhorizons.galaxia.outpost.module.ModuleBigHammer;
import com.gtnewhorizons.galaxia.outpost.module.ModuleHammer;
import com.gtnewhorizons.galaxia.outpost.module.ModuleMiner;
import com.gtnewhorizons.galaxia.outpost.persistence.OutpostDataStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalTransferPlanner;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public final class OutpostModuleConfigPacket implements IMessage {

    private CelestialAsset.ID assetId;
    private int moduleIndex;
    private ConfigAction action;
    private String payload;

    public OutpostModuleConfigPacket() {}

    public OutpostModuleConfigPacket(CelestialAsset.ID assetId, int moduleIndex, ConfigAction action, String payload) {
        this.assetId = assetId;
        this.moduleIndex = moduleIndex;
        this.action = action;
        this.payload = payload == null ? "" : payload;
    }

    public enum ConfigAction {
        ADD_MINER_BLACKLIST,
        REMOVE_MINER_BLACKLIST,
        SET_MINER_COPY_SETTINGS,
        SET_ALLOW_SHOOTING_MODE,
        SET_ALLOW_SHOOTING_THRESHOLD,
        SET_PLANETARY_HANDLING,
        SET_ROUTE_PRIORITY
    }

    @Override
    public void toBytes(ByteBuf buf) {
        PacketUtil.writeAssetId(buf, assetId);
        buf.writeInt(moduleIndex);
        PacketUtil.writeEnum(buf, action);
        PacketUtil.writeString(buf, payload);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        assetId = PacketUtil.readAssetId(buf);
        moduleIndex = buf.readInt();
        action = PacketUtil.readEnum(buf, ConfigAction.class);
        payload = PacketUtil.readString(buf);
    }

    public static final class Handler implements IMessageHandler<OutpostModuleConfigPacket, IMessage> {

        @Override
        public IMessage onMessage(OutpostModuleConfigPacket packet, MessageContext ctx) {
            AutomatedOutpost state = OutpostDataStore.get()
                .getByAssetId(packet.assetId);
            if (state == null) return null;
            if (packet.moduleIndex < 0 || packet.moduleIndex >= state.modules()
                .size()) return null;
            AutomatedOutpostModule module = state.modules()
                .get(packet.moduleIndex);

            switch (packet.action) {
                case ADD_MINER_BLACKLIST -> {
                    if (!(module instanceof ModuleMiner miner)) return null;
                    ((ModuleMiner) module).withAddedBlacklist(packet.payload);
                    if (((ModuleMiner) module).getCopySettingsToOtherMiners()) {
                        copyMinerSettingsToOtherMiners(state, packet.moduleIndex, (ModuleMiner) module);
                    }
                }
                case REMOVE_MINER_BLACKLIST -> {
                    if (!(module instanceof ModuleMiner miner)) return null;
                    ((ModuleMiner) module).withRemovedBlacklist(packet.payload);
                    if (((ModuleMiner) module).getCopySettingsToOtherMiners()) {
                        copyMinerSettingsToOtherMiners(state, packet.moduleIndex, (ModuleMiner) module);
                    }
                }
                case SET_MINER_COPY_SETTINGS -> {
                    if (!(module instanceof ModuleMiner miner)) return null;
                    ((ModuleMiner) module).withCopySettingsToOtherMiners(Boolean.parseBoolean(packet.payload));
                    if (((ModuleMiner) module).getCopySettingsToOtherMiners()) {
                        copyMinerSettingsToOtherMiners(state, packet.moduleIndex, (ModuleMiner) module);
                    }
                }
                case SET_ALLOW_SHOOTING_MODE -> {
                    AllowShootingConfig.Mode mode;
                    try {
                        mode = AllowShootingConfig.Mode.valueOf(packet.payload);
                    } catch (IllegalArgumentException e) {
                        return null;
                    }
                    if (module instanceof ModuleHammer hammer) {
                        double threshold = hammer.getConfig()
                            .threshold();
                        hammer.setConfig(new AllowShootingConfig(mode, threshold));
                    } else if (module instanceof ModuleBigHammer bh) {
                        double threshold = bh.getConfig()
                            .threshold();
                        bh.setConfig(new AllowShootingConfig(mode, threshold));
                    } else {
                        return null;
                    }
                }
                case SET_ALLOW_SHOOTING_THRESHOLD -> {
                    double threshold;
                    try {
                        threshold = Double.parseDouble(packet.payload);
                    } catch (NumberFormatException e) {
                        return null;
                    }
                    if (module instanceof ModuleHammer hammer) {
                        AllowShootingConfig.Mode mode = hammer.getConfig()
                            .mode();
                        hammer.setConfig(new AllowShootingConfig(mode, threshold));
                    } else if (module instanceof ModuleBigHammer bh) {
                        AllowShootingConfig.Mode mode = bh.getConfig()
                            .mode();
                        bh.setConfig(new AllowShootingConfig(mode, threshold));
                    } else {
                        return null;
                    }
                }
                case SET_PLANETARY_HANDLING -> {
                    if (!(module instanceof ModuleBigHammer bh)) return null;
                    bh.setPlanetaryHandling(Boolean.parseBoolean(packet.payload));
                }
                case SET_ROUTE_PRIORITY -> {
                    OrbitalTransferPlanner.RoutePriority priority;
                    try {
                        priority = OrbitalTransferPlanner.RoutePriority.valueOf(packet.payload);
                    } catch (IllegalArgumentException e) {
                        return null;
                    }
                    if (module instanceof ModuleHammer hammer) {
                        hammer.setPriority(priority);
                    } else if (module instanceof ModuleBigHammer bh) {
                        bh.setPriority(priority);
                    } else {
                        return null;
                    }
                }
                default -> {
                    return null;
                }
            }
            return new OutpostFullSyncPacket(state);
        }
    }

    private static void copyMinerSettingsToOtherMiners(AutomatedOutpost state, int sourceModuleIndex,
        ModuleMiner sourceMiner) {
        for (int i = 0; i < state.modules()
            .size(); i++) {
            if (i == sourceModuleIndex) continue;
            AutomatedOutpostModule other = state.modules()
                .get(i);
            if (!(other instanceof ModuleMiner)) continue;
            ((ModuleMiner) other).withCopySettingsToOtherMiners(sourceMiner.getCopySettingsToOtherMiners());
            ((ModuleMiner) other).blacklistedItemKeys.clear();
            ((ModuleMiner) other).blacklistedItemKeys.addAll(sourceMiner.blacklistedItemKeys);
        }
    }
}
