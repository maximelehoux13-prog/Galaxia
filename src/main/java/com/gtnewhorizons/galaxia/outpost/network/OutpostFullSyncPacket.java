package com.gtnewhorizons.galaxia.outpost.network;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.client.Minecraft;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.galaxia.outpost.AutomatedOutpost;
import com.gtnewhorizons.galaxia.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.outpost.LogisticsResourceConfig;
import com.gtnewhorizons.galaxia.outpost.logistics.AllowShootingConfig;
import com.gtnewhorizons.galaxia.outpost.module.AutomatedOutpostModule;
import com.gtnewhorizons.galaxia.outpost.module.ModuleBigHammer;
import com.gtnewhorizons.galaxia.outpost.module.ModuleHammer;
import com.gtnewhorizons.galaxia.outpost.module.ModuleMiner;
import com.gtnewhorizons.galaxia.outpost.module.ModulePower;
import com.gtnewhorizons.galaxia.outpost.module.OutpostModuleKind;
import com.gtnewhorizons.galaxia.outpost.persistence.OutpostDataStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalTransferPlanner;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

/**
 * Server → Client: synchronizes the complete state of a single outpost.
 */
public final class OutpostFullSyncPacket implements IMessage {

    private CelestialAsset.ID assetId;
    private UUID teamId;
    private CelestialObjectId celestialBodyId;
    private CelestialObjectId systemId;
    private CelestialObjectId planetaryAnchorBodyId;
    private long energyStored;
    private List<ModuleSyncData> modules;
    private Map<String, Long> inventory;
    private Map<String, LogisticsConfigSyncData> logisticsConfig;

    public OutpostFullSyncPacket() {}

    public OutpostFullSyncPacket(AutomatedOutpost state) {
        this.assetId = state.assetId;
        this.teamId = state.teamId;
        this.celestialBodyId = state.celestialBodyId;
        this.systemId = state.systemId;
        this.planetaryAnchorBodyId = state.planetaryAnchorBodyId;
        this.energyStored = state.getEnergyStored();

        this.modules = new ArrayList<>();
        for (AutomatedOutpostModule m : state.modules()) {
            List<String> minerBlacklist = Collections.emptyList();
            AllowShootingConfig.Mode allowShootingMode = AllowShootingConfig.Mode.ALWAYS;
            double allowShootingThreshold = 0.0;
            boolean planetaryHandling = false;
            OrbitalTransferPlanner.RoutePriority routePriority = OrbitalTransferPlanner.RoutePriority.PRIORITIZE_TOF;
            boolean minerCopySettings = false;
            if (m instanceof ModuleMiner minerData) {
                minerBlacklist = minerData.blacklistedItemKeys;
                minerCopySettings = minerData.getCopySettingsToOtherMiners();
            } else if (m instanceof ModuleHammer hd) {
                AllowShootingConfig cfg = hd.getConfig();
                allowShootingMode = cfg.mode();
                allowShootingThreshold = cfg.threshold();
                routePriority = hd.getRoutePriority();
            } else if (m instanceof ModuleBigHammer bh) {
                AllowShootingConfig cfg = bh.getConfig();
                allowShootingMode = cfg.mode();
                allowShootingThreshold = cfg.threshold();
                planetaryHandling = bh.getPlanetaryHandling();
                routePriority = bh.getRoutePriority();
            }
            modules.add(
                new ModuleSyncData(
                    m.getKind(),
                    m.status(),
                    m.getConstructionProgress(),
                    minerBlacklist,
                    allowShootingMode,
                    allowShootingThreshold,
                    planetaryHandling,
                    routePriority,
                    minerCopySettings));
        }

        this.inventory = new LinkedHashMap<>();
        for (Map.Entry<ItemStackWrapper, Long> e : state.inventory.snapshot()
            .entrySet()) {
            inventory.put(
                e.getKey()
                    .toKey(),
                e.getValue());
        }

        this.logisticsConfig = new LinkedHashMap<>();
        for (Map.Entry<ItemStackWrapper, LogisticsResourceConfig> e : state.logisticsConfig.snapshot()
            .entrySet()) {
            LogisticsResourceConfig cfg = e.getValue();
            logisticsConfig.put(
                e.getKey()
                    .toKey(),
                new LogisticsConfigSyncData(
                    cfg.minReserve(),
                    cfg.orderSize(),
                    cfg.isImportEnabled(),
                    cfg.isSupplyEnabled()));
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        PacketUtil.writeAssetId(buf, assetId);
        buf.writeLong(teamId.getMostSignificantBits());
        buf.writeLong(teamId.getLeastSignificantBits());
        PacketUtil.writeCelestialObjectId(buf, celestialBodyId);
        PacketUtil.writeCelestialObjectId(buf, systemId);
        PacketUtil.writeCelestialObjectId(buf, planetaryAnchorBodyId);
        buf.writeLong(energyStored);

        buf.writeInt(modules.size());
        for (ModuleSyncData m : modules) {
            PacketUtil.writeEnum(buf, m.kind());
            PacketUtil.writeEnum(buf, m.status());
            buf.writeFloat(m.progress());
            buf.writeInt(m.minerBlacklist().size());
            for (String key : m.minerBlacklist()) {
                PacketUtil.writeString(buf, key);
            }
            PacketUtil.writeEnum(buf, m.allowShootingMode());
            buf.writeDouble(m.allowShootingThreshold());
            buf.writeBoolean(m.planetaryHandling());
            PacketUtil.writeEnum(buf, m.routePriority());
            buf.writeBoolean(m.minerCopySettings());
        }

        buf.writeInt(inventory.size());
        for (Map.Entry<String, Long> e : inventory.entrySet()) {
            PacketUtil.writeString(buf, e.getKey());
            buf.writeLong(e.getValue());
        }

        buf.writeInt(logisticsConfig.size());
        for (Map.Entry<String, LogisticsConfigSyncData> e : logisticsConfig.entrySet()) {
            PacketUtil.writeString(buf, e.getKey());
            buf.writeInt(e.getValue().minReserve);
            buf.writeInt(e.getValue().orderSize);
            buf.writeBoolean(e.getValue().isImportEnabled);
            buf.writeBoolean(e.getValue().isSupplyEnabled);
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        assetId = PacketUtil.readAssetId(buf);
        teamId = new UUID(buf.readLong(), buf.readLong());
        celestialBodyId = PacketUtil.readCelestialObjectId(buf);
        systemId = PacketUtil.readCelestialObjectId(buf);
        planetaryAnchorBodyId = PacketUtil.readCelestialObjectId(buf);
        energyStored = buf.readLong();

        int moduleCount = buf.readInt();
        modules = new ArrayList<>(moduleCount);
        for (int i = 0; i < moduleCount; i++) {
            OutpostModuleKind kind = PacketUtil.readEnum(buf, OutpostModuleKind.class);
            AutomatedOutpostModule.Status status = PacketUtil.readEnum(buf, AutomatedOutpostModule.Status.class);
            float progress = buf.readFloat();
            int blacklistCount = buf.readInt();
            List<String> minerBlacklist = new ArrayList<>(blacklistCount);
            for (int j = 0; j < blacklistCount; j++) {
                minerBlacklist.add(PacketUtil.readString(buf));
            }
            AllowShootingConfig.Mode allowShootingMode = PacketUtil.readEnum(buf, AllowShootingConfig.Mode.class);
            double allowShootingThreshold = buf.readDouble();
            boolean planetaryHandling = buf.readBoolean();
            OrbitalTransferPlanner.RoutePriority routePriority = PacketUtil.readEnum(buf, OrbitalTransferPlanner.RoutePriority.class);
            boolean minerCopySettings = buf.readBoolean();
            modules.add(
                new ModuleSyncData(
                    kind,
                    status,
                    progress,
                    minerBlacklist,
                    allowShootingMode,
                    allowShootingThreshold,
                    planetaryHandling,
                    routePriority,
                    minerCopySettings));
        }

        int invCount = buf.readInt();
        inventory = new LinkedHashMap<>(invCount);
        for (int i = 0; i < invCount; i++) {
            inventory.put(PacketUtil.readString(buf), buf.readLong());
        }

        int logCount = buf.readInt();
        logisticsConfig = new LinkedHashMap<>(logCount);
        for (int i = 0; i < logCount; i++) {
            logisticsConfig.put(
                PacketUtil.readString(buf),
                new LogisticsConfigSyncData(buf.readInt(), buf.readInt(), buf.readBoolean(), buf.readBoolean()));
        }
    }

    public static final class Handler implements IMessageHandler<OutpostFullSyncPacket, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(OutpostFullSyncPacket packet, MessageContext ctx) {
            Minecraft.getMinecraft()
                .func_152344_a(() -> {
                    AutomatedOutpost state = OutpostDataStore.get()
                        .getByAssetId(packet.assetId);
                    if (state == null) {
                        state = new AutomatedOutpost(packet.assetId, packet.teamId, packet.celestialBodyId);
                        OutpostDataStore.get()
                            .put(state);
                    }
                    state.setEnergyStored(packet.energyStored);

                    // Rebuild modules: clear and re-add from server snapshot.
                    // TODO: if AutomatedOutpostModule ever gains client-side transient state (animations,
                    // selection, etc.), do NOT store it on the module object — keep it in the widget layer
                    // keyed by (assetId, moduleIndex, moduleKind). That way this clear+rebuild remains safe.
                    state.clearModules();
                    for (ModuleSyncData md : packet.modules) {
                        AutomatedOutpostModule m = createModuleData(md);
                        m.updateStatus(md.status());
                        state.addModule(m);
                    }

                    // Sync inventory
                    Map<ItemStackWrapper, Long> invSnapshot = new LinkedHashMap<>();
                    for (Map.Entry<String, Long> e : packet.inventory.entrySet()) {
                        ItemStackWrapper key = ItemStackWrapper.fromKey(e.getKey());
                        if (key != null) invSnapshot.put(key, e.getValue());
                    }
                    state.inventory.loadFromSnapshot(invSnapshot);

                    // Sync logistics
                    Map<ItemStackWrapper, LogisticsResourceConfig> logSnapshot = new LinkedHashMap<>();
                    for (Map.Entry<String, LogisticsConfigSyncData> e : packet.logisticsConfig.entrySet()) {
                        ItemStackWrapper key = ItemStackWrapper.fromKey(e.getKey());
                        if (key != null) {
                            LogisticsConfigSyncData d = e.getValue();
                            logSnapshot.put(
                                key,
                                new LogisticsResourceConfig(
                                    d.minReserve,
                                    d.orderSize,
                                    d.isImportEnabled,
                                    d.isSupplyEnabled));
                        }
                    }
                    state.logisticsConfig.loadFromSnapshot(logSnapshot);
                    state.bumpSyncRevision();
                });
            return null;
        }
    }

    @Desugar
    private static record ModuleSyncData(OutpostModuleKind kind, AutomatedOutpostModule.Status status, float progress, List<String> minerBlacklist,
        AllowShootingConfig.Mode allowShootingMode, double allowShootingThreshold, boolean planetaryHandling, OrbitalTransferPlanner.RoutePriority routePriority,
        boolean minerCopySettings) {}

    @Desugar
    private static record LogisticsConfigSyncData(int minReserve, int orderSize, boolean isImportEnabled,
        boolean isSupplyEnabled) {}

    private static AutomatedOutpostModule createModuleData(ModuleSyncData syncData) {
        return switch (syncData.kind()) {
            case HAMMER -> new ModuleHammer(parseAllowShooting(syncData), parseRoutePriority(syncData));
            case BIG_HAMMER -> new ModuleBigHammer(
                syncData.planetaryHandling(),
                parseAllowShooting(syncData),
                parseRoutePriority(syncData));
            case MINER -> new ModuleMiner(syncData.minerBlacklist())
                .withCopySettingsToOtherMiners(syncData.minerCopySettings());
            case POWER -> new ModulePower();
        };
    }

    private static AllowShootingConfig parseAllowShooting(ModuleSyncData d) {
        return new AllowShootingConfig(d.allowShootingMode(), d.allowShootingThreshold());
    }

    private static OrbitalTransferPlanner.RoutePriority parseRoutePriority(ModuleSyncData d) {
        return d.routePriority() != null ? d.routePriority() : OrbitalTransferPlanner.RoutePriority.PRIORITIZE_TOF;
    }
}
