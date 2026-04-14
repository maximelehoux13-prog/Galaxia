package com.gtnewhorizons.galaxia.outpost.module;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import com.gtnewhorizons.galaxia.outpost.AutomatedOutpost;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;

/**
 * A single module instance installed in an {@link AutomatedOutpost}.
 */
public abstract class AutomatedOutpostModule implements Buildable {

    // spotless:off
    public final static Map<ItemStack, Long> defaultConstructionCost = new HashMap<>() {{
        put(new ItemStack(Items.diamond), 8L);
        put(new ItemStack(Items.gold_ingot), 64L);
    }};

    // spotless:on
    public long energyBuffer;
    public final long baseEnergyCapacity;
    public int cooldownTicks;

    private int ticks = 0;
    private final long powerDrawEuPerTick;
    private Status status;
    private Map<ItemStack, Long> constructionResources;

    public AutomatedOutpostModule(long baseEnergyCapacity, long powerDrawEuPerTick, int cooldownTicks) {
        this.cooldownTicks = cooldownTicks;
        this.constructionResources = new HashMap<>();
        this.status = AutomatedOutpostModule.Status.IN_CONSTRUCTION;
        this.energyBuffer = 0L;

        this.baseEnergyCapacity = baseEnergyCapacity;
        this.powerDrawEuPerTick = powerDrawEuPerTick;
    }

    @Override
    public Status status() {
        return status;
    }

    @Override
    public void updateStatus(Buildable.Status status) {
        this.status = status;
    }

    public Status getLegacyStatus() {
        return status;
    }

    public void setLegacyStatus(Status status) {
        this.status = status;
    }

    public boolean isOperational() {
        return status == AutomatedOutpostModule.Status.OPERATIONAL;
    }

    public boolean isDisabled() {
        return status == AutomatedOutpostModule.Status.DISABLED;
    }

    public void setConstructionProgress(float progress) {}

    public Map<ItemStack, Long> getConsumedResources() {
        return constructionResources;
    }

    public void clearConsumedResources() {
        this.constructionResources.clear();
    }

    public float getConstructionProgress() {
        Map<ItemStack, Long> cost = getConstructionCost();

        if (cost.isEmpty() || status != AutomatedOutpostModule.Status.IN_CONSTRUCTION) {
            return 1.0f;
        }

        long totalRequired = 0;
        long totalCollected = 0;

        for (Map.Entry<ItemStack, Long> entry : cost.entrySet()) {
            ItemStack requiredItem = entry.getKey();
            long requiredAmount = entry.getValue();
            long collectedAmount = constructionResources.getOrDefault(requiredItem, 0L);

            totalRequired += requiredAmount;
            totalCollected += Math.min(collectedAmount, requiredAmount);
        }

        return (float) totalCollected / totalRequired;
    }

    public Map<ItemStack, Long> getConstructionCost() {
        return defaultConstructionCost;
    }

    @Override
    public Map<ItemStack, Long> getRequiredResources() {
        return getConstructionCost();
    }

    @Override
    public Map<ItemStack, Long> getConstructionInventory() {
        return constructionResources;
    }

    public abstract OutpostModuleKind getKind();

    public void completeConstructionInstantly() {
        constructionResources.clear();
        status = AutomatedOutpostModule.Status.OPERATIONAL;
        energyBuffer = baseEnergyCapacity;
    }

    public void tick(AutomatedOutpost outpost) {
        if (status == AutomatedOutpostModule.Status.IN_CONSTRUCTION) {
            updateConstruction(outpost);
        } else if (status == AutomatedOutpostModule.Status.OPERATIONAL) {
            updateOperational(outpost);
        }
    }

    public long getDisplayedPowerEuPerTick() {
        if (status != AutomatedOutpostModule.Status.OPERATIONAL) return 0L;
        return powerDrawEuPerTick;
    }

    private void updateConstruction(AutomatedOutpost outpost) {
        float constructionProgress = getConstructionProgress();
        if (constructionProgress >= 1.0f) {
            completeConstructionInstantly();
        }
    }

    private void updateOperational(AutomatedOutpost outpost) {
        if (powerDrawEuPerTick > 0 && !outpost.tryConsumeEnergy(powerDrawEuPerTick)) {
            // Lose progress
            this.ticks = 0;
            return;
        }

        this.ticks += 1;
        if (this.ticks >= this.cooldownTicks) {
            this.apply(outpost);
            this.ticks -= this.cooldownTicks;
        }
    }

    protected abstract void apply(AutomatedOutpost outpost);
}
