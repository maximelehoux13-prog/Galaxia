package com.gtnewhorizons.galaxia.core.oxygen.blocks;

import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.core.oxygen.tile.TileEntityOxygenTether;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;


public class BlockOxygenTether extends Block {

    public BlockOxygenTether() {
        super(Material.rock);
        setBlockName("tether");
        setBlockTextureName("dirt");
        setHardness(1.5F);
        setCreativeTab(Galaxia.creativeTab);
    }

    @Override
    public boolean hasTileEntity(int meta) {
        return true;
    }

    @Override
    public TileEntity createTileEntity(World world, int meta) {
        return new TileEntityOxygenTether();
    }
}
