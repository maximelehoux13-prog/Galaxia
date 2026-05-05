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
    private final int minX, minY, minZ;
    private final int lenX, lenY, lenZ;
    private final int strideY;
    private final int strideX;
    private final int totalBits;

    private int size;

    public DenseBitSet(int minX, int minY, int minZ, int lenX, int lenY, int lenZ) {
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.lenX = lenX;
        this.lenY = lenY;
        this.lenZ = lenZ;

        this.strideY = this.lenZ;
        this.strideX = this.lenY * this.lenZ;
        this.totalBits = this.lenX * this.strideX;

        int numPages = (totalBits + PAGE_SIZE - 1) >>> PAGE_SHIFT;
        this.pages = new long[numPages][];
    }

    private int index(int lx, int ly, int lz) {
        return (lx - minX) * strideX + (ly - minY) * strideY + (lz - minZ);
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

    private boolean inBounds(int x, int y, int z) {
        return x >= minX && x < minX + lenX && y >= minY && y < minY + lenY && z >= minZ && z < minZ + lenZ;
    }

    public boolean containsChecked(int x, int y, int z) {
        return inBounds(x, y, z) && contains(x, y, z);
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
        final int sx = strideX;
        final int sy = strideY;
        final int mx = minX;
        final int my = minY;
        final int mz = minZ;

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

                    consumer.accept(idx / sx + mx, (idx % sx) / sy + my, idx % sy + mz);

                    word &= word - 1L;
                }
            }
        }
    }
}
