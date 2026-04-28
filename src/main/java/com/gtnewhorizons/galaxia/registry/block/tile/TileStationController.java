package com.gtnewhorizons.galaxia.registry.block.tile;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.block.Block;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.InteractionSyncHandler;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.gtnewhorizons.galaxia.api.GalaxiaCelestialAPI;
import com.gtnewhorizons.galaxia.registry.block.GalaxiaBlocksEnum;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.outpost.Station;

public class TileStationController extends TileStationBase {

    private static final List<Block> VALID_BLOCKS = new ArrayList<>();
    static {
        VALID_BLOCKS.addAll(TileStationBase.BASE_VALID_BLOCKS);
        VALID_BLOCKS.add(GalaxiaBlocksEnum.STATION_CONTROLLER.get());
    }

    private UUID owner;
    private CelestialAsset.ID backingStation;

    @Override
    protected void onStructureFormed() {
        super.onStructureFormed();
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
    protected void onStructureDisformed() {
        super.onStructureDisformed();
        if (backingStation != null) {
            CelestialAssetStore.disableAsset(backingStation);
        }
    }

    @Override
    protected boolean isValidBoundaryBlock(Block b) {
        return VALID_BLOCKS.contains(b);
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

    public void unregisterStation() {
        if (this.backingStation == null) return;
        CelestialAssetStore.destroyAsset(this.backingStation);
    }

    public CelestialAsset.ID getBackingStation() {
        return backingStation;
    }
}
