package com.gtnewhorizons.galaxia.outpost.network;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.client.Minecraft;

import com.gtnewhorizons.galaxia.outpost.AutomatedOutpost;
import com.gtnewhorizons.galaxia.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.outpost.logistics.AllowShootingConfig;
import com.gtnewhorizons.galaxia.outpost.module.AutomatedOutpostModule;
import com.gtnewhorizons.galaxia.outpost.module.ModuleBigHammer;
import com.gtnewhorizons.galaxia.outpost.module.ModuleHammer;
import com.gtnewhorizons.galaxia.outpost.module.ModuleMiner;
import com.gtnewhorizons.galaxia.outpost.module.ModulePower;
import com.gtnewhorizons.galaxia.outpost.module.OutpostModuleKind;
import com.gtnewhorizons.galaxia.outpost.persistence.OutpostDataStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalTransferPlanner;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

public final class OutpostDeltaPacket implements IMessage {

    public static final byte MODULE_ADDED = 0;
    public static final byte MODULE_REMOVED = 1;
    public static final byte MODULE_UPDATED = 2;
    public static final byte INVENTORY_ADDED = 3;
    public static final byte INVENTORY_REMOVED = 4;
    public static final byte LOGISTICS_CONFIG_UPDATED = 5;
    public static final byte LOGISTICS_CONFIG_REMOVED = 6;

    private CelestialAsset.ID assetId;
    private byte deltaType;
    private int moduleIndex;
    private OutpostModuleKind moduleKind;
    private AutomatedOutpostModule.Status status;
    private float progress;
    private List<String> minerBlacklist;
    private boolean minerCopySettings;
    private AllowShootingConfig.Mode shootingMode;
    private double shootingThreshold;
    private boolean planetaryHandling;
    private OrbitalTransferPlanner.RoutePriority routePriority;
    private String resourceKey;
    private long inventoryAmount;
    private int logMinReserve;
    private int logOrderSize;
    private boolean logImportEnabled;
    private boolean logSupplyEnabled;

    public OutpostDeltaPacket() {}

    public static OutpostDeltaPacket moduleAdded(CelestialAsset.ID assetId, int moduleIndex, AutomatedOutpostModule module) {
        OutpostDeltaPacket pkt = new OutpostDeltaPacket();
        pkt.assetId = assetId;
        pkt.deltaType = MODULE_ADDED;
        pkt.moduleIndex = moduleIndex;
        pkt.moduleKind = module.getKind();
        pkt.status = module.status();
        pkt.progress = module.getConstructionProgress();
        pkt.minerBlacklist = extractMinerData(module);
        pkt.minerCopySettings = extractMinerCopySettings(module);
        pkt.shootingMode = extractShootingMode(module);
        pkt.shootingThreshold = extractShootingThreshold(module);
        pkt.planetaryHandling = extractPlanetaryHandling(module);
        pkt.routePriority = extractRoutePriority(module);
        return pkt;
    }

    public static OutpostDeltaPacket moduleRemoved(CelestialAsset.ID assetId, int moduleIndex) {
        OutpostDeltaPacket pkt = new OutpostDeltaPacket();
        pkt.assetId = assetId;
        pkt.deltaType = MODULE_REMOVED;
        pkt.moduleIndex = moduleIndex;
        return pkt;
    }

    public static OutpostDeltaPacket moduleUpdated(CelestialAsset.ID assetId, int moduleIndex, AutomatedOutpostModule module) {
        OutpostDeltaPacket pkt = new OutpostDeltaPacket();
        pkt.assetId = assetId;
        pkt.deltaType = MODULE_UPDATED;
        pkt.moduleIndex = moduleIndex;
        pkt.status = module.status();
        pkt.progress = module.getConstructionProgress();
        pkt.minerBlacklist = extractMinerData(module);
        pkt.minerCopySettings = extractMinerCopySettings(module);
        pkt.shootingMode = extractShootingMode(module);
        pkt.shootingThreshold = extractShootingThreshold(module);
        pkt.planetaryHandling = extractPlanetaryHandling(module);
        pkt.routePriority = extractRoutePriority(module);
        return pkt;
    }

    public static OutpostDeltaPacket inventoryAdded(CelestialAsset.ID assetId, String resourceKey, long amount) {
        OutpostDeltaPacket pkt = new OutpostDeltaPacket();
        pkt.assetId = assetId;
        pkt.deltaType = INVENTORY_ADDED;
        pkt.resourceKey = resourceKey;
        pkt.inventoryAmount = amount;
        return pkt;
    }

    public static OutpostDeltaPacket inventoryRemoved(CelestialAsset.ID assetId, String resourceKey, long amount) {
        OutpostDeltaPacket pkt = new OutpostDeltaPacket();
        pkt.assetId = assetId;
        pkt.deltaType = INVENTORY_REMOVED;
        pkt.resourceKey = resourceKey;
        pkt.inventoryAmount = amount;
        return pkt;
    }

    public static OutpostDeltaPacket logisticsConfigUpdated(CelestialAsset.ID assetId, String resourceKey,
            int minReserve, int orderSize, boolean importEnabled, boolean supplyEnabled) {
        OutpostDeltaPacket pkt = new OutpostDeltaPacket();
        pkt.assetId = assetId;
        pkt.deltaType = LOGISTICS_CONFIG_UPDATED;
        pkt.resourceKey = resourceKey;
        pkt.logMinReserve = minReserve;
        pkt.logOrderSize = orderSize;
        pkt.logImportEnabled = importEnabled;
        pkt.logSupplyEnabled = supplyEnabled;
        return pkt;
    }

    public static OutpostDeltaPacket logisticsConfigRemoved(CelestialAsset.ID assetId, String resourceKey) {
        OutpostDeltaPacket pkt = new OutpostDeltaPacket();
        pkt.assetId = assetId;
        pkt.deltaType = LOGISTICS_CONFIG_REMOVED;
        pkt.resourceKey = resourceKey;
        return pkt;
    }

    private static List<String> extractMinerData(AutomatedOutpostModule module) {
        if (module instanceof ModuleMiner miner) {
            return new ArrayList<>(miner.blacklistedItemKeys);
        }
        return Collections.emptyList();
    }

    private static boolean extractMinerCopySettings(AutomatedOutpostModule module) {
        if (module instanceof ModuleMiner miner) {
            return miner.getCopySettingsToOtherMiners();
        }
        return false;
    }

    private static AllowShootingConfig.Mode extractShootingMode(AutomatedOutpostModule module) {
        if (module instanceof ModuleHammer hammer) {
            return hammer.getConfig().mode();
        }
        return AllowShootingConfig.Mode.ALWAYS;
    }

    private static double extractShootingThreshold(AutomatedOutpostModule module) {
        if (module instanceof ModuleHammer hammer) {
            return hammer.getConfig().threshold();
        }
        return 0.0;
    }

    private static boolean extractPlanetaryHandling(AutomatedOutpostModule module) {
        if (module instanceof ModuleBigHammer bh) {
            return bh.getPlanetaryHandling();
        }
        return false;
    }

    private static OrbitalTransferPlanner.RoutePriority extractRoutePriority(AutomatedOutpostModule module) {
        if (module instanceof ModuleHammer hammer) {
            return hammer.getRoutePriority();
        }
        return OrbitalTransferPlanner.RoutePriority.PRIORITIZE_TOF;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        PacketUtil.writeAssetId(buf, assetId);
        buf.writeByte(deltaType);

        switch (deltaType) {
            case MODULE_ADDED, MODULE_UPDATED -> {
                PacketUtil.writeEnum(buf, moduleKind != null ? moduleKind : OutpostModuleKind.POWER);
                PacketUtil.writeEnum(buf, status);
                buf.writeFloat(progress);
                buf.writeInt(minerBlacklist.size());
                for (String key : minerBlacklist) {
                    PacketUtil.writeString(buf, key);
                }
                buf.writeBoolean(minerCopySettings);
                PacketUtil.writeEnum(buf, shootingMode);
                buf.writeDouble(shootingThreshold);
                buf.writeBoolean(planetaryHandling);
                PacketUtil.writeEnum(buf, routePriority);
            }
            case MODULE_REMOVED -> buf.writeInt(moduleIndex);
            case INVENTORY_ADDED, INVENTORY_REMOVED -> {
                PacketUtil.writeString(buf, resourceKey);
                buf.writeLong(inventoryAmount);
            }
            case LOGISTICS_CONFIG_UPDATED -> {
                PacketUtil.writeString(buf, resourceKey);
                buf.writeInt(logMinReserve);
                buf.writeInt(logOrderSize);
                buf.writeBoolean(logImportEnabled);
                buf.writeBoolean(logSupplyEnabled);
            }
            case LOGISTICS_CONFIG_REMOVED -> PacketUtil.writeString(buf, resourceKey);
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        assetId = PacketUtil.readAssetId(buf);
        deltaType = buf.readByte();

        switch (deltaType) {
            case MODULE_ADDED, MODULE_UPDATED -> {
                moduleIndex = buf.readInt();
                moduleKind = PacketUtil.readEnum(buf, OutpostModuleKind.class);
                status = PacketUtil.readEnum(buf, AutomatedOutpostModule.Status.class);
                progress = buf.readFloat();
                int blacklistSize = buf.readInt();
                minerBlacklist = new ArrayList<>(blacklistSize);
                for (int i = 0; i < blacklistSize; i++) {
                    minerBlacklist.add(PacketUtil.readString(buf));
                }
                minerCopySettings = buf.readBoolean();
                shootingMode = PacketUtil.readEnum(buf, AllowShootingConfig.Mode.class);
                shootingThreshold = buf.readDouble();
                planetaryHandling = buf.readBoolean();
                routePriority = PacketUtil.readEnum(buf, OrbitalTransferPlanner.RoutePriority.class);
            }
            case MODULE_REMOVED -> moduleIndex = buf.readInt();
            case INVENTORY_ADDED, INVENTORY_REMOVED -> {
                resourceKey = PacketUtil.readString(buf);
                inventoryAmount = buf.readLong();
            }
            case LOGISTICS_CONFIG_UPDATED -> {
                resourceKey = PacketUtil.readString(buf);
                logMinReserve = buf.readInt();
                logOrderSize = buf.readInt();
                logImportEnabled = buf.readBoolean();
                logSupplyEnabled = buf.readBoolean();
            }
            case LOGISTICS_CONFIG_REMOVED -> resourceKey = PacketUtil.readString(buf);
        }
    }

    public static final class Handler implements IMessageHandler<OutpostDeltaPacket, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(OutpostDeltaPacket packet, MessageContext ctx) {
            Minecraft.getMinecraft().func_152344_a(() -> {
                AutomatedOutpost state = OutpostDataStore.get().getByAssetId(packet.assetId);
                if (state == null) return;

                switch (packet.deltaType) {
                    case MODULE_ADDED -> handleModuleAdded(state, packet);
                    case MODULE_REMOVED -> handleModuleRemoved(state, packet);
                    case MODULE_UPDATED -> handleModuleUpdated(state, packet);
                    case INVENTORY_ADDED -> handleInventoryAdded(state, packet);
                    case INVENTORY_REMOVED -> handleInventoryRemoved(state, packet);
                    case LOGISTICS_CONFIG_UPDATED -> handleLogisticsConfigUpdated(state, packet);
                    case LOGISTICS_CONFIG_REMOVED -> handleLogisticsConfigRemoved(state, packet);
                }
            });
            return null;
        }

        private void handleModuleAdded(AutomatedOutpost state, OutpostDeltaPacket packet) {
            AutomatedOutpostModule module = createModule(packet);
            module.updateStatus(packet.status);
            if (packet.moduleIndex < state.modules().size()) {
                state.modules().set(packet.moduleIndex, module);
            } else {
                state.addModule(module);
            }
        }

        private void handleModuleRemoved(AutomatedOutpost state, OutpostDeltaPacket packet) {
            if (packet.moduleIndex >= 0 && packet.moduleIndex < state.modules().size()) {
                state.modules().remove(packet.moduleIndex);
            }
        }

        private void handleModuleUpdated(AutomatedOutpost state, OutpostDeltaPacket packet) {
            if (packet.moduleIndex >= 0 && packet.moduleIndex < state.modules().size()) {
                AutomatedOutpostModule module = state.modules().get(packet.moduleIndex);
                module.updateStatus(packet.status);
                applyMinerData(module, packet);
                applyHammerData(module, packet);
            }
        }

        private void handleInventoryAdded(AutomatedOutpost state, OutpostDeltaPacket packet) {
            ItemStackWrapper resource = ItemStackWrapper.fromKey(packet.resourceKey);
            if (resource == null) return;
            long currentAmount = state.inventory.getAmount(resource);
            long newAmount = currentAmount + packet.inventoryAmount;
            state.inventory.setAmount(resource, newAmount);
        }

        private void handleInventoryRemoved(AutomatedOutpost state, OutpostDeltaPacket packet) {
            ItemStackWrapper resource = ItemStackWrapper.fromKey(packet.resourceKey);
            if (resource == null) return;
            long currentAmount = state.inventory.getAmount(resource);
            long newAmount = Math.max(0, currentAmount - packet.inventoryAmount);
            state.inventory.setAmount(resource, newAmount);
        }

        private void handleLogisticsConfigUpdated(AutomatedOutpost state, OutpostDeltaPacket packet) {
            ItemStackWrapper resource = ItemStackWrapper.fromKey(packet.resourceKey);
            if (resource == null) return;
            state.logisticsConfig.set(resource, new com.gtnewhorizons.galaxia.outpost.LogisticsResourceConfig(
                packet.logMinReserve, packet.logOrderSize, packet.logImportEnabled, packet.logSupplyEnabled));
        }

        private void handleLogisticsConfigRemoved(AutomatedOutpost state, OutpostDeltaPacket packet) {
            ItemStackWrapper resource = ItemStackWrapper.fromKey(packet.resourceKey);
            if (resource == null) return;
            state.logisticsConfig.reset(resource);
        }

        private AutomatedOutpostModule createModule(OutpostDeltaPacket packet) {
            return switch (packet.moduleKind) {
                case HAMMER -> new ModuleHammer(
                    new AllowShootingConfig(packet.shootingMode, packet.shootingThreshold),
                    packet.routePriority);
                case BIG_HAMMER -> new ModuleBigHammer(
                    packet.planetaryHandling,
                    new AllowShootingConfig(packet.shootingMode, packet.shootingThreshold),
                    packet.routePriority);
                case MINER -> new ModuleMiner(packet.minerBlacklist)
                    .withCopySettingsToOtherMiners(packet.minerCopySettings);
                case POWER -> new ModulePower();
                default -> new ModulePower();
            };
        }

        private void applyMinerData(AutomatedOutpostModule module, OutpostDeltaPacket packet) {
            if (module instanceof ModuleMiner miner) {
                miner.blacklistedItemKeys.clear();
                miner.blacklistedItemKeys.addAll(packet.minerBlacklist);
                miner.withCopySettingsToOtherMiners(packet.minerCopySettings);
            }
        }

        private void applyHammerData(AutomatedOutpostModule module, OutpostDeltaPacket packet) {
            if (module instanceof ModuleHammer hammer) {
                hammer.setConfig(new AllowShootingConfig(packet.shootingMode, packet.shootingThreshold));
                hammer.setPriority(packet.routePriority);
            }
            if (module instanceof ModuleBigHammer bh) {
                bh.setPlanetaryHandling(packet.planetaryHandling);
            }
        }
    }
}