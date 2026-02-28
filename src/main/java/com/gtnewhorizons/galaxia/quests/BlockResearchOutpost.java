package com.gtnewhorizons.galaxia.quests;

import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.cleanroommc.modularui.factory.GuiFactories;

public class BlockResearchOutpost extends Block implements ITileEntityProvider {

    public BlockResearchOutpost() {
        super(Material.rock);
        this.setBlockTextureName("stone");
        this.setHardness(1.5F);
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileEntityResearchOutpost();
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX,
        float hitY, float hitZ) {
        if (world.isRemote) return true;
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileEntityResearchOutpost) GuiFactories.tileEntity()
            .open(player, x, y, z);
        return true;
    }
}
