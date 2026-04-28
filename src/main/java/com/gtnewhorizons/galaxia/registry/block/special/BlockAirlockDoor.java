package com.gtnewhorizons.galaxia.registry.block.special;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Facing;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

public class BlockAirlockDoor extends Block {

    public static final int META_CLOSED = 0;
    public static final int META_OPEN = 1;

    public BlockAirlockDoor() {
        super(Material.iron);

        setBlockName("airlock_door");
        setBlockTextureName("galaxia:machine/airlock_door");

        setHardness(2.0F);
        setResistance(10.0F);
    }

    @Override
    public boolean isOpaqueCube() {
        return false;
    }

    @Override
    public boolean renderAsNormalBlock() {
        return false;
    }

    @Override
    public int getLightOpacity() {
        return 0;
    }

    @Override
    public boolean shouldSideBeRendered(IBlockAccess world, int x, int y, int z, int side) {
        // TODO: This is a complete hack
        int meta = world.getBlockMetadata(
            x - Facing.offsetsXForSide[side],
            y - Facing.offsetsYForSide[side],
            z - Facing.offsetsZForSide[side]);

        return meta == META_CLOSED;
    }

    @Override
    public AxisAlignedBB getCollisionBoundingBoxFromPool(World world, int x, int y, int z) {
        int meta = world.getBlockMetadata(x, y, z);

        if (meta == META_OPEN) {
            return null;
        }

        return AxisAlignedBB.getBoundingBox(x, y, z, x + 1, y + 1, z + 1);
    }

    @Override
    public boolean canCollideCheck(int meta, boolean hitIfLiquid) {
        return true;
    }

    public void setOpen(World world, int x, int y, int z, boolean open) {
        int meta = open ? META_OPEN : META_CLOSED;

        world.setBlockMetadataWithNotify(x, y, z, meta, 3);
    }

    public boolean isOpen(IBlockAccess world, int x, int y, int z) {
        return world.getBlockMetadata(x, y, z) == META_OPEN;
    }

    @Override
    public MovingObjectPosition collisionRayTrace(World world, int x, int y, int z, Vec3 start, Vec3 end) {
        int meta = world.getBlockMetadata(x, y, z);

        if (meta == META_OPEN) {
            return null;
        }

        return super.collisionRayTrace(world, x, y, z, start, end);
    }
}
