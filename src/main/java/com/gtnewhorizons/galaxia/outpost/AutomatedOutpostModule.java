package com.gtnewhorizons.galaxia.outpost;

import java.util.LinkedHashMap;
import java.util.Map;

import com.gtnewhorizons.galaxia.outpost.module.OutpostModuleData;

/**
 * A single module instance installed in an {@link AutomatedOutpostState}.
 */
public final class AutomatedOutpostModule {

    public enum Status {
        IN_CONSTRUCTION,
        OPERATIONAL,
        DISABLED
    }

    private OutpostModuleData data;

    private Status status;

    private float constructionProgress;

    private final Map<ItemStackWrapper, Integer> consumedResources = new LinkedHashMap<>();

    public int cooldownTicks;

    public long energyBuffer;

    public AutomatedOutpostModule(OutpostModuleData data) {
        this.data = data;
        this.status = Status.IN_CONSTRUCTION;
        this.constructionProgress = 0.0f;
        this.cooldownTicks = 0;
        this.energyBuffer = 0L;
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

    public OutpostModuleData getData() {
        return data;
    }

    public void setData(OutpostModuleData data) {
        this.data = data;
    }

    public float getConstructionProgress() {
        return constructionProgress;
    }

    public void setConstructionProgress(float constructionProgress) {
        this.constructionProgress = constructionProgress;
    }

    public Map<ItemStackWrapper, Integer> getConsumedResources() {
        return consumedResources;
    }

    public void clearConsumedResources() {
        consumedResources.clear();
    }

    public void completeConstructionInstantly() {
        consumedResources.clear();
        for (Map.Entry<ItemStackWrapper, Integer> entry : data.requiredResources().entrySet()) {
            consumedResources.put(entry.getKey(), entry.getValue());
        }
        status = Status.OPERATIONAL;
        constructionProgress = 1.0f;
        cooldownTicks = 0;
        energyBuffer = data.baseEnergyCapacity();
    }

    public void tick(AutomatedOutpostState outpost) {
        if (status == Status.IN_CONSTRUCTION) {
            updateConstruction(outpost);
        } else if (status == Status.OPERATIONAL) {
            updateOperational(outpost);
        }
    }

    private void updateConstruction(AutomatedOutpostState outpost) {
        Map<ItemStackWrapper, Integer> requirements = data.requiredResources();
        boolean finished = true;
        int totalNeeded = 0;
        int totalConsumed = 0;

        for (Map.Entry<ItemStackWrapper, Integer> entry : requirements.entrySet()) {
            ItemStackWrapper item = entry.getKey();
            int required = entry.getValue();
            int consumed = consumedResources.getOrDefault(item, 0);

            totalNeeded += required;
            totalConsumed += consumed;

            if (consumed < required) {
                finished = false;
                if (outpost.inventory.tryConsume(item, 1)) {
                    consumedResources.put(item, consumed + 1);
                    totalConsumed++;
                }
            }
        }

        if (totalNeeded > 0) {
            constructionProgress = (float) totalConsumed / totalNeeded;
        } else {
            constructionProgress = 1.0f;
        }

        if (finished) {
            status = Status.OPERATIONAL;
            constructionProgress = 1.0f;
            energyBuffer = data.baseEnergyCapacity();
        }
    }

    private void updateOperational(AutomatedOutpostState outpost) {
        int powerDraw = data.powerDrawEuPerTick();
        if (powerDraw > 0 && !outpost.tryConsumeEnergy(powerDraw)) {
            return;
        }
        data.tick(this, outpost);
    }

    public long getDisplayedPowerEuPerTick() {
        if (status != Status.OPERATIONAL) return 0L;
        return data.powerDrawEuPerTick();
    }
}