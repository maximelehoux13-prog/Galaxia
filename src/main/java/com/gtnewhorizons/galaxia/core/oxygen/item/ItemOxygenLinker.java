package com.gtnewhorizons.galaxia.core.oxygen.item;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.gtnewhorizons.galaxia.core.oxygen.tile.TileEntityOxygenTether;

public class ItemOxygenLinker extends Item {

    @Override
    public boolean onItemUseFirst(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side,
        float hitX, float hitY, float hitZ) {
        if (world.isRemote) {
            return false;
        }

        TileEntity te = world.getTileEntity(x, y, z);
        if (te != null) {
            return false;
        }
        //find me the tethers
        if (te instanceof TileEntityOxygenTether tether) {
            System.out.println(tether.xCoord + " " + tether.yCoord + " " + tether.zCoord);
            return true;

        }

        return false;
    }

}


