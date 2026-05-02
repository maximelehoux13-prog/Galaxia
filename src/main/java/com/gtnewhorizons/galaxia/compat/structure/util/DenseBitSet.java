package com.gtnewhorizons.galaxia.compat.structure.util;

import java.util.Arrays;

public final class DenseBitSet {

    @FunctionalInterface
    public interface CoordConsumer {

        void accept(int lx, int ly, int lz);
    }

    private final long[] words;
    private final int radius;
    private final int stride;
    private final int strideSquared;
    private int size;

    public DenseBitSet(int radius) {
        this.radius = radius;
        this.stride = 2 * radius + 1;
        this.strideSquared = stride * stride;
        this.words = new long[((strideSquared * stride) + 63) >>> 6];
    }

    private int index(int lx, int ly, int lz) {
        return (lx + radius) * strideSquared + (ly + radius) * stride + (lz + radius);
    }

    public boolean add(int lx, int ly, int lz) {
        int idx = index(lx, ly, lz);
        int w = idx >>> 6;
        long mask = 1L << (idx & 63);
        if ((words[w] & mask) != 0L) return false;
        words[w] |= mask;
        size++;
        return true;
    }

    public boolean contains(int lx, int ly, int lz) {
        int idx = index(lx, ly, lz);
        return (words[idx >>> 6] & (1L << (idx & 63))) != 0L;
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public void forEach(CoordConsumer consumer) {
        final long[] w = words;
        final int ss = strideSquared;
        final int st = stride;
        final int r = radius;
        for (int wi = 0; wi < w.length; wi++) {
            long word = w[wi];
            while (word != 0L) {
                int bit = Long.numberOfTrailingZeros(word);
                int idx = (wi << 6) | bit;
                consumer.accept(idx / ss - r, (idx % ss) / st - r, idx % st - r);
                word &= word - 1L;
            }
        }
    }

    public void clear() {
        Arrays.fill(words, 0L);
        size = 0;
    }
}
