package com.gtnewhorizons.galaxia.outpost.module;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.galaxia.outpost.AutomatedOutpostModule;
import com.gtnewhorizons.galaxia.outpost.AutomatedOutpostState;
import com.gtnewhorizons.galaxia.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.outpost.OutpostModuleKind;

/**
 * Static configuration data for a {@link com.gtnewhorizons.galaxia.outpost.OutpostModuleKind#MINER} module.
 */
@Desugar
public record MinerModuleData(List<String> blacklistedItemKeys, boolean copySettingsToOtherMiners)
    implements OutpostModuleData {

    public static final long BASE_ENERGY_CAPACITY = 2000L;
    public static final int POWER_DRAW_EU_PER_TICK = 128;

    @Override
    public OutpostModuleKind moduleKind() {
        return OutpostModuleKind.MINER;
    }

    @Override
    public long baseEnergyCapacity() {
        return BASE_ENERGY_CAPACITY;
    }

    @Override
    public int powerDrawEuPerTick() {
        return POWER_DRAW_EU_PER_TICK;
    }

    @Override
    public Map<ItemStackWrapper, Integer> requiredResources() {
        Map<ItemStackWrapper, Integer> resources = new LinkedHashMap<>();
        resources.put(ItemStackWrapper.of(new ItemStack(Items.iron_ingot)), 128);
        resources.put(ItemStackWrapper.of(new ItemStack(Items.diamond)), 4);
        return resources;
    }

    @Override
    public void tick(AutomatedOutpostModule module, AutomatedOutpostState outpost) {
        if (module.cooldownTicks > 0) {
            module.cooldownTicks--;
            return;
        }
        module.cooldownTicks = COOLDOWN_TICKS;

        com.gtnewhorizons.galaxia.api.GalaxiaCelestialAPI.get(outpost.celestialBodyId)
            .ifPresent(registration -> {
                List<net.minecraft.item.ItemStack> ores = registration.properties().ores();
                if (ores.isEmpty()) return;
                net.minecraft.item.ItemStack chosen = ores.get(RANDOM.nextInt(ores.size()));
                ItemStackWrapper wrapper = ItemStackWrapper.of(chosen);
                if (wrapper == null || isBlacklisted(wrapper.toKey())) return;
                net.minecraft.item.ItemStack ore = chosen.copy();
                ore.stackSize = 1;
                outpost.inventory.add(ItemStackWrapper.of(ore), 1);
            });
    }

    public static final int COOLDOWN_TICKS = 20;
    private static final java.util.Random RANDOM = new java.util.Random();

    public MinerModuleData() {
        this(Collections.emptyList(), false);
    }

    public MinerModuleData {
        List<String> safeKeys = blacklistedItemKeys == null ? Collections.emptyList() : blacklistedItemKeys;
        blacklistedItemKeys = Collections.unmodifiableList(new ArrayList<>(safeKeys));
    }

    public boolean isBlacklisted(String itemKey) {
        return itemKey != null && blacklistedItemKeys.contains(itemKey);
    }

    public MinerModuleData withAddedBlacklist(String itemKey) {
        if (itemKey == null || itemKey.isEmpty() || blacklistedItemKeys.contains(itemKey)) return this;
        ArrayList<String> updated = new ArrayList<>(blacklistedItemKeys);
        updated.add(itemKey);
        return new MinerModuleData(updated, copySettingsToOtherMiners);
    }

    public MinerModuleData withRemovedBlacklist(String itemKey) {
        if (itemKey == null || itemKey.isEmpty() || !blacklistedItemKeys.contains(itemKey)) return this;
        ArrayList<String> updated = new ArrayList<>(blacklistedItemKeys);
        updated.remove(itemKey);
        return new MinerModuleData(updated, copySettingsToOtherMiners);
    }

    public MinerModuleData withCopySettingsToOtherMiners(boolean enabled) {
        return enabled == copySettingsToOtherMiners ? this : new MinerModuleData(blacklistedItemKeys, enabled);
    }
}
