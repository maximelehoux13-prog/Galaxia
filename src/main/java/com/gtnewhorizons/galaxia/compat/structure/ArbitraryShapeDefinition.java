package com.gtnewhorizons.galaxia.compat.structure;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import com.gtnewhorizons.galaxia.compat.GalaxiaStructureUtility;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.IStructureElement;
import com.gtnewhorizon.structurelib.structure.IStructureWalker;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.gtnewhorizon.structurelib.structure.StructureUtility;
import com.gtnewhorizons.galaxia.compat.structure.util.IntQueue;
import com.gtnewhorizons.galaxia.compat.structure.util.LocalCoord;
import com.gtnewhorizons.galaxia.core.Galaxia;

public class ArbitraryShapeDefinition<T extends TileEntity & ArbitraryShapeTile<T>> implements IStructureDefinition<T> {

    private static final int[] DIR_DX = { 0,  0, 0,  0, 1, -1 };
    private static final int[] DIR_DY = { 0,  0, 1, -1, 0,  0 };
    private static final int[] DIR_DZ = { 1, -1, 0,  0, 0,  0 };

    private static final class DenseBitSet {

        @FunctionalInterface
        interface CoordConsumer {
            void accept(int lx, int ly, int lz);
        }

        private final long[] words;
        private final int    radius;
        private final int    stride;
        private final int    strideSquared;
        private int          size;

        DenseBitSet(int radius) {
            this.radius        = radius;
            this.stride        = 2 * radius + 1;
            this.strideSquared = stride * stride;
            this.words         = new long[((strideSquared * stride) + 63) >>> 6];
        }

        private int index(int lx, int ly, int lz) {
            return (lx + radius) * strideSquared + (ly + radius) * stride + (lz + radius);
        }

        boolean add(int lx, int ly, int lz) {
            int  idx  = index(lx, ly, lz);
            int  w    = idx >>> 6;
            long mask = 1L << (idx & 63);
            if ((words[w] & mask) != 0L) return false;
            words[w] |= mask;
            size++;
            return true;
        }

        boolean contains(int lx, int ly, int lz) {
            int idx = index(lx, ly, lz);
            return (words[idx >>> 6] & (1L << (idx & 63))) != 0L;
        }

        int  size()    { return size; }
        boolean isEmpty() { return size == 0; }

        void forEach(CoordConsumer consumer) {
            final long[] w  = words;
            final int    ss = strideSquared;
            final int    st = stride;
            final int    r  = radius;
            for (int wi = 0; wi < w.length; wi++) {
                long word = w[wi];
                while (word != 0L) {
                    int bit = Long.numberOfTrailingZeros(word);
                    int idx = (wi << 6) | bit;
                    consumer.accept(idx / ss - r, (idx % ss) / st - r, idx % st - r);
                    word &= word - 1L;
                }
            }
        }

        void clear() {
            Arrays.fill(words, 0L);
            size = 0;
        }
    }

    private final int searchRadius;
    private T         tile;
    private int       volume;

    private final IStructureElement<T>[] structureElements;
    private final int                    numElements;

    private final DenseBitSet structureBlocks;
    private final DenseBitSet floodVisited;
    private final DenseBitSet validBoundaryBits;
    private final DenseBitSet enclosedVisited;

    /**
     * AABB of the valid boundary found by the most recent floodStructure call.
     * Used by checkEnclosed to fail immediately when an air cell escapes the
     * bounding box of the actual structure — far tighter than the full searchRadius.
     */
    private int aabbMinX, aabbMaxX;
    private int aabbMinY, aabbMaxY;
    private int aabbMinZ, aabbMaxZ;

    public static <T extends TileEntity & ArbitraryShapeTile<T>> Builder<T> builder() {
        return new Builder<>();
    }

    public int getVolume()       { return volume; }
    public int getSearchRadius() { return searchRadius; }

    @SuppressWarnings("unchecked")
    private ArbitraryShapeDefinition(List<IStructureElement<T>> structureElement, int searchRadius) {
        if (searchRadius > LocalCoord.MAX_SEARCH_RADIUS) {
            throw new IllegalArgumentException("Search radius too large: " + searchRadius);
        }
        this.searchRadius      = searchRadius;
        this.structureElements = structureElement.toArray(new IStructureElement[0]);
        this.numElements       = this.structureElements.length;

        this.structureBlocks   = new DenseBitSet(searchRadius);
        this.floodVisited      = new DenseBitSet(searchRadius);
        this.validBoundaryBits = new DenseBitSet(searchRadius);
        this.enclosedVisited   = new DenseBitSet(searchRadius);
    }

    @Override public IStructureElement<T>[] getStructureFor(String s) { return structureElements; }

    @Override
    public boolean isContainedInStructure(String shapeName, int x, int y, int z) {
        if (tile == null) {
            Galaxia.LOG.error("Structure is not formed yet");
            return false;
        }
        return structureBlocks.contains(x - tile.xCoord, y - tile.yCoord, z - tile.zCoord);
    }

    @Override
    public boolean check(T tile, String shapeName, World world, ExtendedFacing extendedFacing,
                         int x, int y, int z, int offsetX, int offsetY, int offsetZ, boolean forceCheckAllBlocks) {

//        if (fastRevalidate(tile)) return true;

        floodStructure(tile, world);
        debugSet(world, validBoundaryBits, x, y, z);

        ForgeDirection placedFacing = tile.getPlacedFacing();
        structureBlocks.clear();

        boolean enclosed = checkEnclosed(tile, world, placedFacing);
        if (enclosed) {
            this.tile   = tile;
            this.volume = structureBlocks.size();
        }
        return enclosed;
    }

    @Override public boolean hints(T tile, ItemStack trigger, String shapeName, World world,
                                   ExtendedFacing extendedFacing, int x, int y, int z, int offsetX, int offsetY, int offsetZ) { return false; }

    @Override public boolean build(T tile, ItemStack trigger, String shapeName, World world,
                                   ExtendedFacing extendedFacing, int x, int y, int z, int offsetX, int offsetY, int offsetZ) { return false; }

    @Override public boolean buildOrHints(T tile, ItemStack trigger, String shapeName, World world,
                                          ExtendedFacing extendedFacing, int x, int y, int z, int offsetX, int offsetY, int offsetZ,
                                          boolean hintsOnly) { return false; }

    @Override public int survivalBuild(T tile, ItemStack trigger, String shapeName, World world,
                                       ExtendedFacing extendedFacing, int x, int y, int z, int offsetX, int offsetY, int offsetZ,
                                       int elementBudget, com.gtnewhorizon.structurelib.structure.IItemSource source,
                                       net.minecraft.entity.player.EntityPlayerMP player, boolean hintsOnly) { return -1; }

    @Override public int survivalBuild(T tile, ItemStack trigger, String shapeName, World world,
                                       ExtendedFacing extendedFacing, int x, int y, int z, int offsetX, int offsetY, int offsetZ,
                                       int elementBudget, com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment env,
                                       boolean hintsOnly) { return -1; }

    @Override public void iterate(String shapeName, World world, ExtendedFacing extendedFacing,
                                  int x, int y, int z, int offsetX, int offsetY, int offsetZ, IStructureWalker<T> walker) {}

    private boolean fastRevalidate(T tile) {
        if (!tile.isStructureValid() || structureBlocks.isEmpty()) return false;

        World world = tile.worldObj();
        if (world == null || world.isRemote) return true;

        final int xCoord = tile.xCoord, yCoord = tile.yCoord, zCoord = tile.zCoord;
        final boolean[] valid = { true };

        structureBlocks.forEach((lx, ly, lz) -> {
            if (!valid[0]) return;
            if (!isValidBoundary(tile, world,
                LocalCoord.worldX(lx, xCoord),
                LocalCoord.worldY(ly, yCoord),
                LocalCoord.worldZ(lz, zCoord))) valid[0] = false;
        });

        return valid[0];
    }

    /**
     * BFS from the controller outward through valid-boundary blocks.
     * Simultaneously tracks the AABB of all boundary blocks found, so
     * checkEnclosed has a tight containment box rather than the full search radius.
     */
    private void floodStructure(T tile, World world) {
        floodVisited.clear();
        validBoundaryBits.clear();

        // Reset AABB to inverse-infinity so the first add initialises it.
        aabbMinX = aabbMinY = aabbMinZ = Integer.MAX_VALUE;
        aabbMaxX = aabbMaxY = aabbMaxZ = Integer.MIN_VALUE;

        IntQueue floodBFS = new IntQueue();
        final int xCoord = tile.xCoord, yCoord = tile.yCoord, zCoord = tile.zCoord;
        final int sr = searchRadius;

        floodBFS.enqueue(LocalCoord.pack(0, 0, 0, sr));
        floodVisited.add(0, 0, 0);
        addToBoundary(0, 0, 0);

        while (!floodBFS.isEmpty()) {
            int cur = floodBFS.dequeue();
            int lx  = LocalCoord.unpackX(cur, sr);
            int ly  = LocalCoord.unpackY(cur, sr);
            int lz  = LocalCoord.unpackZ(cur, sr);

            for (int i = 0; i < 6; i++) {
                int nlx = lx + DIR_DX[i];
                int nly = ly + DIR_DY[i];
                int nlz = lz + DIR_DZ[i];

                if (!LocalCoord.isInBounds(nlx, nly, nlz, sr)) continue;
                if (!floodVisited.add(nlx, nly, nlz)) continue;

                if (!isValidBoundary(tile, world,
                    LocalCoord.worldX(nlx, xCoord),
                    LocalCoord.worldY(nly, yCoord),
                    LocalCoord.worldZ(nlz, zCoord))) continue;

                addToBoundary(nlx, nly, nlz);
                floodBFS.enqueue(LocalCoord.pack(nlx, nly, nlz, sr));
            }
        }
    }

    /** Records a boundary block and expands the AABB to include it. */
    private void addToBoundary(int lx, int ly, int lz) {
        validBoundaryBits.add(lx, ly, lz);
        if (lx < aabbMinX) aabbMinX = lx;
        if (lx > aabbMaxX) aabbMaxX = lx;
        if (ly < aabbMinY) aabbMinY = ly;
        if (ly > aabbMaxY) aabbMaxY = ly;
        if (lz < aabbMinZ) aabbMinZ = lz;
        if (lz > aabbMaxZ) aabbMaxZ = lz;
    }

    // Don't remove for now, makes debugging easier
    private static void debugSet(World world, DenseBitSet set, int xc, int yc, int zc) {
        java.util.HashMap<com.gtnewhorizons.galaxia.api.BlockPos, Block> dbg = new java.util.HashMap<>();
        set.forEach((lx, ly, lz) -> {
            int x = lx + xc, y = ly + yc, z = lz + zc;
            dbg.put(new com.gtnewhorizons.galaxia.api.BlockPos(x, y, z), world.getBlock(x, y, z));
        });
        System.out.println(dbg.size());
    }

    private boolean isValidBoundary(T tile, World world, int x, int y, int z) {
        final IStructureElement<T>[] elems = structureElements;
        final int n = numElements;
        Block b = world.getBlock(x,y,z);
        for (int i = 0; i < n; i++) {
            if (elems[i].couldBeValid(tile, world, x, y, z, null)) {
                return true;
            }
        }
        return false;
    }

    private boolean checkValidBoundary(T tile, World world, int x, int y, int z) {
        final IStructureElement<T>[] elems = structureElements;
        final int n = numElements;
        for (int i = 0; i < n; i++) {
            if (elems[i].check(tile, world, x, y, z)) return true;
        }
        return false;
    }

    /**
     * BFS through interior air from one step ahead of the controller.
     *
     * Uses the AABB computed by floodStructure as a containment test:
     * any air cell that steps outside [aabbMin, aabbMax] on any axis proves
     * the interior is not enclosed — fail immediately without exhausting maxBlocks.
     *
     * maxBlocks is also derived from the AABB volume rather than searchRadius³,
     * so the iteration budget is proportional to the actual structure size.
     */
    private boolean checkEnclosed(T tile, World world, ForgeDirection placedFacing) {
        enclosedVisited.clear();

        // Tight budget: AABB volume rather than full searchRadius³.
        final int maxAir = (aabbMaxX - aabbMinX + 1)
            * (aabbMaxY - aabbMinY + 1)
            * (aabbMaxZ - aabbMinZ + 1);

        IntQueue bfs = new IntQueue();
        final int xCoord = tile.xCoord, yCoord = tile.yCoord, zCoord = tile.zCoord;
        final int sr = searchRadius;

        final int sx = placedFacing.offsetX, sy = placedFacing.offsetY, sz = placedFacing.offsetZ;
        bfs.enqueue(LocalCoord.pack(sx, sy, sz, sr));
        enclosedVisited.add(sx, sy, sz);

        int checked = 0;
        while (!bfs.isEmpty() && checked++ < maxAir) {
            int cur = bfs.dequeue();
            int lx  = LocalCoord.unpackX(cur, sr);
            int ly  = LocalCoord.unpackY(cur, sr);
            int lz  = LocalCoord.unpackZ(cur, sr);

            for (int i = 0; i < 6; i++) {
                int nlx = lx + DIR_DX[i];
                int nly = ly + DIR_DY[i];
                int nlz = lz + DIR_DZ[i];

                // AABB check: escaping the boundary's bounding box means the
                // interior is open. This fires far sooner than isInBounds for
                // any structure smaller than the full search radius.
                if (nlx < aabbMinX || nlx > aabbMaxX
                    || nly < aabbMinY || nly > aabbMaxY
                    || nlz < aabbMinZ || nlz > aabbMaxZ) return false;

                if (!enclosedVisited.add(nlx, nly, nlz)) continue;

                if (validBoundaryBits.contains(nlx, nly, nlz) && !structureBlocks.contains(nlx, nly, nlz)) {
                    int nwx = LocalCoord.worldX(nlx, xCoord);
                    int nwy = LocalCoord.worldY(nly, yCoord);
                    int nwz = LocalCoord.worldZ(nlz, zCoord);
                    if (checkValidBoundary(tile, world, nwx, nwy, nwz)) {
                        structureBlocks.add(nlx, nly, nlz);
                        continue;
                    }
                }

                bfs.enqueue(LocalCoord.pack(nlx, nly, nlz, sr));
            }
        }

        return !structureBlocks.isEmpty() && structureBlocks.size() >= 6;
    }

    public static class Builder<T extends TileEntity & ArbitraryShapeTile<T>> {

        private final List<IStructureElement<T>> elements = new ArrayList<>();
        private int searchRadius = LocalCoord.SEARCH_RADIUS;

        private Builder() {}

        public Builder<T> withSearchRadius(int radius) {
            if (radius > LocalCoord.MAX_SEARCH_RADIUS) {
                throw new IllegalArgumentException("Search radius too large for 10-bit encoding: " + radius);
            }
            this.searchRadius = radius;
            return this;
        }

        public Builder<T> addControllerBlock(Block controller) {
            return addElement(StructureUtility.ofBlockAnyMeta(controller));
        }

        public Builder<T> addElement(IStructureElement<T> element) {
            elements.add(element);
            return this;
        }

        public Builder<T> addElements(Stream<IStructureElement<T>> elements) {
            this.elements.addAll(elements.toList());
            return this;
        }

        @SuppressWarnings({ "unchecked", "rawtypes" })
        public <D> Builder<T> embedDefinition(String shape, IStructureDefinition<D> definition) {
            if (!(definition instanceof StructureDefinition<D> def)) {
                throw new IllegalArgumentException("Unsupported structure definition");
            }
            String encodedShape = def.getShapes().get(shape);
            if (encodedShape == null) {
                throw new IllegalArgumentException("Unknown shape: " + shape);
            }

            Map<Character, IStructureElement<D>> sourceElements = def.getElements();
            for (char c : encodedShape.toCharArray()) {
                if (c == '+' || c == '-' || c == ' ') continue;
                IStructureElement<D> element = sourceElements.get(c);
                if (element == null) continue;
                if (!this.elements.contains(element) && GalaxiaStructureUtility.isStructureNavigate(element)) {
                    this.elements.add((IStructureElement<T>) element);
                }
            }
            return this;
        }

        @SuppressWarnings("unchecked")
        public ArbitraryShapeDefinition<T> build() {
            return new ArbitraryShapeDefinition<>(elements, searchRadius);
        }
    }
}
