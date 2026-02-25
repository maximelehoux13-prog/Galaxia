package com.gtnewhorizons.galaxia.registry.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockAir;
import net.minecraft.world.World;

public class AirPressurizable extends BlockAir {

    // This block class replaces vanilla air and adds additional meta variants.
    // 0 - Vanilla air
    // 1 - Void (space)
    // 2 - Pressurized air
    // 3 - Depressurized air

    @Override
    public void onNeighborBlockChange(World worldIn, int x, int y, int z, Block neighbor) {
        super.onNeighborBlockChange(worldIn, x, y, z, neighbor);

        if (worldIn.getBlockMetadata(x, y, z) == 0) return;
        System.out.println("Pressurized Air updated");
    }
}
