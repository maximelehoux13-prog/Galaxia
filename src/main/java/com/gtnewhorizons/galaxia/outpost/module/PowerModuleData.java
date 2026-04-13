package com.gtnewhorizons.galaxia.outpost.module;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.galaxia.outpost.AutomatedOutpostModule;
import com.gtnewhorizons.galaxia.outpost.AutomatedOutpostState;
import com.gtnewhorizons.galaxia.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.outpost.OutpostModuleKind;

/**
 * Static configuration data for a {@link com.gtnewhorizons.galaxia.outpost.OutpostModuleKind#POWER} module.
 */
@Desugar
public record PowerModuleData() implements OutpostModuleData {

    public static final long BASE_ENERGY_CAPACITY = 1500L;
    public static final int POWER_GENERATION_EU_PER_TICK = 2048;
    public static final long GENERATION_EU_PER_TICK = 2048L;

    @Override
    public OutpostModuleKind moduleKind() {
        return OutpostModuleKind.POWER;
    }

    @Override
    public long baseEnergyCapacity() {
        return BASE_ENERGY_CAPACITY;
    }

    @Override
    public int powerDrawEuPerTick() {
        return -POWER_GENERATION_EU_PER_TICK;
    }

    @Override
    public Map<ItemStackWrapper, Integer> requiredResources() {
        Map<ItemStackWrapper, Integer> resources = new LinkedHashMap<>();
        resources.put(ItemStackWrapper.of(new ItemStack(Items.redstone)), 64);
        resources.put(ItemStackWrapper.of(new ItemStack(Items.iron_ingot)), 32);
        return resources;
    }

    @Override
    public void tick(AutomatedOutpostModule module, AutomatedOutpostState outpost) {
        outpost.addEnergy(GENERATION_EU_PER_TICK);
    }
}
