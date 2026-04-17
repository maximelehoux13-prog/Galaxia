package com.gtnewhorizons.galaxia.vaporchamber;

import static com.gtnewhorizons.galaxia.core.Galaxia.TEXTURE_PREFIX;

import java.util.ArrayList;

import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.material.Material;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

import com.gtnewhorizon.gtnhlib.blockpos.BlockPos;

public class BlockVaporChamber extends Block implements ITileEntityProvider {

    public BlockVaporChamber() {
        super(Material.glass);
        this.setHardness(1.5F);
        this.setResistance(10.0f);
        this.setBlockTextureName(TEXTURE_PREFIX + "vapor_chamber/vapor_chamber");
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileEntityVaporChamber();
    }

    /**
     * Handles logic to be ran on block placing - in this case, connecting to other
     * gantries
     *
     * @param world  The world placed in
     * @param x      X position of placed block
     * @param y      Y position of placed block
     * @param z      Z position of placed block
     * @param placer The placer of the block
     * @param stack  The item stack being used to place
     */
    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        if (world.isRemote) return;

        TileEntity te = world.getTileEntity(x, y, z);
        if (!(te instanceof TileEntityVaporChamber tevc)) {
            return;
        }

        // Check valid directions and connect to others
        for (Vec3 check_offset : TileEntityVaporChamber.CHECK_OFFSETS) {
            int cx = x + (int) check_offset.xCoord;
            int cy = y + (int) check_offset.yCoord;
            int cz = z + (int) check_offset.zCoord;

            TileEntity checkTe = world.getTileEntity(cx, cy, cz);
            if (checkTe instanceof TileEntityVaporChamber checkVaporChamber) {
                tevc.connect(checkVaporChamber);
            } else {
                tevc.notNeighbours.add(new BlockPos(cx, cy, cz));
            }
        }

    }

    /**
     * Handles logic on block break - in this case disconnecting from other gantries
     *
     * @param world  The world placed in
     * @param x      X position of placed block
     * @param y      Y position of placed block
     * @param z      Z position of placed block
     * @param placer The placer of the block
     * @param stack  The item stack being used to place
     */
    @Override
    public void breakBlock(World world, int x, int y, int z, Block block, int meta) {

        TileEntity chamber = world.getTileEntity(x, y, z);
        if (!(chamber instanceof TileEntityVaporChamber terminal)) {
            return;
        }

        // Iterate through neighbours and disconnect them
        for (TileEntityVaporChamber unlink : new ArrayList<>(terminal.neighbours)) {
            terminal.disconnect(unlink);
            unlink.notNeighbours.add(new BlockPos(x, y, z));
        }
    }

    @Override
    public boolean canPlaceBlockAt(World world, int x, int y, int z) {
        return true;
    }

    @Override
    public boolean renderAsNormalBlock() {
        return true;
    }

    @Override
    public boolean isOpaqueCube() {
        return true;
    }

    /**
     * Overrides the render type to not use the block render engine, but instead
     * solely use TESR
     *
     * @return The render type (always -1 in this case)
     */
    @Override
    public int getRenderType() {
        return 0;
    }
}
