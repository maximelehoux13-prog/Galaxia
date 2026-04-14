package com.gtnewhorizons.galaxia.outpost.module;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.item.ItemStack;

import com.gtnewhorizons.galaxia.outpost.AutomatedOutpost;

/**
 * A single module instance installed in an {@link AutomatedOutpost}.
 */
public abstract class AutomatedOutpostModule {

    public enum Status {
        IN_CONSTRUCTION,
        OPERATIONAL,
        DISABLED
    }

    public long energyBuffer;
    public final long baseEnergyCapacity;
    public int cooldownTicks;

    private int ticks = 0;
    private final long powerDrawEuPerTick;
    private Status status;
    private Map<ItemStack, Integer> constructionResources;

    public AutomatedOutpostModule(long baseEnergyCapacity, long powerDrawEuPerTick, int cooldownTicks) {
        this.cooldownTicks = cooldownTicks;
        this.constructionResources = new HashMap<>();
        this.status = Status.IN_CONSTRUCTION;
        this.energyBuffer = 0L;

        this.baseEnergyCapacity = baseEnergyCapacity;
        this.powerDrawEuPerTick = powerDrawEuPerTick;
    }

    public Status getStatus() {
        return status;
    }

    public boolean isOperational() {
        return status == Status.OPERATIONAL;
    }

    public boolean isDisabled() {
        return status == Status.DISABLED;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public void setConstructionProgress(float progress) {}

    public Map<ItemStack, Integer> getConsumedResources() {
        return constructionResources;
    }

    public void clearConsumedResources() {
        this.constructionResources.clear();
    }

    public float getConstructionProgress() {
        Map<ItemStack, Integer> cost = getConstructionCost();

        if (cost.isEmpty() || status != Status.IN_CONSTRUCTION) {
            return 1.0f;
        }

        int totalRequired = 0;
        int totalCollected = 0;

        for (Map.Entry<ItemStack, Integer> entry : cost.entrySet()) {
            ItemStack requiredItem = entry.getKey();
            int requiredAmount = entry.getValue();

            int collectedAmount = constructionResources.getOrDefault(requiredItem, 0);

            totalRequired += requiredAmount;
            totalCollected += Math.min(collectedAmount, requiredAmount);
        }

        return (float) totalCollected / totalRequired;
    }

    public abstract Map<ItemStack, Integer> getConstructionCost();

    public abstract OutpostModuleKind getKind();

    public void completeConstructionInstantly() {
        constructionResources.clear();
        status = Status.OPERATIONAL;
        energyBuffer = baseEnergyCapacity;
    }

    public void tick(AutomatedOutpost outpost) {
        if (status == Status.IN_CONSTRUCTION) {
            updateConstruction(outpost);
        } else if (status == Status.OPERATIONAL) {
            updateOperational(outpost);
        }
    }

    public long getDisplayedPowerEuPerTick() {
        if (status != Status.OPERATIONAL) return 0L;
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
