package com.gtnewhorizons.galaxia.core.oxygen.blocks;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

public class BlockOxygenTether extends Block {

    protected BlockOxygenTether() {
        super(Material.redstoneLight);
        setBlockTextureName("torch");
    }

    @Override
    public boolean onBlockActivated(World worldIn, int x, int y, int z, EntityPlayer player, int side, float subX,
        float subY, float subZ) {
        return super.onBlockActivated(worldIn, x, y, z, player, side, subX, subY, subZ);
    }

}
