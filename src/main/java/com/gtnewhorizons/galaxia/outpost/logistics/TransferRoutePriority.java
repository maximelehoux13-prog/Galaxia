package com.gtnewhorizons.galaxia.outpost.logistics;

public enum TransferRoutePriority {
    PRIORITIZE_TOF,
    PRIORITIZE_DV;

    public TransferRoutePriority toggled() {
        return this == PRIORITIZE_TOF ? PRIORITIZE_DV : PRIORITIZE_TOF;
    }
}
