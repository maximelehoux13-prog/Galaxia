package com.gtnewhorizons.galaxia.vaporchamber;

import java.util.*;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;

import org.apache.commons.lang3.ArrayUtils;

import com.gtnewhorizon.gtnhlib.blockpos.BlockPos;

public class TileEntityVaporChamberController extends TileEntity {

    private int ticks = 0;
    public EulerianSimAPI grid;

    @Override
    public void updateEntity() {
        // final Tessellator tess = Tessellator.instance;
        // tess.startDrawing(GL11.GL_LINE_STRIP);
        //
        // tess.addVertex(10, 10, 10);
        // tess.addVertex(10, 11, 11);
        //
        // tess.draw();
        ticks = ticks + 1;
        if (worldObj.isRemote && ticks == 20) {
            genGrid();
        }

        if (ticks > 20 && ticks % 20 == 0) {
            grid.run();
        }

        if (ticks % 5 == 0 && grid != null) {
            byte[][][] cells = grid.getCells();
            for (int x = 0; x < cells.length; x++) {
                for (int y = 0; y < cells[x].length; y++) {
                    for (int z = 0; z < cells[x][y].length; z++) {
                        if ((cells[x][y][z] & (byte) 0b00000001) != 0) {
                            // worldObj.spawnParticle("crit", x, y + 10, z, 0, 0, 0);

                            float t = 3f;
                            float eps = 0.18f;

                            if (grid.u[x][y][z] > eps || grid.u[x + 1][y][z] > eps
                                || grid.v[x][y][z] > eps
                                || grid.v[x][y + 1][z] > eps
                                || grid.w[x][y][z] > eps
                                || grid.w[x][y][z + 1] > eps
                                || grid.u[x][y][z] < -eps
                                || grid.u[x + 1][y][z] < -eps
                                || grid.v[x][y][z] < -eps
                                || grid.v[x][y + 1][z] < -eps
                                || grid.w[x][y][z] < -eps
                                || grid.w[x][y][z + 1] < -eps) {
                                worldObj.spawnParticle(
                                    "reddust",
                                    x - 0.5,
                                    y + 10,
                                    z,
                                    1,
                                    Math.max(0f, grid.u[x][y][z]) * t,
                                    Math.min(0f, grid.u[x][y][z]) * t);
                                worldObj.spawnParticle(
                                    "reddust",
                                    x + 0.5,
                                    y + 10,
                                    z,
                                    1,
                                    Math.max(0f, grid.u[x + 1][y][z]) * t,
                                    Math.min(0f, grid.u[x + 1][y][z]) * t);

                                worldObj.spawnParticle(
                                    "reddust",
                                    x,
                                    y + 9.5,
                                    z,
                                    1,
                                    Math.max(0f, grid.v[x][y][z]) * t,
                                    Math.min(0f, grid.v[x][y][z]) * t);
                                worldObj.spawnParticle(
                                    "reddust",
                                    x,
                                    y + 10.5,
                                    z,
                                    1,
                                    Math.max(0f, grid.v[x][y + 1][z]) * t,
                                    Math.min(0f, grid.v[x][y + 1][z]) * t);

                                worldObj.spawnParticle(
                                    "reddust",
                                    x,
                                    y + 10,
                                    z - 0.5,
                                    1,
                                    Math.max(0f, grid.w[x][y][z]) * t,
                                    Math.min(0f, grid.w[x][y][z]) * t);
                                worldObj.spawnParticle(
                                    "reddust",
                                    x,
                                    y + 10,
                                    z + 0.5,
                                    1,
                                    Math.max(0f, grid.w[x][y][z + 1]) * t,
                                    Math.min(0f, grid.w[x][y][z + 1]) * t);
                            }
                        }
                    }
                }
            }
        }

        // if (ticks % 5 == 0) {
        // for (BlockPos pos : outline) {
        // worldObj.spawnParticle("crit", pos.x + 0.5, pos.y + 0.5, pos.z + 0.5, 0, 0, 0);
        // }
        // }
        // if ((ticks + 5) % 5 == 0) {
        // for (BlockPos pos : structure) {
        //
        // worldObj.spawnParticle("magicCrit", pos.x + 0.5, pos.y + 0.5, pos.z + 0.5, 0, 0, 0);
        // }
        // }
    }

    private Set<BlockPos> outline = new HashSet<>();
    private Set<BlockPos> structure = new HashSet<>();
    private Set<TileEntityVaporChamber> visited = new HashSet<>();

    public void genGrid() {
        if (worldObj.getTileEntity(xCoord, yCoord - 1, zCoord) instanceof TileEntityVaporChamber tevc) {
            if (!worldObj.isRemote) this.bfs(tevc);

            BlockPos pos = new ArrayList<>(structure).get(0);
            grid = EulerianSimAPI.createGrid(new ArrayList<>(structure), new ArrayList<>(outline), pos);
        }
    }

    public void bfs(TileEntityVaporChamber start) {
        Queue<TileEntityVaporChamber> queue = new LinkedList<>();
        visited.clear();
        outline.clear();
        structure.clear();

        queue.add(start);
        visited.add(start);
        outline.addAll(start.notNeighbours);

        while (!queue.isEmpty()) {
            TileEntityVaporChamber current = queue.remove();
            for (TileEntityVaporChamber neighbour : current.neighbours) {
                if (!visited.contains(neighbour)) {
                    visited.add(neighbour);
                    queue.add(neighbour);
                    outline.addAll(neighbour.notNeighbours);
                }
            }
        }

        int[] total = new int[] {};
        for (TileEntityVaporChamber teev : visited) {
            int[] array = { teev.xCoord, teev.yCoord, teev.zCoord };
            total = ArrayUtils.addAll(total, array);
        }

        int[] array = new int[] {};
        for (int integer : total) {
            array = ArrayUtils.add(array, integer);
            if (array.length == 3) {
                structure.add(new BlockPos(array[0], array[1], array[2]));
                array = new int[] {};
            }
        }
    }

    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound tag = new NBTTagCompound();
        this.writeToNBT(tag);
        int[] total = {};
        for (BlockPos pos : outline) {
            int[] array = { pos.x, pos.y, pos.z };
            total = ArrayUtils.addAll(total, array);
        }

        tag.setIntArray("outline", total);

        total = new int[] {};
        for (TileEntityVaporChamber teev : visited) {
            int[] array = { teev.xCoord, teev.yCoord, teev.zCoord };
            total = ArrayUtils.addAll(total, array);
        }

        tag.setIntArray("structure", total);

        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 0, tag);
    }

    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity packet) {
        NBTTagCompound tag = packet.func_148857_g();

        int[] array = {};
        for (int integer : tag.getIntArray("outline")) {
            array = ArrayUtils.add(array, integer);
            if (array.length == 3) {
                outline.add(new BlockPos(array[0], array[1], array[2]));
                array = new int[] {};
            }
        }

        array = new int[] {};
        for (int integer : tag.getIntArray("structure")) {
            array = ArrayUtils.add(array, integer);
            if (array.length == 3) {
                structure.add(new BlockPos(array[0], array[1], array[2]));
                array = new int[] {};
            }
        }
    }
}
