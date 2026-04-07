package com.gtnewhorizons.galaxia.outpost.module;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.github.bsideup.jabel.Desugar;

/**
 * Static configuration data for a {@link com.gtnewhorizons.galaxia.outpost.OutpostModuleKind#MINER} module.
 */
@Desugar
public record MinerModuleData(List<String> blacklistedItemKeys) implements OutpostModuleData {

    public MinerModuleData() {
        this(Collections.emptyList());
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
        return new MinerModuleData(updated);
    }

    public MinerModuleData withRemovedBlacklist(String itemKey) {
        if (itemKey == null || itemKey.isEmpty() || !blacklistedItemKeys.contains(itemKey)) return this;
        ArrayList<String> updated = new ArrayList<>(blacklistedItemKeys);
        updated.remove(itemKey);
        return new MinerModuleData(updated);
    }
}
