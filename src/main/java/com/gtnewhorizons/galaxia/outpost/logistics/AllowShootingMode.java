package com.gtnewhorizons.galaxia.outpost.logistics;

/**
 * Controls when a logistics module is permitted to spend EU to dispatch a trajectory transfer.
 */
public enum AllowShootingMode {
    /** Always dispatch regardless of dV or time-of-flight. */
    ALWAYS,
    /** Only dispatch when the computed departure delta-V is below the configured threshold. */
    WHEN_DV_UNDER,
    /** Only dispatch when the estimated time-of-flight (in real seconds) is below the configured threshold. */
    WHEN_TOF_UNDER
}
