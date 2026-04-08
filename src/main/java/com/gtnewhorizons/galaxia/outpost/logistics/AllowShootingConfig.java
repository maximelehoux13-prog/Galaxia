package com.gtnewhorizons.galaxia.outpost.logistics;

import com.github.bsideup.jabel.Desugar;

/**
 * Per-module shooting permission configuration.
 *
 * <p>Controls whether a HAMMER or BIG_HAMMER module is allowed to spend EU and dispatch
 * a trajectory-based logistics task. The threshold unit depends on mode:
 * <ul>
 *   <li>{@link AllowShootingMode#WHEN_DV_UNDER} – departure delta-V in orbital velocity units.</li>
 *   <li>{@link AllowShootingMode#WHEN_TOF_UNDER} – time-of-flight in real seconds.</li>
 * </ul>
 */
@Desugar
public record AllowShootingConfig(AllowShootingMode mode, double threshold) {

    /** Singleton representing "always allow". */
    public static final AllowShootingConfig ALWAYS = new AllowShootingConfig(AllowShootingMode.ALWAYS, 0.0);

    /**
     * Returns {@code true} if a transfer with the given metrics is permitted.
     *
     * @param departureDv departure delta-V in orbital velocity units
     * @param tofSeconds  time-of-flight in real seconds
     */
    public boolean allows(double departureDv, double tofSeconds) {
        return switch (mode) {
            case ALWAYS -> true;
            case WHEN_DV_UNDER -> departureDv < threshold;
            case WHEN_TOF_UNDER -> tofSeconds < threshold;
        };
    }
}
