package com.gtnewhorizons.galaxia.vaporchamber;

import com.gtnewhorizon.gtnhlib.blockpos.BlockPos;

import java.util.ArrayList;
import java.util.List;

// formulas extrapolated from https://matthias-research.github.io/pages/tenMinutePhysics/17-fluidSim.pdf
// code overview from https://github.com/matthias-research/pages/blob/master/tenMinutePhysics/17-fluidSim.html (i dont use any part in any significant portion, i only looked at it for inspiration on how to make this)
// thank you matthias for the helpful tutorial
// made by me! (chloe)

public class EulerianSimAPI {
    public static final float dT = 1/120f;
    public static final float h = 0.5f;
    public static final int hINT = (int) (1 / h);

    private byte[][][] cells;

    public float[][][] u;
    public float[][][] v;
    public float[][][] w;

    int dX;
    int dY;
    int dZ;

    public byte[][][] s;

    ArrayList<Long> time = new ArrayList<>();

    public EulerianSimAPI(byte[][][] _cells, float[][][] _u, float[][][] _v, float[][][] _w, int _dX, int _dY, int _dZ) {
        u = _u;
        v = _v;
        w = _w;

        dX = _dX;
        dY = _dY;
        dZ = _dZ;

        if (_cells == null) {
            cells = new byte[][][]{};
        }
        else {
            cells = _cells;
        }

        s = new byte[dX][dY][dZ];

        int lengthX = cells.length;
        for (int i = 0; i < lengthX; i++) {
            int lengthY = cells[i].length;
            for (int j = 0; j < lengthY; j++) {
                int lengthZ = cells[i][j].length;
                for (int k = 0; k < lengthZ; k++) {
                    s[i][j][k] = ((cells[i][j][k] & (byte) 0b00000010) == 0 && (cells[i][j][k] & (byte) 0b00000001) != 0) ? (byte) 1 : (byte) 0;
                }
            }
        }
    }

    public byte[][][] getCells() {
        if (cells == null) {
            return new byte[][][]{};
        }
        return cells;
    }

    public void run() {
        applyForces();
        long start = System.nanoTime();
        for (int i = 0; i <= 10; i++) {
            projection();
        }
        long end = System.nanoTime();

        time.add((end - start));
        if (time.size() == 100) {
            double accum = 0;
            for (long time : time) {
                accum += time;
            }
            System.out.println(accum / 100000000d);
            time = new ArrayList<>();
        }

        advection();
    }

    public static EulerianSimAPI createGrid(List<BlockPos> internals, List<BlockPos> externals, BlockPos heat) {
        List<BlockPos> combined = new ArrayList<>(internals);
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

        int dX_ = ((rightBound - leftBound + 1) * hINT);
        int dY_ = ((upperBound - lowerBound + 1) * hINT);
        int dZ_ = ((forwardBound - backwardBound + 1) * hINT);

        byte[][][] cells = new byte[dX_][dY_][dZ_];

        // bit 0: is a fluid cell
        // bit 1: is a wall

        for (BlockPos internal : internals) {
            for (int i = 0; i < h; i++) {
                for (int dx = 0; dx < hINT; dx++)
                    for (int dy = 0; dy < hINT; dy++)
                        for (int dz = 0; dz < hINT; dz++) {
                            cells[(internal.getX() - leftBound) * hINT + dx][(internal.getY() - lowerBound) * hINT + dy][(internal.getZ() - backwardBound) * hINT + dz] = (byte) 0b00000001;
                        }
            }
        }
        for (BlockPos external : externals) {
            for (int i = 0; i < h; i++) {
                for (int dx = 0; dx < hINT; dx++)
                    for (int dy = 0; dy < hINT; dy++)
                        for (int dz = 0; dz < hINT; dz++) {
                             cells[(external.getX() - leftBound) * hINT + dx][(external.getY() - lowerBound) * hINT + dy][(external.getZ() - backwardBound) * hINT + dz] = (byte) 0b00000010;
                }
            }
        }

//        cells[heat.getX() - leftBound][heat.getY() - lowerBound][heat.getZ() - backwardBound] = (byte) 0b00000101;

        int a = 4;
        cells[dX_/2][hINT+1][dZ_/2] = (byte) 0b00000101;
        //cells[6+a][5][5+a] = (byte) 0b00000101;
        //cells[5+a][5][6+a] = (byte) 0b00000101;
        //cells[6+a][5][6+a] = (byte) 0b00000101;


        // create 3 arrays, 1 for each component of a vector at pos xyz

        float[][][] u = new float[dX_ + 1][dY_][dZ_];
        float[][][] v = new float[dX_][dY_ + 1][dZ_];
        float[][][] w = new float[dX_][dY_][dZ_ + 1];

        return new EulerianSimAPI(cells, u, v, w, dX_, dY_, dZ_);
    }

    public void applyForces() {
        // maybe apply gravity here if i want, will do heating walls and cooling walls here later
        byte[][][] cells = getCells();

//        for (int i = 0; i < cells.length; i++) {
//            for (int j = 0; j < cells[i].length; j++) {
//                for (int k = 0; k < cells[i][j].length; k++) {
//                    if (j % 2 == 0 && getS(i, j, k) == 1) {
//                        v[i][j][k] = v[i][j][k] + (-10f * dT);
//                    }
//                }
//            }
//        }
        for (int i = 0; i < cells.length; i++) {
            for (int j = 0; j < cells[i].length; j++) {
                for (int k = 0; k < cells[i][j].length; k++) {
                    if ((cells[i][j][k] & 0b00000100) != 0) {
                        v[i][j][k] = 30;
                    }
                }
            }
        }
    }

    public void projection() {
        // d (divergence) =
        // (u[i+1][j][k] - u[i][j][k]) +
        // (v[i][j+1][k] - v[i][j][k]) +
        // (w[i][j][k+1] - w[i][j][k])
        // * o
        // must be equal zero to force incompressibility
        // o is scalar for overrelaxation
        // 1 < o < 2

        byte[][][] cells = getCells();

//        float[][][] newU = new float[dX + 1][dY][dZ];
//        float[][][] newV = new float[dX][dY + 1][dZ];
//        float[][][] newW = new float[dX][dY][dZ + 1];


        int lengthX = cells.length;
        for (int i = 0; i < lengthX; i++) {
            int lengthY = cells[i].length;
            for (int j = 0; j < lengthY; j++) {
                int lengthZ = cells[i][j].length;
                for (int k = 0; k < lengthZ; k++) {
                    if (s[i][j][k] == 0) {
                        continue;
                    }
                    float d =
                        1.95f *
                            (u[i+1][j][k] - u[i][j][k] +
                                v[i][j+1][k] - v[i][j][k] +
                                w[i][j][k+1] - w[i][j][k]);
                    // s is the scalar for differentiating walls and other non-fluids
                    // if s is 6 then it scales the divergence normally because all 6 axis are fluids
                    float S =
                        s[i + 1][j][k] +
                            s[i - 1][j][k] +
                            s[i][j + 1][k] +
                            s[i][j - 1][k] +
                            s[i][j][k + 1] +
                            s[i][j][k - 1];
                    if (S == 0)
                        continue;

                    S = 1f / S;

                    u[i][j][k] = u[i][j][k] + (d * (s[i - 1][j][k] * S)); // east
                    u[i+1][j][k] = u[i+1][j][k] - (d * (s[i + 1][j][k] * S)); // west
                    v[i][j][k] = v[i][j][k] + (d * (s[i][j - 1][k] * S)); // north
                    v[i][j+1][k] = v[i][j+1][k] - (d * (s[i][j + 1][k] * S)); // south
                    w[i][j][k] = w[i][j][k] + (d * (s[i][j][k - 1] * S)); // top
                    w[i][j][k+1] = w[i][j][k+1] - (d * (s[i][j][k + 1] * S)); // bottom
                }
            }
        }
//        u = newU;
//        v = newV;
//        w = newW;
    }

    public float velAt(float x, float y, float z, byte component) {
        // thank you to skorched for helping me a LOT with this part in particular
        float halfH = h / 2;

        // Clamping to grid bounds
        x = Math.max(Math.min(x, dX * h), h);
        y = Math.max(Math.min(y, dY * h), h);
        z = Math.max(Math.min(z, dZ * h), h);

        float xOffset = 0;
        float yOffset = 0;
        float zOffset = 0;

        float[][][] activeField;

        // Get correct field and offsets
        if (component == 0) {
            activeField = u;
            yOffset = halfH;
            zOffset = halfH;
        }
        else if (component == 1) {
            activeField = v;
            xOffset = halfH;
            zOffset = halfH;
        }
        else if (component == 2) {
            activeField = w;
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
        int eastIndex = (int) Math.min(Math.floor(adjX / h), dX - 1);
        // Find 1 dimensional distance
        float distanceX = (adjX - eastIndex * h) / h;
        // Find opposite grid index (index + 1 or if it's out of bounds, the maximum inbound point)
        int westIndex = Math.min(eastIndex + 1, dX - 1);

        int topIndex = (int) Math.min(Math.floor(adjY / h), dY - 1);
        float distanceY = (adjY - topIndex * h) / h;
        int bottomIndex = Math.min(topIndex + 1, dY - 1);

        int northIndex = (int) Math.min(Math.floor(adjZ / h), dZ - 1);
        float distanceZ = (adjZ - northIndex * h) / h;
        int southIndex = Math.min(northIndex + 1, dZ - 1);

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

    public void advection() {
        byte[][][] cells = getCells();

        float[][][] newU = new float[dX + 1][dY][dZ];
        float[][][] newV = new float[dX][dY + 1][dZ];
        float[][][] newW = new float[dX][dY][dZ + 1];

        // for each vector component, compute its velocity: V
        // use averages of all surrounding vector components in each respective axis: uBar, vBar, wBar
        // (VdTx, VdTy, VdTz) = V * dT
        // x, y, z refers to the realspace position of the current vector component
        // we compute what x, y, z would be if the current V was reversed by one timestep ((xyz) - V);
        // then use that position to calculate what the velocity component of that particle is using a weighted average of the points closest to it
        // then set the value of the current vector component to the value of that particle after processing all cells

        int lengthX = cells.length;
        for (int i = 0; i < lengthX; i++) {
            int lengthY = cells[i].length;
            for (int j = 0; j < lengthY; j++) {
                int lengthZ = cells[i][j].length;
                for (int k = 0; k < lengthZ; k++) {
                    // compute u
                    if ((s[i][j][k] != 0)) {
                        if (s[i - 1][j][k] != 0) {
                            float vBar = (v[i][j][k] + v[i][j + 1][k] + v[i - 1][j][k] + v[i - 1][j + 1][k]) / 4;
                            float wBar = (w[i][j][k] + w[i][j][k + 1] + w[i - 1][j][k] + w[i - 1][j][k + 1]) / 4;
                            float VdTx = u[i][j][k] * dT;
                            float VdTy = vBar * dT;
                            float VdTz = wBar * dT;

                            float x = i * h;
                            float y = j * h + (h / 2);
                            float z = k * h + (h / 2);

                            newU[i][j][k] = velAt(x - VdTx, y - VdTy, z - VdTz, (byte) 0);
                        }
                    }
                    // compute v
                    if ((s[i][j][k] != 0)) {
                        if (s[i][j - 1][k] != 0) {
                            float uBar = (u[i][j][k] + u[i + 1][j][k] + u[i][j - 1][k] + u[i + 1][j - 1][k]) / 4;
                            float wBar = (w[i][j][k] + w[i][j][k + 1] + w[i][j - 1][k] + w[i][j - 1][k + 1]) / 4;
                            float VdTx = uBar * dT;
                            float VdTy = v[i][j][k] * dT;
                            float VdTz = wBar * dT;

                            float x = i * h + (h / 2);
                            float y = j * h;
                            float z = k * h + (h / 2);

                            newV[i][j][k] = velAt(x - VdTx, y - VdTy, z - VdTz, (byte) 1);
                        }
                    }
                    // compute w
                    if ((s[i][j][k] != 0)) {
                        if ((int) s[i][j][k - 1] != 0) {
                            float uBar = (u[i][j][k] + u[i + 1][j][k] + u[i][j][k - 1] + u[i + 1][j][k - 1]) / 4;
                            float vBar = (v[i][j][k] + v[i][j + 1][k] + v[i][j][k - 1] + v[i][j + 1][k - 1]) / 4;
                            float VdTx = uBar * dT;
                            float VdTy = vBar * dT;
                            float VdTz = w[i][j][k] * dT;

                            float x = i * h + (h / 2);
                            float y = j * h + (h / 2);
                            float z = k * h;

                            newW[i][j][k] = velAt(x - VdTx, y - VdTy, z - VdTz, (byte) 2);
                        }
                    }
                }
            }
        }
        u = newU;
        v = newV;
        w = newW;
    }
}
