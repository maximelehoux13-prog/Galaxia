package com.gtnewhorizons.galaxia.registry.outpost.module;

import com.gtnewhorizons.galaxia.registry.outpost.logistics.AllowShootingConfig;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalTransferPlanner;

public interface IHammer {

    long EU_PER_ITEM_PER_DV = 100L;

    AllowShootingConfig getConfig();

    void setConfig(AllowShootingConfig cfg);

    OrbitalTransferPlanner.RoutePriority getRoutePriority();

    void setPriority(OrbitalTransferPlanner.RoutePriority priority);

    boolean getPlanetaryHandling();

    boolean canFire();

    void fire();
}
