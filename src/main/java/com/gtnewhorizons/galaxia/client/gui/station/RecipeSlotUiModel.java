package com.gtnewhorizons.galaxia.client.gui.station;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeConfig;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSchedulerMode;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSlot;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSnapshot;

final class RecipeSlotUiModel {

    static final int MAX_GUARD = 999_999;
    static final int MAX_BYTE_SETTING = Byte.MAX_VALUE;

    private RecipeSlotUiModel() {}

    static String modeLabel(@Nullable RecipeConfig config) {
        RecipeSchedulerMode mode = config != null ? config.mode()
            : RecipeConfig.empty()
                .mode();
        return "Mode: " + mode.name();
    }

    static RecipeSchedulerMode nextMode(@Nullable RecipeConfig config) {
        RecipeSchedulerMode current = config != null ? config.mode()
            : RecipeConfig.empty()
                .mode();
        RecipeSchedulerMode[] values = RecipeSchedulerMode.values();
        return values[(current.ordinal() + 1) % values.length];
    }

    static String slotTitle(RecipeSlot slot) {
        RecipeSnapshot recipe = slot.recipe();
        String output = itemSummary(recipe.outputs());
        if (output != null) return output;
        output = fluidSummary(recipe.fluidOutputs());
        if (output != null) return output;
        String input = itemSummary(recipe.inputs());
        if (input != null) return input;
        input = fluidSummary(recipe.fluidInputs());
        if (input != null) return input;
        return "Recipe #" + recipe.recipeIndex();
    }

    static int parseIntOrCurrent(String text, int current, int min, int max) {
        int parsed;
        try {
            parsed = Integer.parseInt(text);
        } catch (NumberFormatException ignored) {
            parsed = current;
        }
        return Math.max(min, Math.min(max, parsed));
    }

    private static @Nullable String itemSummary(@Nullable ItemStack[] stacks) {
        if (stacks == null) return null;
        for (ItemStack stack : stacks) {
            if (stack == null || stack.getItem() == null) continue;
            String name = stack.getDisplayName();
            if (name == null || name.isBlank()) continue;
            return stack.stackSize > 1 ? stack.stackSize + "x " + name : name;
        }
        return null;
    }

    private static @Nullable String fluidSummary(@Nullable FluidStack[] stacks) {
        if (stacks == null) return null;
        for (FluidStack stack : stacks) {
            if (stack == null || stack.amount <= 0) continue;
            String name = fluidName(stack);
            if (name == null || name.isBlank()) continue;
            return stack.amount + "L " + name;
        }
        return null;
    }

    private static @Nullable String fluidName(FluidStack stack) {
        try {
            Fluid fluid = stack.getFluid();
            return fluid != null ? fluid.getName() : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
