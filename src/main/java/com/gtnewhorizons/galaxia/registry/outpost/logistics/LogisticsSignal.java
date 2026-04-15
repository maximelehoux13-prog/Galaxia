package com.gtnewhorizons.galaxia.registry.outpost.logistics;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;

/**
 * A single logistics signal emitted by an outpost for one resource.
 *
 * <h3>Sign convention (Two-Table System)</h3>
 * <ul>
 * <li><b>Positive amount</b> → Supply offer: the outpost holds more than its
 * {@code minReserve} and is willing to export the surplus.</li>
 * <li><b>Negative amount</b> → Request: the outpost's stock is below {@code minReserve}
 * and needs exactly {@code |amount|} units.</li>
 * </ul>
 *
 * <h3>Scope</h3>
 * <ul>
 * <li>{@link LogisticsSignal.Scope#PLANETARY} – signal visible only to modules on the same
 * planet and its moons (HAMMER-range). Keyed by {@code planetaryAnchorBodyId}.</li>
 * <li>{@link LogisticsSignal.Scope#SYSTEM} – signal visible to all modules in the same
 * stellar system (BIG_HAMMER-range). Keyed by {@code systemId}.</li>
 * </ul>
 *
 * <p>
 * Signals are regenerated from scratch every logistics tick.
 * They are never persisted – they are purely derived from buffer + config state.
 */
@Desugar
public record LogisticsSignal(
    /** Asset id of the outpost that emitted this signal. */
    CelestialAsset.ID outpostAssetId,
    /** Stellar system id – the id of the host star. */
    CelestialObjectId systemId,
    /** The resource this signal concerns. */
    ItemStackWrapper resourceId,
    /**
     * Signed amount: positive = supply offer (surplus units available),
     * negative = request amount (units needed).
     */
    long amount,
    /** Scope that determines which modules can see this signal. */
    Scope scope,
    /** Celestial body id of the outpost's host body. */
    CelestialObjectId bodyId,
    /**
     * Planetary anchor for PLANETARY-scope signals.
     * For planets: same as {@code bodyId}.
     * For moons: the parent planet's body id.
     * {@code null} for SYSTEM-scope signals.
     */
    CelestialObjectId planetaryAnchorBodyId) {

    /**
     * Determines which logistics modules can see (and respond to) a given signal.
     */
    public enum Scope {
        PLANETARY,
        SYSTEM,
        GALACTIC
    }

    public boolean isSupply() {
        return amount > 0;
    }

    public boolean isRequest() {
        return amount < 0;
    }

    /** The magnitude of this signal regardless of direction. */
    public long magnitude() {
        return Math.abs(amount);
    }
}
