package com.gtnewhorizons.galaxia.utility;

import java.lang.reflect.Method;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

import com.gtnewhorizons.galaxia.core.Galaxia;

public final class GTUtility {

    private GTUtility() {}

    /**
     * Gets a stack of the given ore material.
     * @param materialName The internal name of the GT material (e.g. "Iron")
     * @return The ore stack, or null if not found or GT not present.
     */
    public static ItemStack getOreStack(String materialName) {
        if (!Galaxia.hasGT5U()) return null;
        if (materialName == null || materialName.isEmpty()) return null;

        try {
            Class<?> materialsClass = Class.forName("gregtech.api.enums.Materials");
            Object material = materialsClass.getField(materialName).get(null);

            Class<?> orePrefixesClass = Class.forName("gregtech.api.enums.OrePrefixes");
            Object orePrefix = orePrefixesClass.getField("ore").get(null);

            Method getMethod = orePrefixesClass.getMethod("get", materialsClass);
            return (ItemStack) getMethod.invoke(orePrefix, material);
        } catch (Exception e) {
            Galaxia.LOG.error("Failed to get GT ore stack for material: " + materialName, e);
            return null;
        }
    }

    public static ItemStack getRawOreStack(String materialName) {
        if (materialName == null || materialName.isEmpty()) return null;

        String materialKey = materialName.replaceAll("[^A-Za-z0-9]", "");
        String[] oreDictKeys = new String[] { "raw" + materialKey, "crushed" + materialKey, "dustImpure" + materialKey,
            "ore" + materialKey, "gem" + materialKey, "dust" + materialKey };
        for (String oreDictKey : oreDictKeys) {
            List<ItemStack> matches = OreDictionary.getOres(oreDictKey, false);
            if (matches == null || matches.isEmpty()) continue;
            ItemStack match = matches.get(0);
            if (match != null) return match.copy();
        }
        return getOreStack(materialName);
    }
}
