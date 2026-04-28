package com.gtnewhorizons.galaxia.registry.block;

import net.minecraft.block.Block;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizon.structurelib.structure.IStructureDefinition;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

public abstract class GalaxiaArbitraryShape<T extends GalaxiaArbitraryShape<T>> extends GalaxiaMultiblockBase<T> {

    public static final int SEARCH_RADIUS = 16;
    public static final int MAX_BLOCKS = SEARCH_RADIUS * SEARCH_RADIUS * SEARCH_RADIUS;

    protected final IntSet structureBlocks = LocalCoord.newBlockSet();
    protected int volume = -1;

    protected abstract boolean isValidBoundaryBlock(Block b);

    protected abstract boolean isValidDimension(World world);

    protected abstract ForgeDirection getPlacedFacing();

    @Override
    protected boolean checkStructure() {
        if (worldObj == null || worldObj.isRemote) return structureValid;
        if (fastRevalidate()) return true;

        World world = this.worldObj;
        if (!isValidDimension(world)) return false;

        IntSet validBoundary = floodStructure(world);
        boolean enclosed = checkEnclosed(world, validBoundary, getPlacedFacing());
        if (enclosed != structureValid) {
            structureValid = enclosed;
            if (enclosed) onStructureFormed();
            else onStructureDisformed();
            markDirty();
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        }
        return enclosed;
    }

    public boolean fastRevalidate() {
        if (!structureValid || structureBlocks.isEmpty()) return false;
        if (worldObj == null || worldObj.isRemote) return true;

        for (int packed : structureBlocks) {
            int wx = LocalCoord.worldX(LocalCoord.unpackX(packed), xCoord);
            int wy = LocalCoord.worldY(LocalCoord.unpackSignedY(packed), yCoord);
            int wz = LocalCoord.worldZ(LocalCoord.unpackZ(packed), zCoord);
            Block b = worldObj.getBlock(wx, wy, wz);
            if (!isValidBoundaryBlock(b)) {
                return false;
            }
        }
        return true;
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
    public IStructureDefinition<T> getStructureDefinition() {
        return null;
    }

    protected IntSet floodStructure(World world) {
        IntQueue floodBFS = new IntQueue();
        final IntSet connectedStructure = LocalCoord.newBlockSet();

        int start = LocalCoord.pack(0, 0, 0);
        floodBFS.enqueue(start);
        connectedStructure.add(start);

        while (!floodBFS.isEmpty()) {
            int cur = floodBFS.dequeue();
            int lx = LocalCoord.unpackX(cur);
            int ly = LocalCoord.unpackY(cur);
            int lz = LocalCoord.unpackZ(cur);

            for (ForgeDirection d : ForgeDirection.VALID_DIRECTIONS) {
                int nlx = lx + d.offsetX;
                int nly = ly + d.offsetY;
                int nlz = lz + d.offsetZ;

                if (!LocalCoord.isInBounds(nlx, nly, nlz)) {
                    continue;
                }

                int np = LocalCoord.pack(nlx, nly, nlz);

                int wx = LocalCoord.worldX(nlx, xCoord);
                int wy = LocalCoord.worldY(nly, yCoord);
                int wz = LocalCoord.worldZ(nlz, zCoord);

                Block b = world.getBlock(wx, wy, wz);

                if (!isValidBoundaryBlock(b)) {
                    continue;
                }

                if (connectedStructure.add(np)) {
                    floodBFS.enqueue(np);
                }
            }
        }

        return connectedStructure;
    }

    protected boolean checkEnclosed(World world, IntSet validBoundary, ForgeDirection placedFacing) {
        IntQueue bfs = new IntQueue();
        IntSet visited = LocalCoord.newBlockSet();
        IntSet localStructureBlocks = LocalCoord.newBlockSet();

        int start = LocalCoord.pack(placedFacing.offsetX, placedFacing.offsetY, placedFacing.offsetZ);

        bfs.enqueue(start);
        visited.add(start);

        int checked = 0;
        while (!bfs.isEmpty() && checked++ < MAX_BLOCKS) {
            int cur = bfs.dequeue();
            int lx = LocalCoord.unpackX(cur);
            int ly = LocalCoord.unpackY(cur);
            int lz = LocalCoord.unpackZ(cur);

            for (ForgeDirection d : ForgeDirection.VALID_DIRECTIONS) {
                int nlx = lx + d.offsetX;
                int nly = ly + d.offsetY;
                int nlz = lz + d.offsetZ;

                if (!LocalCoord.isInBounds(nlx, nly, nlz)) {
                    return false;
                }

                int np = LocalCoord.pack(nlx, nly, nlz);

                int nwx = LocalCoord.worldX(nlx, xCoord);
                int nwy = LocalCoord.worldY(nly, yCoord);
                int nwz = LocalCoord.worldZ(nlz, zCoord);

                Block b = world.getBlock(nwx, nwy, nwz);
                if (!b.isAir(world, nwx, nwy, nwz)) {
                    // No need to check if the block is valid since only valid blocks are kept in the boundary
                    if (validBoundary.contains(np)) {
                        localStructureBlocks.add(np);

                        boolean valid = checkDefinition(world, b, nwx, nwy, nwz);
                        if (!valid) return false;
                    }
                    continue;
                }

                if (visited.add(np)) {
                    bfs.enqueue(np);
                }
            }
        }

        if (!localStructureBlocks.isEmpty() && localStructureBlocks.size() >= 6) {
             this.volume = visited.size();
             return true;
        }
        return false;
    }

    protected abstract boolean checkDefinition(World world, Block block, int x, int y, int z);

    public boolean isInside(int x, int y, int z) {
        int packed = LocalCoord.packFromWorld(x, y, z, xCoord, yCoord, zCoord);
        int lx = LocalCoord.unpackX(packed);
        int ly = LocalCoord.unpackY(packed);
        int lz = LocalCoord.unpackZ(packed);

        boolean top = false, bottom = false;

        for (int d = 1; d <= SEARCH_RADIUS; d++) {
            if (structureBlocks.contains(LocalCoord.pack(lx, ly + d, lz))) top = true;
            if (structureBlocks.contains(LocalCoord.pack(lx, ly - d, lz))) bottom = true;
            if (top && bottom) return true;
        }

        return false;
    }

    public static final class LocalCoord {

        private static int offset(int v) {
            return v + SEARCH_RADIUS;
        }

        private static int unoffset(int v) {
            return v - SEARCH_RADIUS;
        }

        public static int pack(int x, int y, int z) {
            return (offset(x) << 12) | (offset(y) << 6) | offset(z);
        }

        public static int packFromWorld(int wx, int wy, int wz, int xCoord, int yCoord, int zCoord) {
            return pack(wx - xCoord, wy - yCoord, wz - zCoord);
        }

        public static int unpackX(int v) {
            return unoffset((v >> 12) & 63);
        }

        public static int unpackSignedY(int v) {
            return unoffset((v >> 6) & 63);
        }

        public static int unpackY(int v) {
            return unoffset((v >> 6) & 63);
        }

        public static int unpackZ(int v) {
            return unoffset(v & 63);
        }

        public static int worldX(int localX, int xCoord) {
            return localX + xCoord;
        }

        public static int worldY(int localY, int yCoord) {
            return localY + yCoord;
        }

        public static int worldZ(int localZ, int zCoord) {
            return localZ + zCoord;
        }

        public static boolean isInBounds(int x, int y, int z) {
            return x >= -SEARCH_RADIUS && x <= SEARCH_RADIUS
                && y >= -SEARCH_RADIUS
                && y <= SEARCH_RADIUS
                && z >= -SEARCH_RADIUS
                && z <= SEARCH_RADIUS;
        }

        public static IntSet newBlockSet() {
            return new IntOpenHashSet();
        }
    }

    public static final class IntQueue {

        private static final int INITIAL_QUEUE_SIZE = 4096;

        private int[] queue = new int[INITIAL_QUEUE_SIZE];
        private int head;
        private int tail;

        public void enqueue(int v) {
            if (tail == queue.length) {
                queue = java.util.Arrays.copyOf(queue, queue.length * 2);
            }
            queue[tail++] = v;
        }

        public int dequeue() {
            return queue[head++];
        }

        public boolean isEmpty() {
            return head >= tail;
        }

        public void reset() {
            head = 0;
            tail = 0;
        }

        public void clear() {
            reset();
            queue = new int[INITIAL_QUEUE_SIZE];
        }
    }
}
