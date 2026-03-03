package com.gtnewhorizons.galaxia.core.oxygen.api;

import net.minecraft.item.ItemStack;

public interface IOxygenStorage {

    /** size of the o2 tank
     */
    int tankSize();

    /**
     *
     * @return int speed to transfer oxygen to this machine
     * default 20
     */
    default int transferAmount() {
        return 20;
    }

    /**
     *
     * @return the current o2 stored in the tank
     */
    default int currentOxygen() {
        return tankSize();
    }

    /**
     *
     * @param stack to get current o2
     * @return get the current o2
     */
    default int currentOxygenFromStack(ItemStack stack) {
        return currentOxygen();
    }

    /**
     *
     * @param amount amount to fill
     * override this to fill ur o2 tank with the desired amount
     */
    default void fill(int amount) {}

    /** for items
     *
     * @param stack - the itemstack being filled e.g. an o2 tank
     * @param amount - the amount filled
     * this is to fill items that hold oxygen
     */
    default void fillStack(ItemStack stack, int amount) {
        fill(amount);
    }

    /**
     * Drain oxygen from an ItemStack containing an ItemOxygenTank. If the full amount cannot be drained, it will
     * drain as much as possible!
     *
     * @param amount Amount of oxygen to consume.
     * @return If the full amount was successfully drained.
     */

    default boolean drain(int amount) {
        return false;
    }

    /**
     * implement this to allow draining oxygen from an ItemStack.
     *
     * @param amount Amount of oxygen to consume.
     * @param  stack stack to drain from
     * @return If the full amount was successfully drained.
     */

    default boolean drainStack(ItemStack stack, int amount) {
        return drain(amount);
    }






}
