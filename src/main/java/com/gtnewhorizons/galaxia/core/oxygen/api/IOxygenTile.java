package com.gtnewhorizons.galaxia.core.oxygen.api;

public interface IOxygenTile extends IOxygenStorage{

    /**
     *
     * @return the current o2 stored in the tank
     */
    int currentOxygen();

    /**
     * Drain oxygen from an ItemStack containing an ItemOxygenTank. If the full amount cannot be drained, it will
     * drain as much as possible!
     *
     * @param amount Amount of oxygen to consume.
     * @return If the full amount was successfully drained.
     */

    boolean drain(int amount);

    /**
     *
     * @param amount amount to fill
     *               override this to fill ur o2 tank with the desired amount
     */
    void fill(int amount);
}
