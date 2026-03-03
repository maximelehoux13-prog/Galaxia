package com.gtnewhorizons.galaxia.core.oxygen.blocks;

import com.cleanroommc.modularui.factory.GuiFactories;
import com.gtnewhorizons.galaxia.core.oxygen.tile.TileOxygenFiller;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

public class BlockOxygenFiller extends Block {


    public BlockOxygenFiller() {
        super(Material.rock);
        this.setBlockTextureName("dirt");
        this.setHardness(1.5F);
    }

    @Override
    public boolean hasTileEntity(int metadata) {
        return true;
    }

    @Override
    public TileEntity createTileEntity(World world, int metadata) {
        return new TileOxygenFiller();
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX,
                                    float hitY, float hitZ) {
        if (world.isRemote) return true;
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileOxygenFiller) GuiFactories.tileEntity()
            .open(player, x, y, z);
        return true;
    }
}
