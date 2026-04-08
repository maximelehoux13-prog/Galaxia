package com.gtnewhorizons.galaxia.outpost.logistics;

/**
 * Determines which logistics modules can see (and respond to) a given signal.
 *
 * <ul>
 *   <li>{@link #PLANETARY} – the host planet and all of its moons form one scope bucket.
 *       HAMMER modules operate at this range.</li>
 *   <li>{@link #SYSTEM} – all bodies orbiting the same star form one scope bucket.
 *       BIG_HAMMER modules operate at this range.</li>
 *   <li>{@link #GALACTIC} – placeholder for future inter-system trade.</li>
 * </ul>
 */
public enum LogisticsSignalScope {
    PLANETARY,
    SYSTEM,
    GALACTIC
}
