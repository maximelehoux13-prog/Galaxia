package com.gtnewhorizons.galaxia.registry.outpost.module;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import com.gtnewhorizons.galaxia.api.GalaxiaCelestialAPI;
import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedOutpost;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;

import cpw.mods.fml.common.registry.GameRegistry;

public class ModuleMiner extends AutomatedOutpostModule {

    public static final OutpostModuleKind KIND = OutpostModuleKind.MINER;

    // spotless:off
    public final static Map<ItemStack, Long> constructionCost = new HashMap<>() {{
        put(new ItemStack(Items.diamond), 8L);
        put(new ItemStack(Items.gold_ingot), 64L);
    }};
    // spotless:on

    public List<String> blacklistedItemKeys;

    public static final long BASE_ENERGY_CAPACITY = 2000L;
    public static final int POWER_DRAW_EU_PER_TICK = 128;
    public static final int COOLDOWN_TICKS = 20;

    @Deprecated
    private boolean copySettingsToOtherMiners = false;

    @Override
    public void apply(AutomatedOutpost outpost) {
        GalaxiaCelestialAPI.get(outpost.celestialObjectId)
            .ifPresent(registration -> {
                List<ItemStack> ores = registration.properties()
                    .ores();
                if (ores.isEmpty()) return;
                ItemStack chosen = ores.get(RANDOM.nextInt(ores.size()));
                if (isBlacklisted(chosen)) return;
                ItemStack ore = chosen.copy();
                ore.stackSize = 1;
                outpost.inventory.add(ItemStackWrapper.of(ore), 1);
            });
    }

    private static final java.util.Random RANDOM = new java.util.Random();

    @Deprecated
    public ModuleMiner(List<String> blacklistedItemKeys) {
        super(BASE_ENERGY_CAPACITY, POWER_DRAW_EU_PER_TICK, COOLDOWN_TICKS);
        this.blacklistedItemKeys = blacklistedItemKeys;
    }

    public static ModuleMiner getDefault() {
        return new ModuleMiner(Collections.emptyList());
    }

    // TODO: This is just for compat
    public boolean isBlacklisted(ItemStack item) {
        GameRegistry.UniqueIdentifier id = GameRegistry.findUniqueIdentifierFor(item.getItem());
        if (id == null) {
            Galaxia.LOG.warn(
                "[ItemStackWrapper] Item {} has no registry entry; key will not resolve on reload.",
                item.getClass()
                    .getName());
            return true;
        }
        return blacklistedItemKeys.contains(id.modId + ":" + id.name + ":" + item.getItemDamage());
    }

    @Deprecated
    public boolean isBlacklisted(@Nonnull String item) {
        return blacklistedItemKeys.contains(item);
    }

    public ModuleMiner withAddedBlacklist(String itemKey) {
        if (itemKey == null || itemKey.isEmpty() || blacklistedItemKeys.contains(itemKey)) return this;
        blacklistedItemKeys.add(itemKey);
        return this;
    }

    public ModuleMiner withRemovedBlacklist(String itemKey) {
        if (itemKey == null || itemKey.isEmpty() || !blacklistedItemKeys.contains(itemKey)) return this;
        blacklistedItemKeys.remove(itemKey);
        return this;
    }

    @Deprecated
    public ModuleMiner withCopySettingsToOtherMiners(boolean enabled) {
        this.copySettingsToOtherMiners = enabled;
        return this;
    }

    @Deprecated
    public boolean getCopySettingsToOtherMiners() {
        return this.copySettingsToOtherMiners;
    }

    @Override
    public Map<ItemStack, Long> getConstructionCost() {
        return constructionCost;
    }

    @Override
    public OutpostModuleKind getKind() {
        return KIND;
    }
}
