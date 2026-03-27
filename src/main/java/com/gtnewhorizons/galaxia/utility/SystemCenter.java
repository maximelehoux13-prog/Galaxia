package com.gtnewhorizons.galaxia.utility;

public final class SystemCenter {

    private static final double SOLAR_MASSES_IN_EARTH_MASSES = 333000;
    private double mass;

    public SystemCenter(double mass) {
        this.mass = mass * SOLAR_MASSES_IN_EARTH_MASSES;
    }

    public double getMass() {
        return this.mass;
    }
}
