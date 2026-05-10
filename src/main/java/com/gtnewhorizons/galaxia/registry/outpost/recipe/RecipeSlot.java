package com.gtnewhorizons.galaxia.registry.outpost.recipe;

public record RecipeSlot(RecipeSnapshot recipe, boolean enabled, RecipeSlotBounds bounds, byte priority,
    byte orderSize) {

    public RecipeSlot {
        if (recipe == null) throw new NullPointerException("recipe must not be null");
        if (bounds == null) bounds = RecipeSlotBounds.empty();
        if (priority < 0) throw new IllegalArgumentException("priority must be >= 0: " + priority);
        if (orderSize < 1) throw new IllegalArgumentException("orderSize must be >= 1: " + orderSize);
    }
}
