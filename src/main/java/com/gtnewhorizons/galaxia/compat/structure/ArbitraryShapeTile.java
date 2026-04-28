package com.gtnewhorizons.galaxia.compat.structure;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizon.structurelib.structure.IStructureDefinition;

import it.unimi.dsi.fastutil.ints.IntSet;

public interface ArbitraryShapeTile<T extends TileEntity & ArbitraryShapeTile<T>> {

    IStructureDefinition<T> getStructureDefinition();

    ForgeDirection getPlacedFacing();

    IntSet getStructureBlocks();

    void setStructureBlocks(IntSet blocks);

    boolean isStructureValid();

    int getVolume();

    void setVolume(int volume);

    default World worldObj() {
        return ((TileEntity) this).getWorldObj();
    }
}
