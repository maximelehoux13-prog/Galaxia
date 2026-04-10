package com.gtnewhorizons.galaxia.outpost.logistics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Unified logistics signal registry, scoped by {@link LogisticsSignal.Scope}.
 *
 * <p>Buckets signals by (scope, scopeKey) pairs:
 * <ul>
 *   <li>PLANETARY scope → keyed by {@code planetaryAnchorBodyId}</li>
 *   <li>SYSTEM scope    → keyed by {@code systemId}</li>
 *   <li>GALACTIC scope  → keyed by a galaxy/inter-system id (placeholder)</li>
 * </ul>
 *
 * <p>The store is rebuilt entirely every logistics tick and holds no persistent state.
 * Accessed only from the server thread.
 */
public final class LogisticsSignalStore {

    private static final LogisticsSignalStore INSTANCE = new LogisticsSignalStore();

    public static LogisticsSignalStore get() {
        return INSTANCE;
    }

    /** scope → scopeKey → signals */
    private final Map<LogisticsSignal.Scope, Map<String, List<LogisticsSignal>>> store = new LinkedHashMap<>();

    private LogisticsSignalStore() {}

    /** Clears all signals – called at the start of every logistics tick rebuild. */
    public void clear() {
        store.clear();
    }

    /** Registers a signal in the appropriate scope bucket. */
    public void addSignal(LogisticsSignal signal) {
        String scopeKey = scopeKeyFor(signal);
        if (scopeKey == null) return;
        store.computeIfAbsent(signal.scope(), s -> new LinkedHashMap<>())
            .computeIfAbsent(scopeKey, k -> new ArrayList<>())
            .add(signal);
    }

    /**
     * Returns all signals in the given scope for the given key.
     * Returns an empty list if none are registered.
     */
    public List<LogisticsSignal> getSignals(LogisticsSignal.Scope scope, String scopeKey) {
        Map<String, List<LogisticsSignal>> byKey = store.get(scope);
        if (byKey == null) return Collections.emptyList();
        List<LogisticsSignal> list = byKey.get(scopeKey);
        return list == null ? Collections.emptyList() : Collections.unmodifiableList(list);
    }

    /**
     * Returns an unmodifiable map of (scopeKey → signals) for the given scope.
     * Returns an empty map if no signals exist for that scope.
     */
    public Map<String, List<LogisticsSignal>> allSignalsForScope(LogisticsSignal.Scope scope) {
        Map<String, List<LogisticsSignal>> byKey = store.get(scope);
        return byKey == null ? Collections.emptyMap() : Collections.unmodifiableMap(byKey);
    }

    private static String scopeKeyFor(LogisticsSignal signal) {
        return switch (signal.scope()) {
            case PLANETARY -> signal.planetaryAnchorBodyId();
            case SYSTEM -> signal.systemId();
            case GALACTIC -> signal.systemId(); // placeholder
        };
    }
}
