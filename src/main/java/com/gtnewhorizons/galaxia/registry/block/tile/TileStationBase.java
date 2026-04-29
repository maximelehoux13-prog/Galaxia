package com.gtnewhorizons.galaxia.registry.block.tile;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.cleanroommc.modularui.api.IGuiHolder;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizons.galaxia.api.GalaxiaCelestialAPI;
import com.gtnewhorizons.galaxia.compat.structure.util.LocalCoord;
import com.gtnewhorizons.galaxia.registry.block.BlockPos;
import com.gtnewhorizons.galaxia.registry.block.GalaxiaBlocksEnum;
import com.gtnewhorizons.galaxia.registry.block.GalaxiaMultiblockBase;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;

public abstract class TileStationBase<T extends GalaxiaMultiblockBase<T>> extends GalaxiaMultiblockBase<T>
    implements IGuiHolder<PosGuiData> {

    public static final List<Block> BASE_VALID_BLOCKS = List.of(
        GalaxiaBlocksEnum.SPACE_STATION_BLOCK.get(),
        GalaxiaBlocksEnum.SPACE_STATION_PANEL.get(),
        GalaxiaBlocksEnum.SPACE_STATION_GLASS.get());

    protected List<BlockPos> airlocks = new ArrayList<>();
    protected BlockPos here;

    @Override
    public Block getControllerBlock() {
        return GalaxiaBlocksEnum.STATION_CONTROLLER.get();
    }

    @Override
    protected boolean checkStructure() {
        return isValidDimension(worldObj) && super.checkStructure();
    }

    @Override
    public void onStructureFormed() {
        super.onStructureFormed();
        this.here = new BlockPos(xCoord, yCoord, zCoord);

        for (BlockPos airlock : airlocks) {
            if (!(worldObj.getTileEntity(airlock.x(), airlock.y(), airlock.z()) instanceof TileEntityAirlock teLock))
                continue;

            teLock.trackStationController(this.here);
        }
    }

    @Override
    public void onStructureDisformed() {
        super.onStructureDisformed();
        for (BlockPos airlock : airlocks) {
            if (!(worldObj.getTileEntity(airlock.x(), airlock.y(), airlock.z()) instanceof TileEntityAirlock teLock))
                continue;

            teLock.untrackStationController(this.here);
        }
        airlocks.clear();
    }

    public boolean isValidDimension(World world) {
        CelestialObjectId objectId = GalaxiaCelestialAPI.getObjectFromDimension(world.provider.dimensionId);
        return objectId != CelestialObjectId.INVALID;
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
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

    public abstract boolean tryRebuildMonitorGraph();

    @Override
    public void invalidate() {
        super.invalidate();
        for (BlockPos b : airlocks) {
            TileEntityAirlock airlock = b.getTE(worldObj);
            if (airlock == null) return;

            airlock.untrackStationController(this.here);
        }
    }

    public boolean isInside(int x, int y, int z) {
        boolean top = false, bottom = false;
        for (int d = 1; d <= LocalCoord.SEARCH_RADIUS; d++) {
            if (getStructureDefinition().isContainedInStructure("main", x, y + d, z)) top = true;
            if (getStructureDefinition().isContainedInStructure("main", x, y - d, z)) bottom = true;
            if (top && bottom) return true;
        }

        return false;
    }
}
