package com.gtnewhorizons.galaxia.registry.outpost.logistics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialRegistry;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalTransferPlanner;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleRegistry;
import com.gtnewhorizons.galaxia.registry.outpost.module.HammerVariant;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleHammer;

final class HammerDispatchStatusTest {

    @BeforeAll
    static void initRegistries() {
        CelestialRegistry.freezeAndBake();
        FacilityModuleRegistry.init();
    }

    @Test
    void readyWhenCandidatePassesHammerDispatchChecks() {
        ModuleHammer hammer = hammer(AllowShootingConfig.ALWAYS, HammerVariant.BIG, 1_000_000L);
        HammerDispatchStatus.Candidate candidate = candidate(64, 64, 32, 1.5, 20.0, 120.0);

        HammerDispatchStatus.Status status = HammerDispatchStatus.evaluateCandidate(hammer, candidate);

        assertEquals(HammerDispatchStatus.Code.READY, status.code());
        assertEquals(200_000L, status.requiredEnergy());
    }

    @Test
    void reportsEnergyNeededWhenRouteCostExceedsPrivateBuffer() {
        ModuleHammer hammer = hammer(AllowShootingConfig.ALWAYS, HammerVariant.BIG, 500_000L);
        HammerDispatchStatus.Candidate candidate = candidate(64, 64, 32, 1.5, 80.0, 120.0);

        HammerDispatchStatus.Status status = HammerDispatchStatus.evaluateCandidate(hammer, candidate);

        assertEquals(HammerDispatchStatus.Code.NEED_ENERGY, status.code());
        assertEquals(800_000L, status.requiredEnergy());
        assertEquals(500_000L, status.storedEnergy());
    }

    @Test
    void reportsDvLimitWhenShootingConfigBlocksRoute() {
        ModuleHammer hammer = hammer(
            new AllowShootingConfig(AllowShootingConfig.Mode.WHEN_DV_UNDER, 2.0),
            HammerVariant.BIG,
            1_000_000L);
        HammerDispatchStatus.Candidate candidate = candidate(64, 64, 32, 3.0, 20.0, 120.0);

        HammerDispatchStatus.Status status = HammerDispatchStatus.evaluateCandidate(hammer, candidate);

        assertEquals(HammerDispatchStatus.Code.BLOCKED_BY_DV_LIMIT, status.code());
    }

    @Test
    void reportsOrderBelowPackageSizeBeforeSpendingEnergy() {
        ModuleHammer hammer = hammer(AllowShootingConfig.ALWAYS, HammerVariant.BIG, 1_000_000L);
        HammerDispatchStatus.Candidate candidate = candidate(64, 16, 32, 1.5, 20.0, 120.0);

        HammerDispatchStatus.Status status = HammerDispatchStatus.evaluateCandidate(hammer, candidate);

        assertEquals(HammerDispatchStatus.Code.ORDER_BELOW_PACKAGE_SIZE, status.code());
        assertEquals(16L, status.sendAmount());
        assertEquals(32, status.orderSize());
    }

    private static ModuleHammer hammer(AllowShootingConfig config, HammerVariant variant, long energyStored) {
        return new ModuleHammer(
            FacilityModuleKind.HAMMER,
            config,
            OrbitalTransferPlanner.RoutePriority.PRIORITIZE_DV,
            variant,
            64,
            energyStored);
    }

    private static HammerDispatchStatus.Candidate candidate(long availableSurplus, long requestedAmount, int orderSize,
        double departureDv, double totalDv, double tofSeconds) {
        return new HammerDispatchStatus.Candidate(
            false,
            true,
            true,
            availableSurplus,
            requestedAmount,
            orderSize,
            departureDv,
            totalDv,
            tofSeconds);
    }
}
