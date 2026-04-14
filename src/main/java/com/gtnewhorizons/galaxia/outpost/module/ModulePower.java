package com.gtnewhorizons.galaxia.outpost.module;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import com.gtnewhorizons.galaxia.outpost.AutomatedOutpost;

public class ModulePower extends AutomatedOutpostModule {

    public static final OutpostModuleKind KIND = OutpostModuleKind.BIG_HAMMER;

    // spotless:off
    public final static Map<ItemStack, Integer> constructionCost = new HashMap<ItemStack, Integer>() {{
        put(new ItemStack(Items.redstone), 8);
        put(new ItemStack(Items.gold_ingot), 64);
    }};
    // spotless:on

    public static final long BASE_ENERGY_CAPACITY = 1500L;
    public static final long GENERATION_EU_PER_TICK = 2048L;

    public ModulePower() {
        super(BASE_ENERGY_CAPACITY, 0, 1);
    }

    public static ModulePower getDefault() {
        return new ModulePower();
    }

    @Override
    public Map<ItemStack, Integer> getConstructionCost() {
        return constructionCost;
    }

    @Override
    public OutpostModuleKind getKind() {
        return KIND;
    }

    @Override
    protected void apply(AutomatedOutpost outpost) {
        outpost.addEnergy(GENERATION_EU_PER_TICK);
    }
}
