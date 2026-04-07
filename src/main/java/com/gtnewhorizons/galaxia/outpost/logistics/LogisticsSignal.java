package com.gtnewhorizons.galaxia.outpost.logistics;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.galaxia.outpost.ItemStackWrapper;

/**
 * A single logistics signal emitted by an outpost for one resource.
 *
 * <h3>Sign convention (Two-Table System)</h3>
 * <ul>
 *   <li><b>Positive amount</b> → Supply offer: the outpost holds more than its
 *       {@code minReserve} and is willing to export the surplus.</li>
 *   <li><b>Negative amount</b> → Request: the outpost's stock is below {@code minReserve}
 *       and needs exactly {@code |amount|} units (i.e. one {@code orderSize} packet).</li>
 * </ul>
 *
 * <p>Signals are regenerated from scratch every logistics tick by
 * {@link com.gtnewhorizons.galaxia.outpost.logistics.OutpostLogisticsEngine}.
 * They are never persisted – they are purely derived from buffer + config state.
 */
@Desugar
public record LogisticsSignal(
    /** Asset id of the outpost that emitted this signal. */
    String outpostAssetId,
    /** Stellar system id – used to scope signal to {@code LocalSystemRegistry}. */
    String systemId,
    /** The resource this signal concerns. */
    ItemStackWrapper resourceId,
    /**
     * Signed amount: positive = supply offer (surplus units available),
     * negative = request amount (units needed, equals one orderSize batch).
     */
    long amount) {

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
