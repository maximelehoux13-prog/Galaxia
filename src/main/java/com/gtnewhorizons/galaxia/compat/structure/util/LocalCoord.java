package com.gtnewhorizons.galaxia.compat.structure.util;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

public final class LocalCoord {

    public static final int SEARCH_RADIUS = 16;

    private LocalCoord() {}

    private static int offset(int v) {
        return v + SEARCH_RADIUS;
    }

    private static int unoffset(int v) {
        return v - SEARCH_RADIUS;
    }

    public static int pack(int x, int y, int z) {
        return (offset(x) << 12) | (offset(y) << 6) | offset(z);
    }

    public static int packFromWorld(int wx, int wy, int wz, int xCoord, int yCoord, int zCoord) {
        return pack(wx - xCoord, wy - yCoord, wz - zCoord);
    }

    public static int unpackX(int v) {
        return unoffset((v >> 12) & 63);
    }

    public static int unpackSignedY(int v) {
        return unoffset((v >> 6) & 63);
    }

    public static int unpackY(int v) {
        return unoffset((v >> 6) & 63);
    }

    public static int unpackZ(int v) {
        return unoffset(v & 63);
    }

    public static int worldX(int localX, int xCoord) {
        return localX + xCoord;
    }

    public static int worldY(int localY, int yCoord) {
        return localY + yCoord;
    }

    public static int worldZ(int localZ, int zCoord) {
        return localZ + zCoord;
    }

    public static boolean isInBounds(int x, int y, int z) {
        return x >= -SEARCH_RADIUS && x <= SEARCH_RADIUS
            && y >= -SEARCH_RADIUS
            && y <= SEARCH_RADIUS
            && z >= -SEARCH_RADIUS
            && z <= SEARCH_RADIUS;
    }

    public static IntSet newBlockSet() {
        return new IntOpenHashSet();
    }
}
