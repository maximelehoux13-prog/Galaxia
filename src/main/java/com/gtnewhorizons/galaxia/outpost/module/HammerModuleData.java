package com.gtnewhorizons.galaxia.outpost.module;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.galaxia.outpost.AutomatedOutpostModule;
import com.gtnewhorizons.galaxia.outpost.AutomatedOutpostState;
import com.gtnewhorizons.galaxia.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.outpost.OutpostModuleKind;
import com.gtnewhorizons.galaxia.outpost.logistics.AllowShootingConfig;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalTransferPlanner;

/**
 * Static configuration data for a {@link com.gtnewhorizons.galaxia.outpost.OutpostModuleKind#HAMMER} module.
 *
 * <p>
 * Operational constants:
 * <ul>
 * <li>EU cost: 100 EU × departure-dV × items transferred.</li>
 * <li>Max batch size: 64 items per {@code LogisticsTask}.</li>
 * <li>Cooldown: 20 ticks (1 second) between operations.</li>
 * </ul>
 *
 * <p>
 * {@code allowShooting} may be {@code null} in saves created before this field
 * was introduced; use {@link #effectiveShooting()} which defaults to
 * {@link AllowShootingConfig#ALWAYS}.
 */
@Desugar
public record HammerModuleData(AllowShootingConfig allowShooting, OrbitalTransferPlanner.RoutePriority routePriority)
    implements OutpostModuleData {

    public static final long BASE_ENERGY_CAPACITY = 1000L;
    public static final int POWER_DRAW_EU_PER_TICK = 10;

    public static HammerModuleData getDefault() {
        return new HammerModuleData(AllowShootingConfig.ALWAYS, OrbitalTransferPlanner.RoutePriority.PRIORITIZE_TOF);
    }

    @Override
    public OutpostModuleKind moduleKind() {
        return OutpostModuleKind.HAMMER;
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
        resources.put(ItemStackWrapper.of(new ItemStack(Items.iron_ingot)), 64);
        resources.put(ItemStackWrapper.of(new ItemStack(Items.gold_ingot)), 16);
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

    public static final int MAX_BATCH_SIZE = 64;
    public static final int COOLDOWN_TICKS = 20;
}
