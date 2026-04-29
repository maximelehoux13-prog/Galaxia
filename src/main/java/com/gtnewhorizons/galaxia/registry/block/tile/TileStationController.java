package com.gtnewhorizons.galaxia.registry.block.tile;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.InteractionSyncHandler;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.StructureUtility;
import com.gtnewhorizons.galaxia.api.GalaxiaCelestialAPI;
import com.gtnewhorizons.galaxia.compat.GalaxiaStructureUtility;
import com.gtnewhorizons.galaxia.compat.structure.ArbitraryShapeDefinition;
import com.gtnewhorizons.galaxia.compat.structure.ArbitraryShapeTile;
import com.gtnewhorizons.galaxia.registry.block.BlockPos;
import com.gtnewhorizons.galaxia.registry.block.GalaxiaBlocksEnum;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.outpost.Station;

public class TileStationController extends TileStationBase<TileStationController>
    implements ArbitraryShapeTile<TileStationController> {

    private UUID owner;
    private CelestialAsset.ID backingStation;

    private final List<BlockPos> monitors = new ArrayList<>();

    public final ArbitraryShapeDefinition<TileStationController> STRUCTURE_DEFINITION = ArbitraryShapeDefinition
        .<TileStationController>builder()
        .addControllerBlock(GalaxiaBlocksEnum.STATION_CONTROLLER.get())
        .addElements(
            BASE_VALID_BLOCKS.stream()
                .map(b -> StructureUtility.ofBlock(b, 0)))
        .addElement(GalaxiaStructureUtility.ofTileAdderCheckHints((_, tileEntity) -> {
            if (tileEntity instanceof TileEntityAirlock airlock) {
                if (!airlock.isStructureValid()) return false;

                BlockPos airlockPos = new BlockPos(airlock.xCoord, airlock.yCoord, airlock.zCoord);
                if (!this.airlocks.contains(airlockPos)) {
                    this.airlocks.add(airlockPos);
                }
                return true;
            }
            return false;
        }, GalaxiaBlocksEnum.AIRLOCK_CONTROLLER.get(), 0))
        .embedDefinition(TileEntityAirlock.STRUCTURE_PIECE_MAIN, TileEntityAirlock.STRUCTURE_DEFINITION)
        .build();

    @Override
    public void onStructureFormed() {
        super.onStructureFormed();

        tryRebuildMonitorGraph();
        // Avoid registering potentially duplicate station on reload
        if (backingStation == null) {
            CelestialObjectId objectId = GalaxiaCelestialAPI.getObjectFromDimension(this.worldObj.provider.dimensionId);
            Station station = (Station) CelestialAsset.create(objectId, CelestialAsset.Kind.STATION, true);
            station.setController(this.here);
            backingStation = station.assetId;

            CelestialAssetStore.registerAsset(owner, station);
        } else {
            CelestialAssetStore.enableAsset(backingStation);
        }
    }

    @Override
    public boolean tryRebuildMonitorGraph() {
        List<BlockPos> newMonitors = new ArrayList<>();
        for (BlockPos pos : airlocks) {
            TileEntityAirlock airlock = pos.getTE(worldObj);
            if (airlock == null) continue;

            airlock.collectGraph(this, newMonitors);
        }

        if (!newMonitors.isEmpty()) {
            monitors.clear();
            monitors.addAll(newMonitors);
            return true;
        }
        return false;
    }

    @Override
    public void onStructureDisformed() {
        super.onStructureDisformed();
        if (backingStation != null) {
            CelestialAssetStore.disableAsset(backingStation);
        }
        monitors.clear();
    }

    @Override
    public int getVolume() {
        int own = ArbitraryShapeTile.super.getVolume();
        return monitors.stream()
            .map(pos -> (TileStationMonitor) pos.getTE(worldObj))
            .filter(Objects::nonNull)
            .mapToInt(ArbitraryShapeTile::getVolume)
            .sum() + own;
    }

    @Override
    public ForgeDirection getPlacedFacing() {
        return placedFacing;
    }

    @Override
    public boolean isStructureValid() {
        return structureValid;
    }

    @Override
    public IStructureDefinition<TileStationController> getStructureDefinition() {
        return STRUCTURE_DEFINITION;
    }

    @Override
    protected int getControllerOffsetX() {
        return 0;
    }

    @Override
    protected int getControllerOffsetY() {
        return 0;
    }

    @Override
    protected int getControllerOffsetZ() {
        return 0;
    }

    @Override
    public ModularPanel buildUI(PosGuiData data, PanelSyncManager syncManager, UISettings settings) {
        if (!worldObj.isRemote) {
            markStructureDirty();
        }
        return new ModularPanel("galaxia:station_controller").size(210, 130)
            .child(
                IKey.str(StatCollector.translateToLocal("galaxia.gui.station_controller.title"))
                    .asWidget()
                    .pos(8, 8))
            .child(
                new ButtonWidget<>().size(190, 30)
                    .pos(10, 85)
                    .overlay(
                        IKey.str(
                            (structureValid ? EnumChatFormatting.GREEN : EnumChatFormatting.RED)
                                + StatCollector.translateToLocal("galaxia.gui.station_controller.status")
                                + EnumChatFormatting.RESET))
                    .syncHandler(new InteractionSyncHandler().setOnMousePressed(mouseData -> {
                        if (mouseData.mouseButton != 0 || worldObj.isRemote) return;
                        markStructureDirty();
                        for (BlockPos b : monitors) {
                            TileStationMonitor monitor = (TileStationMonitor) b.getTE(worldObj);
                            if (monitor != null) {
                                System.out.println(monitor.here);
                            }
                        }
                    })));
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        if (owner != null) {
            nbt.setLong("ownerMost", owner.getMostSignificantBits());
            nbt.setLong("ownerLeast", owner.getLeastSignificantBits());
        }
        if (backingStation != null) {
            nbt.setLong(
                "backingStationMost",
                backingStation.id()
                    .getMostSignificantBits());
            nbt.setLong(
                "backingStationLeast",
                backingStation.id()
                    .getLeastSignificantBits());
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        if (nbt.hasKey("ownerMost") && nbt.hasKey("ownerLeast")) {
            owner = new UUID(nbt.getLong("ownerMost"), nbt.getLong("ownerLeast"));
        }

        if (nbt.hasKey("backingStationMost") && nbt.hasKey("backingStationLeast")) {
            backingStation = CelestialAsset.ID
                .from(new UUID(nbt.getLong("backingStationMost"), nbt.getLong("backingStationLeast")));
        }
    }

    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound nbt = new NBTTagCompound();
        this.writeToNBT(nbt);
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 0, nbt);
    }

    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity pkt) {
        this.readFromNBT(pkt.func_148857_g());
    }

    public UUID getOwner() {
        return owner;
    }

    public void setOwner(UUID owner) {
        this.owner = owner;
    }

    public CelestialAsset.ID getBackingStation() {
        return backingStation;
    }

    @Override
    public void invalidate() {
        super.invalidate();
        if (backingStation != null) {
            if (isChunkUnloading) {
                CelestialAssetStore.disableAsset(backingStation);
            } else {
                CelestialAssetStore.destroyAsset(backingStation);
            }
        }
    }
}
