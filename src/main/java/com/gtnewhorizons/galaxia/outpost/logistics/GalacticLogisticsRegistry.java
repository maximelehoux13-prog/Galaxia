package com.gtnewhorizons.galaxia.outpost.logistics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Galaxy-wide logistics signal registry for inter-stellar trade.
 *
 * <p>Only signals explicitly promoted by a future "Galactic Supply Center" module
 * are placed here. Standard HAMMER / BIG_HAMMER modules never reach this layer –
 * they operate solely within the {@link LocalSystemRegistry}.
 *
 * <p>The registry is rebuilt every logistics tick and holds no persistent state.
 * Accessed only from the server thread.
 */
public final class GalacticLogisticsRegistry {

    private static final GalacticLogisticsRegistry INSTANCE = new GalacticLogisticsRegistry();

    public static GalacticLogisticsRegistry get() {
        return INSTANCE;
    }

    private final List<LogisticsSignal> signals = new ArrayList<>();

    private GalacticLogisticsRegistry() {}

    /** Clears all galactic signals – called at the start of every logistics tick rebuild. */
    public void clear() {
        signals.clear();
    }

    /**
     * Promotes a signal from a local system to the galactic registry.
     * Only special modules (e.g. a future Galactic Supply Center) should call this.
     */
    public void promoteSignal(LogisticsSignal signal) {
        signals.add(signal);
    }

    /** Returns an unmodifiable snapshot of all galactic signals. */
    public List<LogisticsSignal> allSignals() {
        return Collections.unmodifiableList(signals);
    }
}
