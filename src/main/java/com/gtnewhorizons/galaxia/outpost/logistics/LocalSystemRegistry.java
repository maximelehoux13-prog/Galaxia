package com.gtnewhorizons.galaxia.outpost.logistics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Intra-system logistics signal registry.
 *
 * <p>Maps a {@code systemId} (the id of the host star in the celestial hierarchy) to a
 * flat list of {@link LogisticsSignal}s emitted by outposts in that stellar system.
 * Standard HAMMER / BIG_HAMMER pairings are resolved within this scope.
 *
 * <p>The registry is rebuilt entirely every logistics tick – it holds no persistent state.
 * Only {@link GalacticLogisticsRegistry} is consulted for inter-system trade.
 *
 * <p>Accessed only from the server thread.
 */
public final class LocalSystemRegistry {

    private static final LocalSystemRegistry INSTANCE = new LocalSystemRegistry();

    public static LocalSystemRegistry get() {
        return INSTANCE;
    }

    private final Map<String, List<LogisticsSignal>> signalsBySystem = new LinkedHashMap<>();

    private LocalSystemRegistry() {}

    /** Clears all signals – called at the start of every logistics tick rebuild. */
    public void clear() {
        signalsBySystem.clear();
    }

    /** Registers a signal for the given stellar system. */
    public void addSignal(LogisticsSignal signal) {
        signalsBySystem.computeIfAbsent(signal.systemId(), k -> new ArrayList<>())
            .add(signal);
    }

    /**
     * Returns all signals for the given system.
     * Returns an empty list if the system has no registered signals.
     */
    public List<LogisticsSignal> getSignals(String systemId) {
        List<LogisticsSignal> list = signalsBySystem.get(systemId);
        return list == null ? Collections.emptyList() : Collections.unmodifiableList(list);
    }

    /** Returns an unmodifiable view of the full signal map (systemId → signals). */
    public Map<String, List<LogisticsSignal>> allSignals() {
        return Collections.unmodifiableMap(signalsBySystem);
    }
}
