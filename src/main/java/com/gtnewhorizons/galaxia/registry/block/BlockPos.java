package com.gtnewhorizons.galaxia.registry.block;

import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

public record BlockPos(int x, int y, int z) {

   public <T extends TileEntity> T getTE(World world) {
       return (T) world.getTileEntity(x, y, z);
   }

   public Block getBlock(World world) {
        return world.getBlock(x,y,z);
   }

}
