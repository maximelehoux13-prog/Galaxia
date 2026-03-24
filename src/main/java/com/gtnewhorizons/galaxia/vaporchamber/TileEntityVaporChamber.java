package com.gtnewhorizons.galaxia.vaporchamber;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.gtnewhorizon.gtnhlib.blockpos.BlockPos;
import com.gtnewhorizons.galaxia.registry.block.PlanetBlocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.Vec3;
import net.minecraftforge.common.util.Constants.NBT;

import org.apache.commons.lang3.ArrayUtils;

public class TileEntityVaporChamber extends TileEntity {
    public static final Vec3[] CHECK_OFFSETS = {
        Vec3.createVectorHelper(1, 0, 0), Vec3.createVectorHelper(-1, 0, 0),
        Vec3.createVectorHelper(0, 0, 1), Vec3.createVectorHelper(0, 0, -1),
        Vec3.createVectorHelper(0, 1, 0), Vec3.createVectorHelper(0, -1, 0)};

    private List<int[]> pendingNeighbourCoords = new ArrayList<>();

    List<TileEntityVaporChamber> neighbours = new ArrayList<>();
    public Set<BlockPos> notNeighbours = new HashSet<>();
    public List<Vec3> neighbourDirs = new ArrayList<>();

    private int refrigerantAmount = 1000;

    /**
     * Updates the tile entity.
     */
    @Override
    public void updateEntity() {
        if (worldObj.getBlock(xCoord, yCoord - 1, zCoord) == PlanetBlocks.THEIA_MAGMA) {
            if (worldObj.getBlockMetadata(xCoord, yCoord - 1, zCoord) == 1) {

            }
        }

        try {
            worldObj.playerEntities.get(0).addChatMessage(new ChatComponentText(Integer.toString(neighbours.size())));
        }
        catch (Exception e) {

        }

        markDirty();
    }

    /**
     * Gets the list of neighbours that count as valid neighbours to this one
     *
     * @return The list of valid neighbours
     */
    public List<TileEntityVaporChamber> getNeighbours() {
        return neighbours;
    }

    /**
     * Connects another gantry to this one and updates both
     *
     * @param other The other gantry to connect to
     */
    public void connect(TileEntityVaporChamber other) {
        this.neighbours.add(other);
        other.neighbours.add(this);
        this.updateNeighbourDirs();
        other.updateNeighbourDirs();
        this.markDirty();
        other.markDirty();
        if (worldObj != null) {
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
            worldObj.markBlockForUpdate(other.xCoord, other.yCoord, other.zCoord);
        }
    }

    /**
     * Update neighbour direction list
     */
    public void updateNeighbourDirs() {
        if (worldObj.isRemote) return;

        // Clear list and go through neighbours getting vectors
        neighbourDirs.clear();
        for (TileEntityVaporChamber neighbour : neighbours) {
            neighbourDirs.add(
                Vec3.createVectorHelper(
                    neighbour.xCoord - xCoord,
                    neighbour.yCoord - yCoord,
                    neighbour.zCoord - zCoord));
            notNeighbours.remove(new BlockPos(neighbour.xCoord, neighbour.yCoord, neighbour.zCoord));
        }

        markDirty();
        if (worldObj != null) {
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        }
        return;
    }

    /**
     * Disconnects the gantry from another one and updates both
     *
     * @param other The other gantry to disconnect from
     */
    public void disconnect(TileEntityVaporChamber other) {
        this.neighbours.remove(other);
        other.neighbours.remove(this);
        this.updateNeighbourDirs();
        other.updateNeighbourDirs();
        if (worldObj != null) {
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
            worldObj.markBlockForUpdate(other.xCoord, other.yCoord, other.zCoord);
        }

    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        NBTTagList neighbourList = new NBTTagList();
        for (TileEntityVaporChamber neighbour : neighbours) {
            NBTTagCompound neighbourTag = new NBTTagCompound();
            neighbourTag.setInteger("x", neighbour.xCoord);
            neighbourTag.setInteger("y", neighbour.yCoord);
            neighbourTag.setInteger("z", neighbour.zCoord);
            neighbourList.appendTag(neighbourTag);
        }
        tag.setTag("neighbours", neighbourList);

        NBTTagList dirList = new NBTTagList();
        for (Vec3 dir : neighbourDirs) {
            NBTTagCompound entry = new NBTTagCompound();
            entry.setDouble("x", dir.xCoord);
            entry.setDouble("y", dir.yCoord);
            entry.setDouble("z", dir.zCoord);
            dirList.appendTag(entry);
        }
        tag.setTag("neighbourDirs", dirList);

    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);

        NBTTagList neighbourList = tag.getTagList("neighbours", NBT.TAG_COMPOUND);
        pendingNeighbourCoords = new ArrayList<>();

        for (int i = 0; i < neighbourList.tagCount(); i++) {
            NBTTagCompound entry = neighbourList.getCompoundTagAt(i);
            pendingNeighbourCoords
                .add(new int[]{entry.getInteger("x"), entry.getInteger("y"), entry.getInteger("z"),});
        }

        neighbourDirs.clear();
        NBTTagList list = tag.getTagList("neighbourDirs", NBT.TAG_COMPOUND);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound entry = list.getCompoundTagAt(i);
            neighbourDirs
                .add(Vec3.createVectorHelper(entry.getDouble("x"), entry.getDouble("y"), entry.getDouble("z")));
        }
    }

    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound tag = new NBTTagCompound();
        this.writeToNBT(tag);

        int[] array = new int[]{};
        for (TileEntityVaporChamber neighbour : neighbours) {
            array = ArrayUtils.addAll(array, new int[]{neighbour.xCoord, neighbour.yCoord, neighbour.zCoord});
        }

        tag.setIntArray("neighbours", array);

        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 0, tag);
    }

    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity packet) {
        NBTTagCompound tag = packet.func_148857_g();

        int[] array = {};
        for (int integer : tag.getIntArray("neighbours")) {
            array = ArrayUtils.add(array, integer);
            if (array.length == 3) {
                if (worldObj.getTileEntity(array[0], array[1], array[2]) instanceof TileEntityVaporChamber teev) {
                    neighbours.add(teev);
                }
                array = new int[]{};
            }
        }
    }

    @Override
    public AxisAlignedBB getRenderBoundingBox() {
        return TileEntity.INFINITE_EXTENT_AABB;
    }

    @Override
    public double getMaxRenderDistanceSquared() {
        return 512 * 512;
    }
}

