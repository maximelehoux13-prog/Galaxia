package com.gtnewhorizons.galaxia.outpost;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

/**
 * Enumeration of all automated outpost module types.
 */
public enum OutpostModuleKind {

    HAMMER("HAMMER", 1000, 10),
    BIG_HAMMER("BIG HAMMER", 5000, 25),
    MINER("MINER", 2000, 128),
    POWER("POWER", 1500, 0);

    public final String displayName;
    public final long baseEnergyCapacity;
    /** EU consumed per tick while the module is operational. */
    public final int powerDrawEuPerTick;

    OutpostModuleKind(String displayName, long baseEnergyCapacity, int powerDrawEuPerTick) {
        this.displayName = displayName;
        this.baseEnergyCapacity = baseEnergyCapacity;
        this.powerDrawEuPerTick = powerDrawEuPerTick;
    }

    public Map<ItemStackWrapper, Integer> getRequiredResources() {
        Map<ItemStackWrapper, Integer> resources = new LinkedHashMap<>();
        switch (this) {
            case HAMMER:
                resources.put(ItemStackWrapper.of(new ItemStack(Items.iron_ingot)), 64);
                resources.put(ItemStackWrapper.of(new ItemStack(Items.gold_ingot)), 16);
                break;
            case BIG_HAMMER:
                resources.put(ItemStackWrapper.of(new ItemStack(Items.diamond)), 8);
                resources.put(ItemStackWrapper.of(new ItemStack(Items.gold_ingot)), 64);
                break;
            case MINER:
                resources.put(ItemStackWrapper.of(new ItemStack(Items.iron_ingot)), 128);
                resources.put(ItemStackWrapper.of(new ItemStack(Items.diamond)), 4);
                break;
            case POWER:
                resources.put(ItemStackWrapper.of(new ItemStack(Items.redstone)), 64);
                resources.put(ItemStackWrapper.of(new ItemStack(Items.iron_ingot)), 32);
                break;
        }
        return Collections.unmodifiableMap(resources);
    }
}
