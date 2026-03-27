package com.gtnewhorizons.galaxia.utility;

import com.gtnewhorizons.galaxia.registry.dimension.DimensionDef;
import com.gtnewhorizons.galaxia.rocketmodules.rocket.RocketAssembly;

public class OrbitalCalculatorHelper {
    /*
     * Notes and explanations:
     * For the sake of computational complexity, the following changes have been
     * made to the standard equations:
     * G (Gravitational constant) = 1 (for simplicity)
     * All masses are in Earth Masses as unit
     * All distances are measured as average Earth Radii;
     */

    // Corrective factor for unit conversion in Hohmann Transfers
    static final double hohmannCorrectiveFactor = 6700;

    // Corrective factor for unit conversion in escape velocities
    static final double escapeCorrectiveFactor = 1.65;

    // Corrective factor for calculating effective exhaust velocity
    static final double effectiveCorrectiveFactor = 2.7;

    // TODO: REMOVE THIS and replace as a property of different fuels
    static final double specificImpulse = 3000;

    /**
     * Calculates the Effective Exhaust Velocity (v_e) based on the rocket and body
     * to launch from
     *
     * @param launchBody     The Gravitational Body from the surface of which to
     *                       launch
     * @param rocket         The rocket being used to calculate
     * @param launchAltitude The altitude above the body from which to launch
     * @return Effective Exhaust Velocity (v_e)
     */
    public static double calculateEffectiveExhaustVelocity(DimensionDef launchBody, RocketAssembly assembly,
        double launchAltitude) {
        return effectiveCorrectiveFactor * specificImpulse
            * (launchBody.mass())
            / Math.pow((launchAltitude + launchBody.orbitalRadius()), 2);
    }

    /**
     * Calculates the Maximum DeltaV of the rocket provided launching from a given
     * body
     *
     * @param launchBody     The Gravitational Body from the surface of which to
     *                       launch
     * @param rocket         The rocket being used to calculate
     * @param launchAltitude The altitude above the body from which to launch
     * @return Maximum Delta V achievable
     */
    public static double calculateMaxDeltaVelocity(DimensionDef launchBody, RocketAssembly assembly,
        double launchAltitude) {
        final double effectiveExhaustVelocity = calculateEffectiveExhaustVelocity(launchBody, assembly, launchAltitude);
        final double dryMass = assembly.getTotalWeight();
        final double totalMass = dryMass + assembly.getFuelWeight();

        return effectiveExhaustVelocity * Math.log(totalMass / dryMass);
    }

    /**
     * Calculates the DeltaV required to enter elliptical orbit (stage 1)
     *
     * @param launchBody  The Gravitational Body for starting orbit
     * @param centerBody  The Gravitational Body from which main source of Gravity
     *                    in system
     * @param targetBody  The Gravitational Body for arrival orbit
     * @param startRadius The starting orbital radius (surface height for base
     *                    launch)
     * @return DeltaV required for stage 1 of orbit transfer
     */
    public static double calculateHohmannV1(DimensionDef launchBody, DimensionDef targetBody, SystemCenter centerBody,
        double startRadius) {

        // Most of this is renaming readable variables to fit standard notation for
        // calculation, and legibility

        // GM of the center body
        final double mu_s = centerBody.getMass();

        // GM of departure body
        final double mu_1 = launchBody.mass();

        // Distance from center body to launch body
        final double r_1 = launchBody.mass();

        // Distance from center body to arrival body
        final double r_2 = targetBody.orbitalRadius();

        // Radius of original orbit
        final double a_1 = startRadius;

        return hohmannCorrectiveFactor * Math.sqrt(
            Math.pow((Math.sqrt((2 * mu_s * r_2) / (r_1 * (r_1 + r_2))) - Math.sqrt(mu_s / r_1)), 2) + (2 * mu_1) / a_1)
            - Math.sqrt(mu_1 / a_1);
    }

    /**
     * Calculates the DeltaV required to correct elliptical orbit into circular
     * (stage 2)
     *
     * @param centerBody The Gravitational Body from which gravity is mainly felt
     * @param launchBody The Gravitational Body for starting orbit
     * @param targetBody The Gravitational Body for arrival orbit
     * @param endRadius  The final orbital radius
     * @return DeltaV required for stage 2 of orbit transfer
     */
    public static double calculateHohmannV2(DimensionDef launchBody, DimensionDef targetBody, SystemCenter centerBody,
        double endRadius) {

        /*
         * Most of this is renaming readable variables to fit standard notation for
         * calculation, and legibility
         * mu_s
         * mu_2
         * r_1
         * r_2
         * a_2
         */

        // GM of center body
        final double mu_s = centerBody.getMass();

        // GM of target body
        final double mu_2 = targetBody.mass();

        // Distance from center body to launch body
        final double r_1 = launchBody.orbitalRadius();

        // Distance from center body to target body
        final double r_2 = targetBody.orbitalRadius();

        // orbital height for target body
        final double a_2 = endRadius;

        return hohmannCorrectiveFactor * Math.sqrt(
            (Math.pow((Math.sqrt((2 * mu_s * r_1) / (r_2 * (r_1 + r_2))) - Math.sqrt(mu_s / r_2)), 2)
                + (2 * mu_2) / a_2))
            - Math.sqrt(mu_2 / a_2);

    }

    /**
     * Combines the two stages of Hohmann Transfer
     *
     * @param launchBody  The Gravitational Body from first orbit
     * @param targetBody  The Gravitational Body for final orbit
     * @param centerBody  The Gravitational Body providing main gravitational pull
     *                    in system (i.e. star etc.)
     * @param startRadius The starting circular radius of orbit
     * @param endRadius   The final circular radius target
     * @return Total Delta V required to alter orbital radius
     */
    public static double calculateHohmannVelocity(DimensionDef launchBody, DimensionDef targetBody,
        SystemCenter centerBody, double startRadius, double endRadius) {
        final double v_1 = calculateHohmannV1(launchBody, targetBody, centerBody, startRadius);
        final double v_2 = calculateHohmannV2(launchBody, targetBody, centerBody, endRadius);
        return (Math.round((v_1 + v_2) * 100) / 100.0);
    }

    /**
     *
     * @param launchBody     The body from which to launch
     * @param launchAltitude The altitude above the body from which to launch
     * @return The escape velocity of the planet from that altitude
     */
    public static double calculateEscapeVelocity(DimensionDef launchBody) {
        return escapeCorrectiveFactor * Math.sqrt((2 * launchBody.mass() / (launchBody.orbitalRadius())));
    }

    public static double calculateDirectDeltaV(DimensionDef launchBody, DimensionDef targetBody) {
        return (calculateEscapeVelocity(launchBody) + calculateEscapeVelocity(targetBody));
    }

    public static double calculateFuelRequiredForTravel(RocketAssembly assembly, DimensionDef launchBody,
        DimensionDef targetBody, SystemCenter centerBody) {
        double requiredDeltaV = calculateHohmannVelocity(
            launchBody,
            targetBody,
            centerBody,
            launchBody.radius(),
            targetBody.radius());
        double v_e = calculateEffectiveExhaustVelocity(launchBody, assembly, launchBody.radius());

        return assembly.getTotalWeight() * (Math.exp(requiredDeltaV / v_e) - 1);

    }

}
