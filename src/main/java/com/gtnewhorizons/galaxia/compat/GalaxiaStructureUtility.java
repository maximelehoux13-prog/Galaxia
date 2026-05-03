package com.gtnewhorizons.galaxia.compat;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.gtnewhorizon.structurelib.StructureLibAPI;
import com.gtnewhorizon.structurelib.structure.IStructureElement;
import com.gtnewhorizon.structurelib.structure.IStructureElementNoPlacement;
import com.gtnewhorizon.structurelib.structure.adders.ITileAdder;

public class GalaxiaStructureUtility {

    private static Class<?> navClass;
    static {
        try {
            navClass = Class.forName("com.gtnewhorizon.structurelib.structure.IStructureNavigate");
        } catch (ClassNotFoundException e) {
            navClass = null;
        }
    }

    // TODO: This method should be upstreamed, but right now it would be annoying due to the feature freeze
    public static <T> IStructureElementNoPlacement<T> ofTileAdderCheckHints(ITileAdder<T> iTileAdder, Block hintBlock,
        int hintMeta) {
        if (iTileAdder == null || hintBlock == null) {
            throw new IllegalArgumentException();
        }
        return new IStructureElementNoPlacement<T>() {

            @Override
            public boolean check(T t, World world, int x, int y, int z) {
                TileEntity tileEntity = world.getTileEntity(x, y, z);
                // This used to check if it's a GT tile. Since this is now an standalone mod we no longer do this
                return iTileAdder.apply(t, tileEntity);
            }

            @Override
            public boolean couldBeValid(T t, World world, int x, int y, int z, ItemStack trigger) {
                Block worldBlock = world.getBlock(x, y, z);
                return hintBlock == worldBlock && hintMeta == worldBlock.getDamageValue(world, x, y, z);
            }

            @Override
            public boolean spawnHint(T t, World world, int x, int y, int z, ItemStack trigger) {
                StructureLibAPI.hintParticle(world, x, y, z, hintBlock, hintMeta);
                return true;
            }
        };
    }

    public static <T> IStructureElementNoPlacement<T> ofTileAdderCheckHintsAnyMeta(ITileAdder<T> iTileAdder,
        Block hintBlock, int hintMeta) {
        if (iTileAdder == null || hintBlock == null) {
            throw new IllegalArgumentException();
        }
        return new IStructureElementNoPlacement<T>() {

            @Override
            public boolean check(T t, World world, int x, int y, int z) {
                TileEntity tileEntity = world.getTileEntity(x, y, z);
                // This used to check if it's a GT tile. Since this is now an standalone mod we no longer do this
                return couldBeValid(t, world, x, y, z, null) && iTileAdder.apply(t, tileEntity);
            }

            @Override
            public boolean couldBeValid(T t, World world, int x, int y, int z, ItemStack trigger) {
                Block worldBlock = world.getBlock(x, y, z);
                return hintBlock == worldBlock;
            }

            @Override
            public boolean spawnHint(T t, World world, int x, int y, int z, ItemStack trigger) {
                StructureLibAPI.hintParticle(world, x, y, z, hintBlock, hintMeta);
                return true;
            }
        };
    }

    public static <T> boolean isStructureNavigate(IStructureElement<T> element) {
        return navClass != null && navClass.isInstance(element);
    }
}
