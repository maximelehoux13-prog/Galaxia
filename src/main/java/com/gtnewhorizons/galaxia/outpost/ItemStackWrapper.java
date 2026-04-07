package com.gtnewhorizons.galaxia.outpost;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.github.bsideup.jabel.Desugar;

import cpw.mods.fml.common.registry.GameRegistry;

/**
 * NBT-aware wrapper for ItemStack to be used as a key in HashMaps.
 */
@Desugar
public record ItemStackWrapper(Item item, int meta, NBTTagCompound nbt) {

    public static ItemStackWrapper of(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return null;
        }
        return new ItemStackWrapper(
                stack.getItem(),
                stack.getItemDamage(),
                stack.hasTagCompound() ? (NBTTagCompound) stack.getTagCompound().copy() : null);
    }

    public String toKey() {
        GameRegistry.UniqueIdentifier id = GameRegistry.findUniqueIdentifierFor(item);
        return id.modId + ":" + id.name + ":" + meta;
    }

    public static ItemStackWrapper fromKey(String key) {
        String[] parts = key.split(":");
        if (parts.length < 3) return null;
        try {
            Item item = GameRegistry.findItem(parts[0], parts[1]);
            int meta = Integer.parseInt(parts[2]);
            return new ItemStackWrapper(item, meta, null);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public ItemStack toStack(int amount) {
        ItemStack stack = new ItemStack(item, amount, meta);
        if (nbt != null) {
            stack.setTagCompound((NBTTagCompound) nbt.copy());
        }
        return stack;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ItemStackWrapper that = (ItemStackWrapper) o;
        if (meta != that.meta) return false;
        if (item != that.item) return false;
        if (nbt == null) return that.nbt == null;
        return nbt.equals(that.nbt);
    }

    @Override
    public int hashCode() {
        int result = item != null ? item.hashCode() : 0;
        result = 31 * result + meta;
        result = 31 * result + (nbt != null ? nbt.hashCode() : 0);
        return result;
    }
}
