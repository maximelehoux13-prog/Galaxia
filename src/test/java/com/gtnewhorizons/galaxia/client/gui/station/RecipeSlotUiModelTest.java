package com.gtnewhorizons.galaxia.client.gui.station;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeConfig;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSchedulerMode;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSlot;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSnapshot;

final class RecipeSlotUiModelTest {

    @Test
    void unresolvedRecipeFallsBackToRecipeIndex() {
        RecipeSlot slot = new RecipeSlot(RecipeSnapshot.unresolved((byte) 1, 7, 42L), true, 0, 999, (byte) 1, (byte) 1);

        assertEquals("Recipe #7", RecipeSlotUiModel.slotTitle(slot));
    }

    @Test
    void parseIntClampsToAllowedRange() {
        assertEquals(0, RecipeSlotUiModel.parseIntOrCurrent("-10", 5, 0, 10));
        assertEquals(10, RecipeSlotUiModel.parseIntOrCurrent("99", 5, 0, 10));
        assertEquals(5, RecipeSlotUiModel.parseIntOrCurrent("bad", 5, 0, 10));
    }

    @Test
    void nextModeCyclesSchedulerMode() {
        assertEquals(RecipeSchedulerMode.ORDER, RecipeSlotUiModel.nextMode(RecipeConfig.empty()));
    }
}
