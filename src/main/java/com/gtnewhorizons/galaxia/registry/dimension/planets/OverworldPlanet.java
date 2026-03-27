package com.gtnewhorizons.galaxia.registry.dimension.planets;

import com.gtnewhorizons.galaxia.registry.dimension.DimensionEnum;
import com.gtnewhorizons.galaxia.registry.dimension.builder.DimensionBuilder;
import com.gtnewhorizons.galaxia.rocketmodules.rocket.EnumTiers;

/**
 * A "Planet" class for the overworld. Does not get registered as a dimension,
 * but made this way for consistency in calculations with other planets
 */
public class OverworldPlanet extends BasePlanet {

    public static final DimensionEnum ENUM = DimensionEnum.OVERWORLD;

    @Override
    public DimensionEnum getPlanetEnum() {
        return ENUM;
    }

    @Override
    protected DimensionBuilder customizeDimension(DimensionBuilder builder) {
        return builder.mass(1)
            .radius(1)
            .orbitalRadius(1 * earthRadiusToAU)
            .tier(EnumTiers.TIER_1);
    }

}
