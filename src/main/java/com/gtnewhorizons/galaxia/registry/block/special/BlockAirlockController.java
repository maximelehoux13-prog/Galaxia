package com.gtnewhorizons.galaxia.registry.block.special;

import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.material.Material;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizons.galaxia.registry.block.tile.TileEntityAirlock;

public class BlockAirlockController extends Block implements ITileEntityProvider {

    public BlockAirlockController() {
        super(Material.rock);
        this.setHardness(1.5F);
        this.setBlockName("airlock_controller");
        this.setBlockTextureName("galaxia:machine/airlock_controller");
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileEntityAirlock();
    }

    @Override
    public boolean hasTileEntity(int meta) {
        return true;
    }

    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {

        TileEntity te = world.getTileEntity(x, y, z);

        if (te instanceof TileEntityAirlock) {
            TileEntityAirlock tile = (TileEntityAirlock) te;

            ForgeDirection facing;

            if (placer.isSneaking()) {
                facing = ForgeDirection.UP;
            } else {
                facing = ForgeDirection.getOrientation(Math.round(placer.rotationYaw / 90F) & 3)
                    .getOpposite();
            }

            tile.setFacing(facing);
        }
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX,
        float hitY, float hitZ) {

        TileEntity te = world.getTileEntity(x, y, z);
        if (world.isRemote) return true;

        if (te instanceof TileEntityAirlock) {
            ((TileEntityAirlock) te).toggleState();
            return true;
        }

        return false;
    }
}
