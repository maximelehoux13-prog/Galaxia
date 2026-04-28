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
import com.gtnewhorizons.galaxia.registry.block.BlockPos;
import com.gtnewhorizons.galaxia.registry.block.GalaxiaArbitraryShape;
import com.gtnewhorizons.galaxia.registry.block.GalaxiaBlocksEnum;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;

public abstract class TileStationBase extends GalaxiaArbitraryShape<TileStationController>
    implements IGuiHolder<PosGuiData> {

    public static final List<Block> BASE_VALID_BLOCKS = new ArrayList<>();
    static {
        BASE_VALID_BLOCKS.addAll(
            List.of(
                GalaxiaBlocksEnum.SPACE_STATION_BLOCK.get(),
                GalaxiaBlocksEnum.SPACE_STATION_PANEL.get(),
                GalaxiaBlocksEnum.SPACE_STATION_GLASS.get()));
        BASE_VALID_BLOCKS.addAll(TileEntityAirlock.VALID_BLOCKS);
    }

    protected ForgeDirection placedFacing = ForgeDirection.NORTH;
    protected List<BlockPos> airlocks = List.of();
    protected BlockPos here;

    @Override
    public Block getControllerBlock() {
        return GalaxiaBlocksEnum.STATION_CONTROLLER.get();
    }

    @Override
    public void construct(ItemStack trigger, boolean hintsOnly) {}

    @Override
    public int survivalConstruct(ItemStack trigger, int elementBudget, ISurvivalBuildEnvironment env) {
        return -2;
    }

    @Override
    protected void onStructureFormed() {
        this.here = new BlockPos(xCoord, yCoord, zCoord);

        for (BlockPos airlock : airlocks) {
            if (!(worldObj.getTileEntity(airlock.x(), airlock.y(), airlock.z()) instanceof TileEntityAirlock teLock))
                continue;

            teLock.trackStationController(this.here);
        }
    }

    @Override
    protected void onStructureDisformed() {
        for (BlockPos airlock : airlocks) {
            if (!(worldObj.getTileEntity(airlock.x(), airlock.y(), airlock.z()) instanceof TileEntityAirlock teLock))
                continue;

            teLock.untrackStationController(this.here);
        }
        airlocks.clear();
    }

    @Override
    protected abstract boolean isValidBoundaryBlock(Block b);

    @Override
    protected boolean isValidDimension(World world) {
        CelestialObjectId objectId = GalaxiaCelestialAPI.getObjectFromDimension(world.provider.dimensionId);
        return objectId != CelestialObjectId.INVALID;
    }

    @Override
    public ForgeDirection getPlacedFacing() {
        return placedFacing;
    }

    @Override
    protected boolean checkDefinition(World world, Block block, int x, int y, int z) {
        if (block == GalaxiaBlocksEnum.AIRLOCK_CONTROLLER.get()) {
            if (world.getTileEntity(x, y, z) instanceof TileEntityAirlock airlock) {
                if (!airlock.isStructureValid()) return false;

                BlockPos airlockPos = new BlockPos(x, y, z);
                if (!airlocks.contains(airlockPos)) {
                    airlocks.add(airlockPos);
                }

                return true;
            }
            return false;
        }
        return true;
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
}
