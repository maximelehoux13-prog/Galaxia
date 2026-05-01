package com.gtnewhorizons.galaxia.compat.structure;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

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
import com.gtnewhorizons.galaxia.api.BlockPos;
import com.gtnewhorizons.galaxia.compat.structure.util.IntQueue;
import com.gtnewhorizons.galaxia.compat.structure.util.LocalCoord;
import com.gtnewhorizons.galaxia.core.Galaxia;

import it.unimi.dsi.fastutil.ints.IntSet;

public class ArbitraryShapeDefinition<T extends TileEntity & ArbitraryShapeTile<T>> implements IStructureDefinition<T> {

    private final int searchRadius;
    private final int maxBlocks;
    private T tile;
    private int volume;
    private final IStructureElement<T>[] structureElements;
    private final IntSet structureBlocks = LocalCoord.newBlockSet();

    public static <T extends TileEntity & ArbitraryShapeTile<T>> Builder<T> builder() {
        return new Builder<>();
    }

    public int getVolume() {
        return volume;
    }

    public int getSearchRadius() {
        return searchRadius;
    }

    @SuppressWarnings("unchecked")
    private ArbitraryShapeDefinition(List<IStructureElement<T>> structureElement, int searchRadius) {
        if (searchRadius > LocalCoord.MAX_SEARCH_RADIUS) {
            throw new IllegalArgumentException("Search radius too large: " + searchRadius);
        }
        this.searchRadius = searchRadius;
        this.maxBlocks = searchRadius * searchRadius * searchRadius;
        this.structureElements = structureElement.toArray(new IStructureElement[0]);
    }

    @Override
    public IStructureElement<T>[] getStructureFor(String s) {
        return structureElements;
    }

    @Override
    public boolean isContainedInStructure(String shapeName, int x, int y, int z) {
        if (tile == null) {
            Galaxia.LOG.error("Structure is not formed yet");
            return false;
        }
        return structureBlocks
            .contains(LocalCoord.pack(x - tile.xCoord, y - tile.yCoord, z - tile.zCoord, searchRadius));
    }

    @Override
    public boolean check(T tile, String shapeName, World world, ExtendedFacing extendedFacing, int x, int y, int z,
        int offsetX, int offsetY, int offsetZ, boolean forceCheckAllBlocks) {

        if (fastRevalidate(tile)) return true;
        IntSet validBoundary = floodStructure(tile, world);

        ForgeDirection placedFacing = tile.getPlacedFacing();
        structureBlocks.clear();

        boolean enclosed = checkEnclosed(tile, world, validBoundary, placedFacing, structureBlocks);
        if (enclosed) {
            this.tile = tile;
            this.volume = structureBlocks.size();
        }
        return enclosed;
    }

    @Override
    public boolean hints(T tile, ItemStack trigger, String shapeName, World world, ExtendedFacing extendedFacing, int x,
        int y, int z, int offsetX, int offsetY, int offsetZ) {
        return false;
    }

    @Override
    public boolean build(T tile, ItemStack trigger, String shapeName, World world, ExtendedFacing extendedFacing, int x,
        int y, int z, int offsetX, int offsetY, int offsetZ) {
        return false;
    }

    @Override
    public boolean buildOrHints(T tile, ItemStack trigger, String shapeName, World world, ExtendedFacing extendedFacing,
        int x, int y, int z, int offsetX, int offsetY, int offsetZ, boolean hintsOnly) {
        return false;
    }

    @Override
    public int survivalBuild(T tile, ItemStack trigger, String shapeName, World world, ExtendedFacing extendedFacing,
        int x, int y, int z, int offsetX, int offsetY, int offsetZ, int elementBudget,
        com.gtnewhorizon.structurelib.structure.IItemSource source, net.minecraft.entity.player.EntityPlayerMP player,
        boolean hintsOnly) {
        return -1;
    }

    @Override
    public int survivalBuild(T tile, ItemStack trigger, String shapeName, World world, ExtendedFacing extendedFacing,
        int x, int y, int z, int offsetX, int offsetY, int offsetZ, int elementBudget,
        com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment env, boolean hintsOnly) {
        return -1;
    }

    @Override
    public void iterate(String shapeName, World world, ExtendedFacing extendedFacing, int x, int y, int z, int offsetX,
        int offsetY, int offsetZ, IStructureWalker<T> walker) {}

    private boolean fastRevalidate(T tile) {
        if (!tile.isStructureValid() || structureBlocks.isEmpty()) return false;

        World world = tile.worldObj();
        if (world == null || world.isRemote) return true;

        for (int packed : structureBlocks) {
            int wx = LocalCoord.worldX(LocalCoord.unpackX(packed, searchRadius), tile.xCoord);
            int wy = LocalCoord.worldY(LocalCoord.unpackY(packed, searchRadius), tile.yCoord);
            int wz = LocalCoord.worldZ(LocalCoord.unpackZ(packed, searchRadius), tile.zCoord);
            if (!isValidBoundary(tile, world, wx, wy, wz)) {
                return false;
            }
        }
        return true;
    }

    private IntSet floodStructure(T tile, World world) {
        IntQueue floodBFS = new IntQueue();
        final IntSet connectedStructure = LocalCoord.newBlockSet();

        int start = LocalCoord.pack(0, 0, 0, searchRadius);
        floodBFS.enqueue(start);
        connectedStructure.add(start);

        while (!floodBFS.isEmpty()) {
            int cur = floodBFS.dequeue();
            int lx = LocalCoord.unpackX(cur, searchRadius);
            int ly = LocalCoord.unpackY(cur, searchRadius);
            int lz = LocalCoord.unpackZ(cur, searchRadius);

            for (ForgeDirection d : ForgeDirection.VALID_DIRECTIONS) {
                int nlx = lx + d.offsetX;
                int nly = ly + d.offsetY;
                int nlz = lz + d.offsetZ;

                if (!LocalCoord.isInBounds(nlx, nly, nlz, searchRadius)) continue;

                int np = LocalCoord.pack(nlx, nly, nlz, searchRadius);

                int wx = LocalCoord.worldX(nlx, tile.xCoord);
                int wy = LocalCoord.worldY(nly, tile.yCoord);
                int wz = LocalCoord.worldZ(nlz, tile.zCoord);

                if (!isValidBoundary(tile, world, wx, wy, wz)) continue;

                if (connectedStructure.add(np)) {
                    floodBFS.enqueue(np);
                }
            }
        }

        return connectedStructure;
    }

    // Don't remove for now, makes debugging easier
    private static void debugSet(World world, IntSet set, int xc, int yc, int zc, int searchRadius) {
        HashMap<BlockPos, Block> dbg = new HashMap<>();
        for (int coord : set) {
            int x = LocalCoord.unpackX(coord, searchRadius) + xc;
            int y = LocalCoord.unpackY(coord, searchRadius) + yc;
            int z = LocalCoord.unpackZ(coord, searchRadius) + zc;
            BlockPos pos = new BlockPos(x, y, z);
            Block b = world.getBlock(x, y, z);
            dbg.put(pos, b);
        }

        System.out.println(dbg.size());
    }

    private boolean isValidBoundary(T tile, World world, int x, int y, int z) {
        for (IStructureElement<T> element : structureElements) {
            if (element.couldBeValid(tile, world, x, y, z, null)) {
                return true;
            }
        }

        return false;
    }

    private boolean checkValidBoundary(T tile, World world, int x, int y, int z) {
        for (IStructureElement<T> element : structureElements) {
            if (element.check(tile, world, x, y, z)) {
                return true;
            }
        }

        return false;
    }

    private boolean checkEnclosed(T tile, World world, IntSet validBoundary, ForgeDirection placedFacing,
        IntSet localStructureBlocks) {

        IntQueue bfs = new IntQueue();
        IntSet visited = LocalCoord.newBlockSet();

        int start = LocalCoord.pack(placedFacing.offsetX, placedFacing.offsetY, placedFacing.offsetZ, searchRadius);

        bfs.enqueue(start);
        visited.add(start);

        int checked = 0;
        while (!bfs.isEmpty() && checked++ < maxBlocks) {
            int cur = bfs.dequeue();
            int lx = LocalCoord.unpackX(cur, searchRadius);
            int ly = LocalCoord.unpackY(cur, searchRadius);
            int lz = LocalCoord.unpackZ(cur, searchRadius);

            for (ForgeDirection d : ForgeDirection.VALID_DIRECTIONS) {
                int nlx = lx + d.offsetX;
                int nly = ly + d.offsetY;
                int nlz = lz + d.offsetZ;

                if (!LocalCoord.isInBounds(nlx, nly, nlz, searchRadius)) return false;

                int np = LocalCoord.pack(nlx, nly, nlz, searchRadius);

                int nwx = LocalCoord.worldX(nlx, tile.xCoord);
                int nwy = LocalCoord.worldY(nly, tile.yCoord);
                int nwz = LocalCoord.worldZ(nlz, tile.zCoord);

                Block b = world.getBlock(nwx, nwy, nwz);

                if (!b.isAir(world, nwx, nwy, nwz)) {
                    if (validBoundary.contains(np)) {
                        localStructureBlocks.add(np);

                        if (!checkValidBoundary(tile, world, nwx, nwy, nwz)) {
                            return false;
                        }
                    }
                    continue;
                }

                if (visited.add(np)) {
                    bfs.enqueue(np);
                }
            }
        }

        return !localStructureBlocks.isEmpty() && localStructureBlocks.size() >= 6;
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
            if (!(definition instanceof StructureDefinition<D>def)) {
                throw new IllegalArgumentException("Unsupported structure definition");
            }

            String encodedShape = def.getShapes()
                .get(shape);
            if (encodedShape == null) {
                throw new IllegalArgumentException("Unknown shape: " + shape);
            }

            Map<Character, IStructureElement<D>> sourceElements = def.getElements();

            for (char c : encodedShape.toCharArray()) {
                IStructureElement<D> element = sourceElements.get(c);

                if (element == null) continue;

                if (c == '+' || c == '-' || c == ' ') continue;

                if (!this.elements.contains(element)) {
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
