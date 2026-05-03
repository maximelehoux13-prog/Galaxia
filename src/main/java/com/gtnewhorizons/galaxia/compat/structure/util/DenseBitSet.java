package com.gtnewhorizons.galaxia.compat.structure.util;

import java.util.Arrays;

public final class DenseBitSet {

    @FunctionalInterface
    public interface CoordConsumer {

        void accept(int lx, int ly, int lz);
    }

    // Assume 4Kb pages (too bad if you are on macOS)
    private static final int PAGE_SHIFT = 12;
    private static final int PAGE_SIZE = 1 << PAGE_SHIFT;
    private static final int WORDS_PER_PAGE = PAGE_SIZE >>> 6;

    private final long[][] pages;
    private final int radius;
    private final int stride;
    private final int strideSquared;
    private final int totalBits;

    private int size;

    public DenseBitSet(int radius) {
        this.radius = radius;
        this.stride = 2 * radius + 1;
        this.strideSquared = stride * stride;
        this.totalBits = strideSquared * stride;

        int numPages = (totalBits + PAGE_SIZE - 1) >>> PAGE_SHIFT;
        this.pages = new long[numPages][];
    }

    private int index(int lx, int ly, int lz) {
        return (lx + radius) * strideSquared + (ly + radius) * stride + (lz + radius);
    }

    public boolean add(int lx, int ly, int lz) {
        int idx = index(lx, ly, lz);

        int pageIdx = idx >>> PAGE_SHIFT;
        long[] page = pages[pageIdx];

        if (page == null) {
            page = new long[WORDS_PER_PAGE];
            pages[pageIdx] = page;
        }

        int wordIndex = (idx >>> 6) & (WORDS_PER_PAGE - 1);
        long mask = 1L << (idx & 63);

        if ((page[wordIndex] & mask) != 0L) return false;

        page[wordIndex] |= mask;
        size++;
        return true;
    }

    public boolean contains(int lx, int ly, int lz) {
        int idx = index(lx, ly, lz);

        int pageIdx = idx >>> PAGE_SHIFT;
        long[] page = pages[pageIdx];
        if (page == null) return false;

        int wordIndex = (idx >>> 6) & (WORDS_PER_PAGE - 1);
        return (page[wordIndex] & (1L << (idx & 63))) != 0L;
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public void clear() {
        for (int i = 0; i < pages.length; i++) {
            if (pages[i] != null) {
                Arrays.fill(pages[i], 0L);
            }
        }
        size = 0;
    }

    public void forEach(CoordConsumer consumer) {
        final int ss = strideSquared;
        final int st = stride;
        final int r = radius;

        for (int pi = 0; pi < pages.length; pi++) {
            long[] page = pages[pi];
            if (page == null) continue;

            int baseIdx = pi << PAGE_SHIFT;

            for (int wi = 0; wi < WORDS_PER_PAGE; wi++) {
                long word = page[wi];
                while (word != 0L) {
                    int bit = Long.numberOfTrailingZeros(word);
                    int idx = baseIdx + (wi << 6) + bit;

                    if (idx >= totalBits) break;

                    consumer.accept(idx / ss - r, (idx % ss) / st - r, idx % st - r);

                    word &= word - 1L;
                }
            }
        }
    }
}
