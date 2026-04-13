package com.gtnewhorizons.galaxia.outpost.module;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.galaxia.outpost.AutomatedOutpostModule;
import com.gtnewhorizons.galaxia.outpost.AutomatedOutpostState;
import com.gtnewhorizons.galaxia.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.outpost.logistics.AllowShootingConfig;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalTransferPlanner;

/**
 * Static configuration data for a {@link OutpostModuleKind#BIG_HAMMER} module.
 *
 * <p>
 * BIG_HAMMER operates within the same stellar system as the host outpost without
 * range restrictions or batch-size limits.
 */
@Desugar
public record BigHammerModuleData(boolean planetaryTransferHandling, AllowShootingConfig allowShooting,
    OrbitalTransferPlanner.RoutePriority routePriority) implements OutpostModuleData {

    public static final long BASE_ENERGY_CAPACITY = 5000L;
    public static final int POWER_DRAW_EU_PER_TICK = 25;

    public BigHammerModuleData() {
        this(false, AllowShootingConfig.ALWAYS, OrbitalTransferPlanner.RoutePriority.PRIORITIZE_TOF);
    }

    public static BigHammerModuleData getDefault() {
        return new BigHammerModuleData(
            false,
            AllowShootingConfig.ALWAYS,
            OrbitalTransferPlanner.RoutePriority.PRIORITIZE_TOF);
    }

    @Override
    public OutpostModuleKind moduleKind() {
        return OutpostModuleKind.BIG_HAMMER;
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
        resources.put(ItemStackWrapper.of(new ItemStack(Items.diamond)), 8);
        resources.put(ItemStackWrapper.of(new ItemStack(Items.gold_ingot)), 64);
        return resources;
    }

    @Override
    public void tick(AutomatedOutpostModule module, AutomatedOutpostState outpost) {
        if (module.cooldownTicks > 0) {
            module.cooldownTicks--;
            return;
        }
        module.cooldownTicks = COOLDOWN_TICKS;
    }

    public AllowShootingConfig effectiveShooting() {
        return allowShooting != null ? allowShooting : AllowShootingConfig.ALWAYS;
    }

    public OrbitalTransferPlanner.RoutePriority effectiveRoutePriority() {
        return routePriority != null ? routePriority : OrbitalTransferPlanner.RoutePriority.PRIORITIZE_TOF;
    }

    public static final int COOLDOWN_TICKS = 20;
}
