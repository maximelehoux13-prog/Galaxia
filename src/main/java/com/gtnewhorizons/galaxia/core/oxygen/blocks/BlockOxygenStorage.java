package com.gtnewhorizons.galaxia.core.oxygen.blocks;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.cleanroommc.modularui.factory.GuiFactories;
import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.core.oxygen.tile.TileEntityOxygenStorage;

public class BlockOxygenStorage extends Block {

    public BlockOxygenStorage() {
        super(Material.rock);
        this.setBlockTextureName("dirt");
        this.setHardness(1.5F);
        setBlockName("oxygenStorage");
        setCreativeTab(Galaxia.creativeTab);
    }

    @Override
    public boolean hasTileEntity(int metadata) {
        return true;
    }

    @Override
    public TileEntity createTileEntity(World world, int metadata) {
        return new TileEntityOxygenStorage();
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX,
        float hitY, float hitZ) {
        if (world.isRemote) return true;
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileEntityOxygenStorage) GuiFactories.tileEntity()
            .open(player, x, y, z);
        return true;
    }

}
