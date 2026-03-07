package com.gtnewhorizons.galaxia.utility;

import java.util.Random;

public class Noise {

    protected double scale = 1;
    protected long seed;
    protected int[] perm;
    // each line is x1, y1, z1, x2, y2, z2
    private static final int[][] cellCorners3D = { { 0, 0, 1, 0, 1, 1 }, { 0, 1, 0, 0, 1, 1 }, { 0, 0, 0, 0, 0, 0 },
        { 0, 1, 0, 1, 1, 0 }, { 0, 0, 1, 1, 0, 1 }, { 0, 0, 0, 0, 0, 0 }, { 1, 0, 0, 1, 0, 1 }, { 1, 0, 0, 1, 1, 0 } };

    // F(n) = (sqrt(n+1)-1)/n
    public static final double F2D = (MathUtil.SQRT_3 - 1.0) / 2.0;
    public static final double F3D = 1.D / 3.D;
    public static final double F4D = (MathUtil.SQRT_5 - 1.0) / 4.0;
    // G(n) = (1-1/(sqrt(n+1)))/n
    public static final double G2D = (1.0 - 1.0 / MathUtil.SQRT_3) / 2.0;
    public static final double G3D = 1.0 / 6.0;
    public static final double G4D = (1.0 - 1.0 / MathUtil.SQRT_5) / 4.0;

    public Noise(double scale, long seed) {
        Random rand = new Random(seed);
        this.scale = scale;
        this.seed = seed;
        this.perm = new int[512];
        for (int i = 0; i < 256; i++) {
            perm[i] = i;
        }
        for (int i = 0; i < 256; i++) {
            int j = rand.nextInt(256 - i);
            int k = perm[i];
            perm[i] = perm[j];
            perm[j] = k;
            perm[i + 256] = perm[i];
        }
    }

    public long getSeed() {
        return seed;
    }

    public void setScale(double scale) {
        this.scale = scale;
    }

    public double getScale() {
        return scale;
    }

    /**
     * gets the 2d value and derivative of a single corner of a simplex cell
     *
     * @param x        pos x
     * @param z        pos z
     * @param contribX corner to calculate from
     * @param contribZ corner to calculate from
     * @return 3 value array containing the value then the part derivative along x and z
     */
    protected double[] getSimplexContrib2D(double x, double z, double contribX, double contribZ) {
        // get a deterministically random gradient vector for each point
        int seed = perm[perm[((int) contribX) & 255] + ((int) contribZ) & 255];
        MathUtil.Vec2D grad = Rand.unitVec2D[seed % Rand.unitVec2D.length];
        // unskew the corner back to their original position
        double unskew = (contribX + contribZ) * G2D;
        // xi = xi' - (x₁'+x₂'+...xn') * G
        contribX -= unskew;
        contribZ -= unskew;
        // distances between the point and corner, max spacing is cell size (SQRT_2 / 4.) so need to renormalize later
        MathUtil.Vec2D delta = new MathUtil.Vec2D(x - contribX, z - contribZ);
        // only calculate the contribution if the corner is close enough
        double s = 0.5 - delta.distSquare();
        s *= (int) (s + 1); // scuffed fast max(0, s) for 0 branch, since s is between 0.5 and -1
        // dot product of the distance vector and the chosen corner gradient
        double dot = delta.dot(grad);
        double s2 = s * s, s4 = s2 * s2, s3dot = 8.0 * s * s2 * dot;
        // s⁴×dot for each corner, ÷ 0.5⁴ to normalize it
        double res = s4 * dot * 16.0 * 8.0 / MathUtil.SQRT_2;
        // partial derivative in x is (Δx×s⁴-8Δx×s³×dot)
        MathUtil.Vec2D deriv = grad.mul(s4)
            .sub(delta.mul(s3dot));
        /// divided by 5/6 I guess?!?
        return new double[] { res * 6.0 / 5.0, deriv.x, deriv.y };
    }

    /**
     * calculate 2D simplex and derivative at point {x, z}
     *
     * @param x x coordinate
     * @param z z coordinate
     * @return 3 value array containing the value (between -1 and -1) and the partial
     *         derivative along x and z
     */
    public double[] simplex2D(double x, double z) {
        // scale the coordinate depending on the scale of the noise
        x /= scale;
        z /= scale;
        // skew the coordinate to find the cells, xi' = xi + (x₁+x₂+...xn) * F
        double skew_factor = (x + z) * F2D;
        double xp = x + skew_factor, zp = z + skew_factor;

        // get the corners and the position of the point inside the cell
        int xb = (int) xp, zb = (int) zp;
        double xi = xp - xb, zi = zp - zb;

        // chose which cell we are in
        // a, b and c are the three corners of the 2D cell
        double[] res_a = getSimplexContrib2D(x, z, xb, zb);
        double[] res_b = getSimplexContrib2D(x, z, xb + (xi > zi ? 1 : 0), zb + (xi > zi ? 0 : 1));
        double[] res_c = getSimplexContrib2D(x, z, xb + 1, zb + 1);

        // sum everything together
        return new double[] { res_a[0] + res_b[0] + res_c[0], res_a[1] + res_b[1] + res_c[1],
            res_a[2] + res_b[2] + res_c[2] };
    }

    /**
     * gets the 3d value and derivative of a single corner of a simplex cell
     *
     * @param x        pos x
     * @param y        pos y
     * @param z        pos z
     * @param contribX corner to calculate from
     * @param contribY corner to calculate from
     * @param contribZ corner to calculate from
     * @return 4 value array containing the value then the part derivative along x, y and z
     */
    protected double[] getSimplexContrib3D(MathUtil.Vec3D point, MathUtil.Vec3D contrib) {
        // get a deterministically random gradient vector for each point
        int seed = perm[perm[perm[((int) contrib.x) & 255] + ((int) contrib.y) & 255] + ((int) contrib.z) & 255];
        MathUtil.Vec3D grad = Rand.unitVec3D[seed % Rand.unitVec3D.length];
        // unskew the corner back to their original position
        double unskew = (contrib.x + contrib.y + contrib.z) * G3D;
        // xi = xi' - (x₁'+x₂'+...xn') * G
        contrib.x -= unskew;
        contrib.y -= unskew;
        contrib.z -= unskew;
        // distances between the point and corner, max spacing is cell size (SQRT_6 / 6.) so need to renormalize later
        MathUtil.Vec3D delta = new MathUtil.Vec3D(point.x - contrib.x, point.y - contrib.y, point.z - contrib.z);
        // only calculate the contribution if the corner is close enough
        double s = 0.5 - delta.distSquare();
        s *= (int) (s + 1); // scuffed fast max(0, s) for 0 branch, since s is between 0.5 and -1
        // dot product of the distance vector and the chosen corner gradient
        double dot = delta.dot(grad);
        double s2 = s * s, s4 = s2 * s2, s3dot = 8.0 * s * s2 * dot;
        // s⁴×dot for each corner, ÷ 0.5⁴ to normalize it
        double res = s4 * dot * 16.0 * 12.0 / MathUtil.SQRT_6;
        // partial derivative in x is (Δx×s⁴-8Δx×s³×dot)
        MathUtil.Vec3D deriv = grad.mul(s4)
            .sub(delta.mul(s3dot)); // TODO: actually recalculate for 3D (it might be ok already)
        return new double[] { res, deriv.x, deriv.y, deriv.z };
    }

    /**
     * calculate 2D simplex and derivative at point {x, z}
     *
     * @param x x coordinate
     * @param z z coordinate
     * @return 3 value array containing the value (normalized to [-1, 1]) and the partial
     *         derivative along x and z
     */
    public double[] simplex3D(double x, double y, double z) {
        // scale the coordinate depending on the scale of the noise
        x /= scale;
        y /= scale;
        z /= scale;
        // skew the coordinate to find the cells, xi' = xi + (x₁+x₂+...xn) * F
        double skew_factor = (x + y + z) * F3D;
        double xp = x + skew_factor, yp = y + skew_factor, zp = z + skew_factor;

        // get the corners and the position of the point inside the cell
        int xb = (int) xp, yb = (int) yp, zb = (int) zp;
        double xi = xp - xb, yi = yp - yb, zi = zp - zb;

        // branchless comparison and choosing the right 2nd and 3rd corners
        int xy = (int) (xi - yi + 1.0), xz = (int) (xi - zi + 1.0), yz = (int) (yi - zi + 1.0);
        // they are all 1 or 0 depending on which is higher
        int[] corner = cellCorners3D[(xy << 2) + (xz << 1) + yz];
        int xb1 = xb + corner[0], yb1 = yb + corner[1], zb1 = zb + corner[2];
        int xb2 = xb + corner[3], yb2 = yb + corner[4], zb2 = zb + corner[5];

        MathUtil.Vec3D point = new MathUtil.Vec3D(x, y, z);
        // chose which cell we are in
        // a, b and c are the three corners of the 2D cell
        double[] res_a = getSimplexContrib3D(point, new MathUtil.Vec3D(xb, yb, zb));
        double[] res_b = getSimplexContrib3D(point, new MathUtil.Vec3D(xb1, yb1, zb1));
        double[] res_c = getSimplexContrib3D(point, new MathUtil.Vec3D(xb2, yb2, zb2));
        double[] res_d = getSimplexContrib3D(point, new MathUtil.Vec3D(xb + 1, yb + 1, zb + 1));

        // sum everything together
        return new double[] { res_a[0] + res_b[0] + res_c[0] + res_d[0], res_a[1] + res_b[1] + res_c[1] + res_d[1],
            res_a[2] + res_b[2] + res_c[2] + res_d[2] };
    }

    /**
     * calculate multiple octaves of 2D noise along a 2D range
     * the noise returned is normalized between 0 and 1
     *
     * @param x       start of the range in X
     * @param z       start of the range in Z
     * @param sizeX   size of the range in X
     * @param sizeZ   size of the range in Z
     * @param octaves number of octaves to calculate
     * @param noise   noise generator used to generate octaves
     * @return [heightmap (normalized to [-1, 1])][X derivative][Z derivative]
     */
    public static double[][] simplexOctaves2D(double x, double z, int sizeX, int sizeZ, int octaves, Noise noise) {
        double[][] result = new double[3][];
        result[0] = new double[sizeX * sizeZ];
        result[1] = new double[sizeX * sizeZ];
        result[2] = new double[sizeX * sizeZ];
        double amplitude = 0.25;
        for (int o = 1; o <= octaves; ++o) {
            for (int x1 = 0; x1 < sizeX; ++x1) {
                for (int z1 = 0; z1 < sizeZ; ++z1) {
                    // TODO: change that (don't use the same seed for all octaves !!!
                    double[] current = noise.simplex2D((x1 + x) / amplitude, (z1 + z) / amplitude);
                    result[0][x1 + (z1 << 4)] += current[0] * amplitude;
                    result[1][x1 + (z1 << 4)] += current[1] * amplitude;
                    result[2][x1 + (z1 << 4)] += current[2] * amplitude;
                }
            }
            amplitude /= 2.0;
        }
        return result;
    }

    /**
     * calculate multiple octaves of 3D noise along a 3D range
     * the noise returned is normalized between 0 and 1
     *
     * @param x       start of the range in X
     * @param z       start of the range in Z
     * @param sizeX   size of the range in X
     * @param sizeZ   size of the range in Z
     * @param octaves number of octaves to calculate
     * @param noise   noise generator used to generate octaves
     * @return [heightmap (normalized to [-1, 1])][X derivative][Z derivative]
     */
    public static double[][] simplexOctaves3D(double x, double y, double z, int sizeX, int sizeY, int sizeZ,
        int octaves, Noise noise) {
        double[][] result = new double[4][];
        result[0] = new double[sizeX * sizeY * sizeZ];
        result[1] = new double[sizeX * sizeY * sizeZ];
        result[2] = new double[sizeX * sizeY * sizeZ];
        result[3] = new double[sizeX * sizeY * sizeZ];
        double amplitude = 0.25;
        for (int o = 1; o <= octaves; ++o) {
            for (int x1 = 0; x1 < sizeX; ++x1) {
                for (int y1 = 0; y1 < sizeY; ++y1) {
                    for (int z1 = 0; z1 < sizeZ; ++z1) {
                        // TODO: change that (don't use the same seed for all octaves !!!
                        double[] current = noise.simplex3D(
                            (x + (double) x1) / amplitude,
                            (y + (double) y1) / amplitude,
                            (z + (double) z1) / amplitude);
                        result[0][x1 + (y1 << 4) + (z1 << 8)] += current[0] * amplitude;
                        result[1][x1 + (y1 << 4) + (z1 << 8)] += current[1] * amplitude;
                        result[2][x1 + (y1 << 4) + (z1 << 8)] += current[2] * amplitude;
                        result[3][x1 + (y1 << 4) + (z1 << 8)] += current[2] * amplitude;
                    }
                }
            }
            amplitude /= 2.0;
        }
        return result;
    }

    public double[] craterNoise2D(double x, double z) {
        return craterNoise2D(x, z, 1.D, 1.D, 0.D);
    }

    public double[] craterNoise2D(double x, double z, double density) {
        return craterNoise2D(x, z, density, 1.D, 0.D);
    }

    protected double[] get2dCraterContrib(double x, double z, double contribX, double contribZ, double density,
        double maxSize, double minSize) {
        // get the seed of the vertex
        long seed = ((long) ((Object) contribX).hashCode() << 32 + ((Object) contribZ).hashCode()) ^ this.seed;
        Random rand = new Random(seed + 1);
        // only if dense enough
        if (!(rand.nextDouble() < density)) return new double[] { 0D, 1D, 0D };

        // unskew the corner back to their original position
        // x = xn' - (x₁'+x₂'+...xn') * G
        double unskew = (contribX + contribZ) * G2D;
        contribX -= unskew;
        contribZ -= unskew;

        // get a deterministically random direction, distance and size for each point
        double[] grad = Rand.getRandomVector(seed, 2);
        double d = rand.nextDouble() * MathUtil.SQRT_2 / 4.;
        grad = new double[] { grad[0] * d, grad[1] * d };
        double size = (rand.nextDouble() * (maxSize - minSize) + minSize) * MathUtil.SQRT_2 / 4.;

        // distances between the point and circle center
        double[] delta = new double[] { x - contribX - grad[0], x - contribZ - grad[1] };

        double distance = delta[0] * delta[0] + delta[1] * delta[1] + size * size;
        if (distance > 0D) return new double[] { 0D, 1D, 0D };

        // derivative of x² is 2x, same for partial derivative with respect to x of x²+y²
        return new double[] { distance, delta[0] * 2D, delta[1] * 2D };
    }

    public double[] craterNoise2D(double x, double z, double density, double maxSize, double minSize) {
        // scale the coordinate depending on the scale of the noise
        x = x / scale;
        z = z / scale;
        // skew the coordinate to find the cells, x' = xn + (x₁+x₂+...xn) * F
        double skew_factor = (x + z) * F2D;
        double xp = x + skew_factor, zp = z + skew_factor;

        // get the corners and the position of the point inside the cell
        int xb = (int) Math.floor(xp), zb = (int) Math.floor(zp);
        double xi = xp - xb, zi = zp - zb;

        // chose which cell we are in
        // a, b and c are the three corners of the 2D cell
        double[] resA = get2dCraterContrib(x, z, xb, zb, density, minSize, maxSize);
        double[] resB = get2dCraterContrib(
            x,
            z,
            xb + (xi > zi ? 1D : 0D),
            zb + (xi > zi ? 0D : 1D),
            density,
            minSize,
            maxSize);
        double[] resC = get2dCraterContrib(x, z, xb + 1, zb + 1, density, minSize, maxSize);

        // put everything together
        // there's probably better blending fuctions for craters
        // like min
        return new double[] { resA[0] + resB[0] + resC[0], resA[1] + resB[1] + resC[1], resA[2] + resB[2] + resC[2] };
    }
}
