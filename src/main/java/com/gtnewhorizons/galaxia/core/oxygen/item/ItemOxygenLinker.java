package com.gtnewhorizons.galaxia.core.oxygen.item;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;

import com.gtnewhorizons.galaxia.core.oxygen.tile.TileEntityOxygenTether;

public class ItemOxygenLinker extends Item {

    private static final String STORED_X = "stored x";
    private static final String STORED_Y = "stored y";
    private static final String STORED_Z = "stored z";

    @Override
    public boolean onItemUseFirst(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side,
        float hitX, float hitY, float hitZ) {
        if (world.isRemote) {
            return false;
        }

        TileEntity te = world.getTileEntity(x, y, z);
        if (te == null) {
            return false;
        }
        // find me the tethers
        if (te instanceof TileEntityOxygenTether tether) {
            if (player.isSneaking()) {
                // create nbt tag if this doesnt have the tag
                if (!stack.hasTagCompound()) {
                    stack.setTagCompound(new NBTTagCompound());
                }
                // grab previous tag that either exists or is created
                NBTTagCompound tag = stack.getTagCompound();
                // add new coordinates to the tag
                tag.setInteger(STORED_X, tether.xCoord);
                tag.setInteger(STORED_Y, tether.yCoord);
                tag.setInteger(STORED_Z, tether.zCoord);

                player.addChatMessage(
                    new ChatComponentText(
                        "Stored tether at: " + tether.xCoord + ", " + tether.yCoord + ", " + tether.zCoord));

                return true;
            }
        }

        return false;
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {

        if (world.isRemote) {
            return stack;
        }

        //possible better way here ? not sure
        var mop = this.getMovingObjectPositionFromPlayer(world, player, true);
        if (mop != null) {
            TileEntity te = world.getTileEntity(mop.blockX, mop.blockY, mop.blockZ);
            if (te instanceof TileEntityOxygenTether) {
                return stack;
            }
        }

        if (player.isSneaking()) {

            if (stack.hasTagCompound()) {
                stack.setTagCompound(null);
                player.addChatMessage(new ChatComponentText("Cleared stored tether"));
            }
            return stack;
        }
        return stack;
    }

    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List tooltip, boolean advanced) {
        if (!stack.hasTagCompound()) {
            tooltip.add(EnumChatFormatting.RED + "No tether stored");
            return;
        }

        NBTTagCompound tag = stack.getTagCompound();
        // idk if this is the best way to do this because idt there is gonna be any other data but jsut to be safe
        if (tag.hasKey(STORED_X) && tag.hasKey(STORED_Y) && tag.hasKey(STORED_Z)) {
            int x = tag.getInteger(STORED_X);
            int y = tag.getInteger(STORED_Y);
            int z = tag.getInteger(STORED_Z);

            tooltip.add(EnumChatFormatting.BOLD + "" + EnumChatFormatting.GREEN + "Stored Tether:");
            tooltip.add("X: " + x);
            tooltip.add("Y: " + y);
            tooltip.add("Z: " + z);
        } else {
            tooltip.add("No tether stored");
        }
    }
}
