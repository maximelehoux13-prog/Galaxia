package com.gtnewhorizons.galaxia.registry.outpost.logistics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;

import net.minecraft.item.Item;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialRegistry;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;

final class LogisticStoreTest {

    @BeforeAll
    static void initRegistries() {
        CelestialRegistry.freezeAndBake();
    }

    @AfterEach
    void cleanup() {
        LogisticStore.clearDeliveries();
        CelestialAssetStore.clear();
    }

    @Test
    void arrivedDeliveryKeepsRemainderPendingWhenDestinationInventoryIsFull() {
        UUID teamId = UUID.randomUUID();
        AutomatedFacility source = facility();
        AutomatedFacility destination = facility();
        ItemStackWrapper filler = new ItemStackWrapper(new Item(), 0, null);
        ItemStackWrapper delivered = new ItemStackWrapper(new Item(), 0, null);
        destination.inventory.add(filler, 998L);
        CelestialAssetStore.add(teamId, source);
        CelestialAssetStore.add(teamId, destination);

        LogisticStore.addDelivery(
            LogisticsDelivery.createWithTrajectory(
                source.assetId,
                destination.assetId,
                delivered,
                5L,
                1,
                LogisticSignal.Scope.SYSTEM,
                source.celestialObjectId,
                destination.celestialObjectId,
                0,
                0));

        LogisticStore.tickDeliveries();

        assertEquals(2L, destination.inventory.getAmount(delivered));
        assertEquals(
            1,
            LogisticStore.activeDeliveries()
                .size());
        assertEquals(
            3L,
            LogisticStore.activeDeliveries()
                .get(0).data.amount());

        destination.inventory.add(filler, -3L);
        LogisticStore.tickDeliveries();

        assertEquals(5L, destination.inventory.getAmount(delivered));
        assertEquals(
            0,
            LogisticStore.activeDeliveries()
                .size());
    }

    private static AutomatedFacility facility() {
        return new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.PANSPIRA,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
    }
}
