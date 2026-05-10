package com.gtnewhorizons.galaxia.registry.outpost.module;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import com.gtnewhorizons.galaxia.compat.recipe.GTRecipeChance;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacilityInventory;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeConfig;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeScheduler;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSchedulerMode;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSlot;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSnapshot;

public final class ProductionModuleHelper {

    private static final ItemStackWrapper[] EMPTY_WRAPPERS = new ItemStackWrapper[0];

    private ProductionModuleHelper() {}

    public static void execute(ModuleInstance instance, AutomatedFacility outpost, IRecipeModule recipeModule,
        Random random, Map<RecipeSnapshot, ItemStackWrapper[]> inputWrapperCache,
        Map<RecipeSnapshot, ItemStackWrapper[]> outputWrapperCache) {
        RecipeConfig config = recipeModule.getRecipeConfig();
        if (config == null) return;

        int slotIdx = recipeModule.getNextSlot(random);
        if (slotIdx < 0) return;

        RecipeSlot slot = config.slots()
            .get(slotIdx);
        RecipeSnapshot recipe = slot.recipe();

        AutomatedFacilityInventory inv = outpost.inventory;
        ItemStack[] inputs = recipe.inputs();
        ItemStack[] outputs = recipe.outputs();
        int[] outputChances = recipe.outputChances();
        FluidStack[] fluidInputs = recipe.fluidInputs();
        FluidStack[] fluidOutputs = recipe.fluidOutputs();
        int[] fluidOutputChances = recipe.fluidOutputChances();

        ItemStackWrapper[] inputWrappers = cachedWrappers(inputWrapperCache, recipe, inputs);

        Map<ItemStackWrapper, Long> requiredInputs = requiredInputs(inputWrappers, inputs);
        for (Map.Entry<ItemStackWrapper, Long> e : requiredInputs.entrySet()) {
            if (inv.getAmount(e.getKey()) - e.getValue() < slot.bounds()
                .inputItemLowerBound(e.getKey())) {
                advanceScheduler(config, recipeModule);
                return;
            }
        }

        Map<String, Long> requiredFluidInputs = requiredFluidInputs(fluidInputs);
        for (Map.Entry<String, Long> e : requiredFluidInputs.entrySet()) {
            if (inv.getFluidAmount(e.getKey()) - e.getValue() < slot.bounds()
                .inputFluidLowerBound(e.getKey())) {
                advanceScheduler(config, recipeModule);
                return;
            }
        }

        ItemStackWrapper[] outputWrappers = cachedWrappers(outputWrapperCache, recipe, outputs);
        Map<ItemStackWrapper, Long> selectedOutputs = selectedOutputs(outputWrappers, outputs, outputChances, random);
        Map<String, Long> selectedFluidOutputs = selectedFluidOutputs(fluidOutputs, fluidOutputChances, random);
        if (!allowsItemOutputs(inv, selectedOutputs, slot)) {
            advanceScheduler(config, recipeModule);
            return;
        }
        if (!allowsFluidOutputs(inv, selectedFluidOutputs, slot)) {
            advanceScheduler(config, recipeModule);
            return;
        }
        if (!canFitSelectedItemOutputs(outpost, selectedOutputs, requiredInputs)) {
            advanceScheduler(config, recipeModule);
            return;
        }

        // Consume inputs
        for (Map.Entry<ItemStackWrapper, Long> e : requiredInputs.entrySet()) {
            outpost.addInventory(e.getKey(), -e.getValue());
        }

        if (fluidInputs != null) {
            for (FluidStack fluid : fluidInputs) {
                String fluidName = fluidName(fluid);
                if (fluidName != null) inv.addFluid(fluidName, -fluid.amount);
            }
        }

        // Produce outputs
        for (Map.Entry<ItemStackWrapper, Long> e : selectedOutputs.entrySet()) {
            outpost.insertInventory(e.getKey(), e.getValue());
        }

        if (fluidOutputs != null) {
            for (Map.Entry<String, Long> e : selectedFluidOutputs.entrySet()) {
                inv.addFluid(e.getKey(), e.getValue());
            }
        }

        if (config.mode() == RecipeSchedulerMode.ORDER) {
            recipeModule.setRecipeConfig(RecipeScheduler.advanceOrder(config));
        }
    }

    private static ItemStackWrapper[] cachedWrappers(Map<RecipeSnapshot, ItemStackWrapper[]> cache,
        RecipeSnapshot recipe, ItemStack[] stacks) {
        ItemStackWrapper[] cached = cache.get(recipe);
        if (cached != null) return cached;
        if (stacks == null) {
            cache.put(recipe, EMPTY_WRAPPERS);
            return EMPTY_WRAPPERS;
        }
        ItemStackWrapper[] wrappers = new ItemStackWrapper[stacks.length];
        for (int i = 0; i < stacks.length; i++) {
            wrappers[i] = stacks[i] != null ? ItemStackWrapper.of(stacks[i]) : null;
        }
        cache.put(recipe, wrappers);
        return wrappers;
    }

    private static Map<ItemStackWrapper, Long> requiredInputs(ItemStackWrapper[] wrappers, ItemStack[] stacks) {
        Map<ItemStackWrapper, Long> required = new LinkedHashMap<>();
        if (stacks == null) return required;
        for (int i = 0; i < wrappers.length && i < stacks.length; i++) {
            if (wrappers[i] == null || stacks[i] == null || stacks[i].stackSize <= 0) continue;
            required.merge(wrappers[i], (long) stacks[i].stackSize, Long::sum);
        }
        return required;
    }

    private static Map<String, Long> requiredFluidInputs(FluidStack[] stacks) {
        Map<String, Long> required = new LinkedHashMap<>();
        if (stacks == null) return required;
        for (FluidStack stack : stacks) {
            String fluidName = fluidName(stack);
            if (fluidName == null || stack.amount <= 0) continue;
            required.merge(fluidName, (long) stack.amount, Long::sum);
        }
        return required;
    }

    private static boolean allowsItemOutputs(AutomatedFacilityInventory inv, Map<ItemStackWrapper, Long> outputs,
        RecipeSlot slot) {
        for (Map.Entry<ItemStackWrapper, Long> entry : outputs.entrySet()) {
            if (!slot.bounds()
                .hasOutputItemUpperBound(entry.getKey())) continue;
            long upperBound = slot.bounds()
                .outputItemUpperBound(entry.getKey());
            if (inv.getAmount(entry.getKey()) + entry.getValue() > upperBound) return false;
        }
        return true;
    }

    private static boolean canFitSelectedItemOutputs(AutomatedFacility outpost, Map<ItemStackWrapper, Long> outputs,
        Map<ItemStackWrapper, Long> inputs) {
        long outputAmount = 0L;
        for (long amount : outputs.values()) {
            outputAmount += amount;
        }
        if (outputAmount <= 0L) return true;
        long freedByInputs = 0L;
        for (long amount : inputs.values()) {
            freedByInputs += amount;
        }
        return outputAmount <= outpost.remainingItemInventoryCapacity() + freedByInputs;
    }

    private static boolean allowsFluidOutputs(AutomatedFacilityInventory inv, Map<String, Long> outputs,
        RecipeSlot slot) {
        for (Map.Entry<String, Long> entry : outputs.entrySet()) {
            if (!slot.bounds()
                .hasOutputFluidUpperBound(entry.getKey())) continue;
            long upperBound = slot.bounds()
                .outputFluidUpperBound(entry.getKey());
            if (inv.getFluidAmount(entry.getKey()) + entry.getValue() > upperBound) return false;
        }
        return true;
    }

    private static Map<ItemStackWrapper, Long> selectedOutputs(ItemStackWrapper[] wrappers, ItemStack[] stacks,
        int[] chances, Random random) {
        Map<ItemStackWrapper, Long> selected = new LinkedHashMap<>();
        if (stacks == null) return selected;
        for (int i = 0; i < wrappers.length && i < stacks.length; i++) {
            if (wrappers[i] == null || stacks[i] == null || stacks[i].stackSize <= 0) continue;
            if (!shouldProduceOutput(chances, i, random)) continue;
            selected.merge(wrappers[i], (long) stacks[i].stackSize, Long::sum);
        }
        return selected;
    }

    private static Map<String, Long> selectedFluidOutputs(FluidStack[] stacks, int[] chances, Random random) {
        Map<String, Long> selected = new LinkedHashMap<>();
        if (stacks == null) return selected;
        for (int i = 0; i < stacks.length; i++) {
            FluidStack stack = stacks[i];
            String fluidName = fluidName(stack);
            if (fluidName == null || stack.amount <= 0) continue;
            if (!shouldProduceOutput(chances, i, random)) continue;
            selected.merge(fluidName, (long) stack.amount, Long::sum);
        }
        return selected;
    }

    private static boolean shouldProduceOutput(int[] chances, int index, Random random) {
        return GTRecipeChance.shouldProduce(chances, index, random);
    }

    private static void advanceScheduler(RecipeConfig config, IRecipeModule recipeModule) {
        if (config.mode() == RecipeSchedulerMode.ORDER) {
            recipeModule.setRecipeConfig(RecipeScheduler.advanceOrder(config));
        }
    }

    private static String fluidName(FluidStack stack) {
        if (stack == null) return null;
        Fluid fluid = fluidType(stack);
        return fluid != null ? fluid.getName() : null;
    }

    private static Fluid fluidType(FluidStack stack) {
        try {
            return stack.getFluid();
        } catch (RuntimeException ignored) {
            try {
                var field = FluidStack.class.getDeclaredField("fluid");
                field.setAccessible(true);
                return (Fluid) field.get(stack);
            } catch (ReflectiveOperationException e) {
                return null;
            }
        }
    }
}
