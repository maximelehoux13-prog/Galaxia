package com.gtnewhorizons.galaxia.quests;

import java.util.List;

import net.minecraft.item.ItemStack;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.gtnewhorizons.galaxia.registry.dimension.DimensionEnum;

public class ResearchQuest {

    List<ItemStack> inputs;
    ItemStack output;
    IDrawable icon;
    String name;
    String desc;
    DimensionEnum planet;

    public ResearchQuest(List<ItemStack> inputs, ItemStack output, String name, String desc, DimensionEnum planet,
        IDrawable icon) {
        this.inputs = inputs;
        this.output = output;
        this.name = name;
        this.desc = desc;
        this.planet = planet;
        this.icon = icon;
    }
}
