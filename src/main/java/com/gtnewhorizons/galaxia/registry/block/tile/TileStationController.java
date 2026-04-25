package com.gtnewhorizons.galaxia.registry.block.tile;

import java.util.UUID;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.cleanroommc.modularui.api.IGuiHolder;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.InteractionSyncHandler;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizons.galaxia.api.GalaxiaCelestialAPI;
import com.gtnewhorizons.galaxia.registry.block.BlockPos;
import com.gtnewhorizons.galaxia.registry.block.GalaxiaArbitraryShape;
import com.gtnewhorizons.galaxia.registry.block.GalaxiaBlocksEnum;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.outpost.Station;

public class TileStationController extends GalaxiaArbitraryShape<TileStationController>
    implements IGuiHolder<PosGuiData> {

    private UUID owner;
    private CelestialAsset.ID backingStation;

    private ForgeDirection placedFacing = ForgeDirection.NORTH;

    @Override
    public Block getControllerBlock() {
        return GalaxiaBlocksEnum.MODULE_CONTROLLER.get();
    }

    @SuppressWarnings("unchecked")
    @Override
    public void construct(ItemStack trigger, boolean hintsOnly) {}

    @Override
    public int survivalConstruct(ItemStack trigger, int elementBudget, ISurvivalBuildEnvironment env) {
        return -2;
    }

    @Override
    protected void onStructureFormed() {
        CelestialObjectId objectId = GalaxiaCelestialAPI.getObjectFromDimension(this.worldObj.provider.dimensionId);

        Station station = (Station) CelestialAsset.create(objectId, CelestialAsset.Kind.STATION, true);
        station.setController(new BlockPos(xCoord, yCoord, zCoord));
        backingStation = station.assetId;
        CelestialAssetStore.registerAsset(owner, station);
    }

    @Override
    protected boolean isValidBoundaryBlock(Block b) {
        return b == GalaxiaBlocksEnum.SPACE_STATION_BLOCK.get() || b == GalaxiaBlocksEnum.MODULE_CONTROLLER.get();
    }

    @Override
    protected boolean isValidDimension(World world) {
        CelestialObjectId objectId = GalaxiaCelestialAPI.getObjectFromDimension(world.provider.dimensionId);
        return objectId != CelestialObjectId.INVALID;
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
    public ForgeDirection getPlacedFacing() {
        return placedFacing;
    }

    public void setPlacedFacing(ForgeDirection dir) {
        placedFacing = dir;
    }

    public ExtendedFacing getCurrentFacing() {
        return currentFacing;
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        if (owner != null) {
            nbt.setLong("ownerMost", owner.getMostSignificantBits());
            nbt.setLong("ownerLeast", owner.getLeastSignificantBits());
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        if (nbt.hasKey("ownerMost") && nbt.hasKey("ownerLeast")) {
            owner = new UUID(nbt.getLong("ownerMost"), nbt.getLong("ownerLeast"));
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

}
