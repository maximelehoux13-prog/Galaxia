package com.gtnewhorizons.galaxia.vaporchamber;

import com.cleanroommc.modularui.widgets.layout.Grid;
import com.gtnewhorizon.gtnhlib.blockpos.BlockPos;
import net.minecraft.block.Block;

import java.util.List;

// formulas extrapolated from https://matthias-research.github.io/pages/tenMinutePhysics/17-fluidSim.pdf
// code overview from https://github.com/matthias-research/pages/blob/master/tenMinutePhysics/17-fluidSim.html (i dont use any part in any significant portion, i only looked at it for inspiration on how to make this)
// thank you matthias for the helpful tutorial
// made by me! (chloe)

public class EulerianSimAPI {
    public static final float dT = 0.05f;
    public static final float h = 1f;

    public static class GridWrapper {
        private byte[][][] cells;

        public float[][][] u;
        public float[][][] v;
        public float[][][] w;

        int dX;
        int dY;
        int dZ;

        public GridWrapper(byte[][][] _cells, float[][][] _u, float[][][] _v, float[][][] _w, int _dX, int _dY, int _dZ) {
            cells = _cells;

            u = _u;
            v = _v;
            w = _w;

            dX = _dX;
            dY = _dY;
            dZ = _dZ;
        }
        public byte getCell(int x, int y, int z) {
            return cells[x][y][z];
        }
        public int getS(int x, int y, int z) {
            if (x < 0 || y < 0 || z < 0 || x == cells.length || y == cells[x].length || z == cells[x][y].length) {
                return 0;
            }
            return ((cells[x][y][z] & (byte) 0b00000010) == 0 && (cells[x][y][z] & (byte) 0b00000001) != 0) ? 1 : 0;
        }

        public byte[][][] getCells() {
            if (cells == null) {
                return new byte[][][]{};
            }
            return cells;
        }
    }

    public static void run(GridWrapper grid) {
        applyForces(grid);
        projection(grid);
        advection(grid);
    }

    public static GridWrapper createGrid(List<BlockPos> internals, List<BlockPos> externals, BlockPos heat) {
        List<BlockPos> combined = internals;
        combined.addAll(externals);
        combined.add(heat);

        Integer rightBound = null; // +X
        Integer leftBound = null; // -X
        Integer upperBound = null;
        Integer lowerBound = null;
        Integer forwardBound = null; // +Z
        Integer backwardBound = null; // -Z

        // create AABB by finding the furthest bounds in each direction

        for (BlockPos vector : combined) {
            if (upperBound == null) {
                rightBound = vector.x;
                leftBound = vector.x;
                upperBound = vector.y;
                lowerBound = vector.y;
                forwardBound = vector.z;
                backwardBound = vector.z;
            }

            rightBound = Math.max(rightBound, vector.x);
            leftBound = Math.min(leftBound, vector.x);
            upperBound = Math.max(upperBound, vector.y);
            lowerBound = Math.min(lowerBound, vector.y);
            forwardBound = Math.max(forwardBound, vector.z);
            backwardBound = Math.min(backwardBound, vector.z);
        }

        if (upperBound == null) {
            return null;
        }

        int dX = rightBound - leftBound + 1;
        int dY = upperBound - lowerBound + 1;
        int dZ = forwardBound - backwardBound + 1;

        byte[][][] cells = new byte[dX][dY][dZ];

        // bit 0: is a fluid cell
        // bit 1: is a wall

        for (BlockPos internal : internals) {
            cells[internal.getX() - leftBound][internal.getY() - lowerBound][internal.getZ() - backwardBound] = (byte) 0b00000001;
        }
        for (BlockPos external : externals) {
            cells[external.getX() - leftBound][external.getY() - lowerBound][external.getZ() - backwardBound] = (byte) 0b00000010;
        }

            cells[heat.getX() - leftBound][heat.getY() - lowerBound][heat.getZ() - backwardBound] = (byte) 0b00000101;

        // create 3 arrays, 1 for each component of a vector at pos xyz

        float[][][] u = new float[dX + 1][dY][dZ];
        float[][][] v = new float[dX][dY + 1][dZ];
        float[][][] w = new float[dX][dY][dZ + 1];

        return new GridWrapper(cells, u, v, w, dX, dY, dZ);
    }

    public static void applyForces(GridWrapper grid) {
        // maybe apply gravity here if i want, will do heating walls and cooling walls here later
        byte[][][] cells = grid.getCells();

        for (int i = 0; i < cells.length; i++) {
            for (int j = 0; j < cells[i].length; j++) {
                for (int k = 0; k < cells[i][j].length; k++) {
                    grid.v[i][j][k] = grid.v[i][j][k] + (0.31f * dT);
                }
            }
        }
//        for (int i = 0; i < cells.length; i++) {
//            for (int j = 0; j < cells[i].length; j++) {
//                for (int k = 0; k < cells[i][j].length; k++) {
//                    if ((cells[i][j][k] & 0b00000100) != 0) {
//                        grid.v[i][j][k] = 12;
//                    }
//                }
//            }
//        }
    }

    public static void projection(GridWrapper grid) {
        // d (divergence) =
        // (u[i+1][j][k] - u[i][j][k]) +
        // (v[i][j+1][k] - v[i][j][k]) +
        // (w[i][j][k+1] - w[i][j][k])
        // * o
        // must be equal zero to force incompressibility
        // o is scalar for overrelaxation
        // 1 < o < 2

        byte[][][] cells = grid.getCells();

        float[][][] u = grid.u;
        float[][][] v = grid.v;
        float[][][] w = grid.w;

        for (int i = 0; i < cells.length; i++) {
            for (int j = 0; j < cells[i].length; j++) {
                for (int k = 0; k < cells[i][j].length; k++) {
                    float d =
                        1.8f *
                        ((u[i+1][j][k] - u[i][j][k]) +
                        (v[i][j+1][k] - v[i][j][k]) +
                        (w[i][j][k+1] - w[i][j][k]));
                    // s is the scalar for differentiating walls and other non-fluids
                    // if s is 6 then it scales the divergence normally because all 6 axis are fluids
                    float s =
                        grid.getS(i+1, j, k) +
                        grid.getS(i-1, j, k) +
                        grid.getS(i, j+1, k) +
                        grid.getS(i, j-1, k) +
                        grid.getS(i, j, k+1) +
                        grid.getS(i, j, k-1);
                    if (s == 0)
                        continue;
                    u[i][j][k] = u[i][j][k] + d * (grid.getS(i-1, j, k) / s); // east
                    u[i+1][j][k] = u[i+1][j][k] - d * (grid.getS(i+1, j, k) / s); // west
                    v[i][j][k] = v[i][j][k] + d * (grid.getS(i, j-1, k) / s); // north
                    v[i][j+1][k] = v[i][j+1][k] - d * (grid.getS(i, j+1, k) / s); // south
                    w[i][j][k] = w[i][j][k] + d * (grid.getS(i, j, k-1) / s); // top
                    w[i][j][k+1] = w[i][j][k+1] - d * (grid.getS(i, j, k+1) / s); // bottom
                }
            }
        }
    }

    public static float velAt(GridWrapper grid, float x, float y, float z, byte component) {
        // thank you to skorched for helping me a LOT with this part in particular
        float halfH = h / 2;

        // Clamping to grid bounds
        x = Math.max(Math.min(x, grid.dX * h), h);
        y = Math.max(Math.min(y, grid.dY * h), h);
        z = Math.max(Math.min(z, grid.dZ * h), h);

        float xOffset = 0;
        float yOffset = 0;
        float zOffset = 0;

        float[][][] activeField;

        // Get correct field and offsets
        if (component == 0) {
            activeField = grid.u;
            yOffset = halfH;
            zOffset = halfH;
        }
        else if (component == 1) {
            activeField = grid.v;
            xOffset = halfH;
            zOffset = halfH;
        }
        else if (component == 2) {
            activeField = grid.w;
            xOffset = halfH;
            yOffset = halfH;
        }
        else
            activeField = new float[][][]{};

        // Get offset target coordinates
        float adjX = x - xOffset;
        float adjY = y - yOffset;
        float adjZ = z - zOffset;

        // Find grid index
        int eastIndex = (int) Math.min(Math.floor(adjX / h), grid.dX - 1);
        // Find 1 dimensional distance
        float distanceX = (adjX - eastIndex * h) / h;
        // Find opposite grid index (index + 1 or if it's out of bounds, the maximum inbound point)
        int westIndex = Math.min(eastIndex + 1, grid.dX - 1);

        int topIndex = (int) Math.min(Math.floor(adjY / h), grid.dY - 1);
        float distanceY = (adjY - topIndex * h) / h;
        int bottomIndex = Math.min(topIndex + 1, grid.dY - 1);

        int northIndex = (int) Math.min(Math.floor(adjZ / h), grid.dZ - 1);
        float distanceZ = (adjZ - northIndex * h) / h;
        int southIndex = Math.min(northIndex + 1, grid.dZ - 1);

        // Complementary weights
        float sx = 1 - distanceX;
        float sy = 1 - distanceY;
        float sz = 1 - distanceZ;

        return
            sx * sy * sz * activeField[eastIndex][topIndex][northIndex] +
            distanceX * sy * sz * activeField[westIndex][topIndex][northIndex] +
            sx * distanceY * sz * activeField[eastIndex][bottomIndex][northIndex] +
            distanceX * distanceY * sz * activeField[westIndex][bottomIndex][northIndex] +
            sx * sy * distanceZ * activeField[eastIndex][topIndex][southIndex] +
            distanceX * sy * distanceZ * activeField[westIndex][topIndex][southIndex] +
            sx * distanceY * distanceZ * activeField[eastIndex][bottomIndex][southIndex] +
            distanceX * distanceY * distanceZ * activeField[westIndex][bottomIndex][southIndex];
    }

    public static void advection(GridWrapper grid) {
        byte[][][] cells = grid.getCells();

        float[][][] u = grid.u;
        float[][][] newU = u.clone();
        float[][][] v = grid.v;
        float[][][] newV = v.clone();
        float[][][] w = grid.w;
        float[][][] newW = w.clone();

        // for each vector component, compute its velocity: V
        // use averages of all surrounding vector components in each respective axis: uBar, vBar, wBar
        // (VdTx, VdTy, VdTz) = V * dT
        // x, y, z refers to the realspace position of the current vector component
        // we compute what x, y, z would be if the current V was reversed by one timestep ((xyz) - V);
        // then use that position to calculate what the velocity component of that particle is using a weighted average of the points closest to it
        // then set the value of the current vector component to the value of that particle after processing all cells

        for (int i = 0; i < cells.length; i++) {
            for (int j = 0; j < cells[i].length; j++) {
                for (int k = 0; k < cells[i][j].length; k++) {
                    // compute u
                    if ((grid.getS(i, j, k) != 0) && (grid.getS(i-1, j, k) != 0)) {
                        float vBar = (v[i][j][k] + v[i][j+1][k] + v[i-1][j][k] + v[i-1][j+1][k]) / 4;
                        float wBar = (w[i][j][k] + w[i][j][k+1] + w[i-1][j][k] + w[i-1][j][k+1]) / 4;
                        float VdTx = u[i][j][k] * dT;
                        float VdTy = vBar * dT;
                        float VdTz = wBar * dT;

                        float x = i * h;
                        float y = j * (h / 2);
                        float z = k * (h / 2);

                        newU[i][j][k] = velAt(grid, x - VdTx, y - VdTy, z - VdTz, (byte) 0);
                    }
                    // compute v
                    if ((grid.getS(i, j, k) != 0) && (grid.getS(i, j-1, k) != 0)) {
                        float uBar = (u[i][j][k] + u[i+1][j][k] + u[i][j-1][k] + u[i+1][j-1][k]) / 4;
                        float wBar = (w[i][j][k] + w[i][j][k+1] + w[i][j-1][k] + w[i][j-1][k+1]) / 4;
                        float VdTx = uBar * dT;
                        float VdTy = v[i][j][k] * dT;
                        float VdTz = wBar * dT;

                        float x = i * (h / 2);
                        float y = j * h;
                        float z = k * (h / 2);

                        newV[i][j][k] = velAt(grid, x - VdTx, y - VdTy, z - VdTz, (byte) 1);
                    }
                    // compute w
                    if ((grid.getS(i, j, k) != 0) && (grid.getS(i, j, k-1) != 0)) {
                        float uBar = (u[i][j][k] + u[i+1][j][k] + u[i][j][k-1] + u[i+1][j][k-1]) / 4;
                        float vBar = (v[i][j][k] + v[i][j+1][k] + v[i][j][k-1] + v[i][j+1][k-1]) / 4;
                        float VdTx = uBar * dT;
                        float VdTy = vBar * dT;
                        float VdTz = w[i][j][k] * dT;

                        float x = i * (h / 2);
                        float y = j * (h / 2);
                        float z = k * h;

                        newW[i][j][k] = velAt(grid, x - VdTx, y - VdTy, z - VdTz, (byte) 2);
                    }
                }
            }
        }
        u = newU;
        v = newV;
        w = newW;
    }
}
