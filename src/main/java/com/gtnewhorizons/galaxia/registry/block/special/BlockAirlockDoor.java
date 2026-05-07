package com.gtnewhorizons.galaxia.registry.block.special;

import com.gtnewhorizons.galaxia.registry.block.base.BlockUpdatable;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Facing;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizons.galaxia.compat.structure.util.IntQueue;
import com.gtnewhorizons.galaxia.compat.structure.util.LocalCoord;
import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.registry.block.GalaxiaBlocksEnum;

public class BlockAirlockDoor extends BlockUpdatable {

    public static final int META_CLOSED = 0;
    public static final int META_OPEN = 1;

    public BlockAirlockDoor() {
        super(Material.iron);

        setBlockName("airlock_door");
        setBlockTextureName("galaxia:machine/airlock_door");

        setHardness(2.0F);
        setResistance(10.0F);
        this.setCreativeTab(Galaxia.creativeTab);
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

    private static final int AIRLOCK_FLOOD_RADIUS = 8;

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX,
        float hitY, float hitZ) {
        if (world.isRemote) return true;

        IntQueue floodBFS = new IntQueue();
        int start = LocalCoord.pack(0, 0, 0, AIRLOCK_FLOOD_RADIUS);
        floodBFS.enqueue(start);

        while (!floodBFS.isEmpty()) {
            int cur = floodBFS.dequeue();
            int lx = LocalCoord.unpackX(cur, AIRLOCK_FLOOD_RADIUS);
            int ly = LocalCoord.unpackY(cur, AIRLOCK_FLOOD_RADIUS);
            int lz = LocalCoord.unpackZ(cur, AIRLOCK_FLOOD_RADIUS);

            for (ForgeDirection d : ForgeDirection.VALID_DIRECTIONS) {
                int nlx = lx + d.offsetX;
                int nly = ly + d.offsetY;
                int nlz = lz + d.offsetZ;

                if (!LocalCoord.isInBounds(nlx, nly, nlz, AIRLOCK_FLOOD_RADIUS)) continue;

                int np = LocalCoord.pack(nlx, nly, nlz, AIRLOCK_FLOOD_RADIUS);

                int wx = LocalCoord.worldX(nlx, x);
                int wy = LocalCoord.worldY(nly, y);
                int wz = LocalCoord.worldZ(nlz, z);

                Block b = world.getBlock(wx, wy, wz);

                if (b == GalaxiaBlocksEnum.AIRLOCK_DOOR.get()) {
                    floodBFS.enqueue(np);
                } else if (b == GalaxiaBlocksEnum.AIRLOCK_CONTROLLER.get()) {
                    BlockAirlockController controller = (BlockAirlockController) b;
                    return controller.toggleDoor(world, wx, wy, wz);
                }
            }
        }

        return false;
    }
}
