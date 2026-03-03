package com.gtnewhorizons.galaxia.core.oxygen.api;


/*
    this interface is used to grab the tank
    u should eithe rimplement IOxygenTile or IOxygenItem NOT this
 */
public interface IOxygenStorage {

    /**
     * size of the o2 tank
     */
    int tankSize();

    /**
     *
     * @return int speed to transfer oxygen to this machine
     *
     */
    int transferAmount();

}
