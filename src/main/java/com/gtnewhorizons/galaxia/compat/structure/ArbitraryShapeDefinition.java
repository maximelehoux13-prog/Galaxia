package com.gtnewhorizons.galaxia.compat.structure;

import java.util.ArrayList;
import java.util.List;
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
import com.gtnewhorizons.galaxia.compat.structure.util.IntQueue;
import com.gtnewhorizons.galaxia.compat.structure.util.LocalCoord;
import com.gtnewhorizons.galaxia.core.Galaxia;

import it.unimi.dsi.fastutil.ints.IntSet;

public class ArbitraryShapeDefinition<T extends TileEntity & ArbitraryShapeTile<T>> implements IStructureDefinition<T> {

    private static final int MAX_BLOCKS = LocalCoord.SEARCH_RADIUS * LocalCoord.SEARCH_RADIUS
        * LocalCoord.SEARCH_RADIUS;

    private T tile;
    private final IStructureElement<T>[] structureElements;

    public static <T extends TileEntity & ArbitraryShapeTile<T>> Builder<T> builder() {
        return new Builder<>();
    }

    @SuppressWarnings("unchecked")
    private ArbitraryShapeDefinition(IStructureElement<T>[] structureElement) {
        this.structureElements = structureElement;
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
        return tile.getStructureBlocks()
            .contains(LocalCoord.pack(x - tile.xCoord, y - tile.yCoord, z - tile.zCoord));
    }

    @Override
    public boolean check(T tile, String shapeName, World world, ExtendedFacing extendedFacing, int x, int y, int z,
        int offsetX, int offsetY, int offsetZ, boolean forceCheckAllBlocks) {

        if (fastRevalidate(tile)) return true;

        IntSet validBoundary = floodStructure(tile, world);
        ForgeDirection placedFacing = tile.getPlacedFacing();
        IntSet localStructureBlocks = LocalCoord.newBlockSet();

        boolean enclosed = checkEnclosed(tile, world, validBoundary, placedFacing, localStructureBlocks);
        if (enclosed) {
            tile.setStructureBlocks(localStructureBlocks);
            this.tile = tile;
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
        if (!tile.isStructureValid() || tile.getStructureBlocks()
            .isEmpty()) return false;

        World world = tile.worldObj();
        if (world == null || world.isRemote) return true;

        for (int packed : tile.getStructureBlocks()) {
            int wx = LocalCoord.worldX(LocalCoord.unpackX(packed), tile.xCoord);
            int wy = LocalCoord.worldY(LocalCoord.unpackSignedY(packed), tile.yCoord);
            int wz = LocalCoord.worldZ(LocalCoord.unpackZ(packed), tile.zCoord);
            if (!isValidBoundary(tile, world, wx, wy, wz)) {
                return false;
            }
        }
        return true;
    }

    private IntSet floodStructure(T tile, World world) {
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

                if (!LocalCoord.isInBounds(nlx, nly, nlz)) continue;

                int np = LocalCoord.pack(nlx, nly, nlz);

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

    private boolean isValidBoundary(T tile, World world, int x, int y, int z) {
        for (IStructureElement<T> element : structureElements) {
            if (!element.couldBeValid(tile, world, x, y, z, null)) {
                return false;
            }
        }

        return true;
    }

    private boolean checkEnclosed(T tile, World world, IntSet validBoundary, ForgeDirection placedFacing,
        IntSet localStructureBlocks) {

        IntQueue bfs = new IntQueue();
        IntSet visited = LocalCoord.newBlockSet();

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

                if (!LocalCoord.isInBounds(nlx, nly, nlz)) return false;

                int np = LocalCoord.pack(nlx, nly, nlz);

                int nwx = LocalCoord.worldX(nlx, tile.xCoord);
                int nwy = LocalCoord.worldY(nly, tile.yCoord);
                int nwz = LocalCoord.worldZ(nlz, tile.zCoord);

                Block b = world.getBlock(nwx, nwy, nwz);

                if (!b.isAir(world, nwx, nwy, nwz)) {
                    if (validBoundary.contains(np)) {
                        localStructureBlocks.add(np);

                        for (IStructureElement<T> element : structureElements) {
                            if (!element.check(tile, world, nwx, nwy, nwz)) {
                                return false;
                            }
                        }
                    }
                    continue;
                }

                if (visited.add(np)) {
                    bfs.enqueue(np);
                }
            }
        }

        if (!localStructureBlocks.isEmpty() && localStructureBlocks.size() >= 6) {
            tile.setVolume(visited.size());
            return true;
        }
        return false;
    }

    public static class Builder<T extends TileEntity & ArbitraryShapeTile<T>> {

        private final List<IStructureElement<T>> elements = new ArrayList<>();

        private Builder() {}

        public Builder<T> addElement(IStructureElement<T> element) {
            elements.add(element);
            return this;
        }

        public Builder<T> addElements(Stream<IStructureElement<T>> elements) {
            this.elements.addAll(elements.toList());
            return this;
        }

        @SuppressWarnings({ "unchecked", "rawtypes" })
        public Builder<T> embedDefinition(String shape, IStructureDefinition<?> definition) {
            for (IStructureElement element : definition.getStructureFor(shape)) {
                elements.add((IStructureElement<T>) element);
            }
            return this;
        }

        @SuppressWarnings("unchecked")
        public ArbitraryShapeDefinition<T> build() {
            return new ArbitraryShapeDefinition<>(elements.toArray(new IStructureElement[0]));
        }
    }
}
