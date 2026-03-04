package com.gtnewhorizons.galaxia.core.oxygen.api;

import net.minecraft.item.ItemStack;

/**
 * implement on Items that have oxygen capabilities
 */
public interface IOxygenItem extends IOxygenStorage {

    /**
     *
     * @param stack to get current o2
     * @return get the current o2
     */
    int currentOxygen(ItemStack stack);

    /**
     * for items
     *
     * @param stack  - the itemstack being filled e.g. an o2 tank
     * @param amount - the amount filled
     *               this is to fill items that hold oxygen
     */
    void fill(ItemStack stack, int amount);

    /**
     * implement this to allow draining oxygen from an ItemStack.
     *
     * @param amount Amount of oxygen to consume.
     * @param stack  stack to drain from
     * @return If the full amount was successfully drained.
     */

    boolean drain(ItemStack stack, int amount);
}
