package com.gtnewhorizons.galaxia.quests;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import com.cleanroommc.modularui.drawable.ItemDrawable;
import com.gtnewhorizons.galaxia.registry.block.base.BlockVariant;
import com.gtnewhorizons.galaxia.registry.block.base.GalaxiaBlock;
import com.gtnewhorizons.galaxia.registry.dimension.DimensionEnum;
import com.gtnewhorizons.galaxia.registry.items.GalaxiaItemList;

public class QuestRegistry {

    public static final List<ResearchQuest> researchQuests = new ArrayList<>();

    public static void init() {
        // Theia Quests
        researchQuests.add(
            new ResearchQuest(
                Arrays.asList(
                    GalaxiaBlock.getBlockStack(DimensionEnum.THEIA, BlockVariant.REGOLITH.suffix(), 16),
                    GalaxiaBlock.getBlockStack(DimensionEnum.THEIA, BlockVariant.TEKTITE.suffix(), 16),
                    GalaxiaBlock.getBlockStack(DimensionEnum.THEIA, BlockVariant.BASALT.suffix(), 16)),
                new ItemStack(Items.golden_apple),
                "Theian Soil Sample",
                "Gather samples of Theian Regolith, Tektite, and Basalt",
                DimensionEnum.THEIA,
                new ItemDrawable()
                    .setItem(GalaxiaBlock.getBlockStack(DimensionEnum.THEIA, BlockVariant.REGOLITH.suffix(), 1))));
        researchQuests.add(
            new ResearchQuest(
                Arrays.asList(GalaxiaBlock.getBlockStack(DimensionEnum.THEIA, BlockVariant.MAGMA.suffix(), 1)),
                new ItemStack(Items.carrot),
                "Volcanic Depths",
                "Find an active volcanic chamber and retrieve a core sample",
                DimensionEnum.THEIA,
                new ItemDrawable()
                    .setItem(GalaxiaBlock.getBlockStack(DimensionEnum.THEIA, BlockVariant.MAGMA.suffix(), 1))));
        researchQuests.add(
            new ResearchQuest(
                Arrays.asList(new ItemStack(GalaxiaItemList.CINNABAR_BLOOD_SCALE.getItem(), 256)),
                new ItemStack(Items.carrot),
                "Crimson Moon",
                "Collect Cinnabar Blood Scales during a dangerous Blood Moon",
                DimensionEnum.THEIA,
                new ItemDrawable().setItem(GalaxiaItemList.CINNABAR_BLOOD_SCALE.getItem())));
    }
}
