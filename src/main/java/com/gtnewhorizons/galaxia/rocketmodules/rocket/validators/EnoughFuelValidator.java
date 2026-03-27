package com.gtnewhorizons.galaxia.rocketmodules.rocket.validators;

import net.minecraft.util.StatCollector;

import com.gtnewhorizons.galaxia.registry.dimension.DimensionDef;
import com.gtnewhorizons.galaxia.registry.dimension.SolarSystemRegistry;
import com.gtnewhorizons.galaxia.rocketmodules.rocket.RocketAssembly;
import com.gtnewhorizons.galaxia.utility.OrbitalCalculatorHelper;
import com.gtnewhorizons.galaxia.utility.SystemCenter;

public class EnoughFuelValidator implements IRocketValidator {

    @Override
    public ValidationResult validate(RocketAssembly assembly) {
        int currentDimensionID = assembly.getCurrentDim();
        int targetDimensionID = assembly.getDestination();

        DimensionDef launchBody = SolarSystemRegistry.getById(currentDimensionID);
        DimensionDef targetBody = SolarSystemRegistry.getById(targetDimensionID);

        double storedFuel = assembly.getStoredFuel();

        double requiredFuel = OrbitalCalculatorHelper
            .calculateFuelRequiredForTravel(assembly, launchBody, targetBody, new SystemCenter(1));
        return storedFuel >= requiredFuel ? ValidationResult.success()
            : new ValidationResult(
                false,
                StatCollector
                    .translateToLocalFormatted("galaxia.gui.rocket_silo.validator.not_enough_fuel", targetBody.name()));
    }
}
