package com.gtnewhorizons.galaxia.utility;

import java.util.Random;

public class Rand {

    private static final double diag2 = MathUtil.SQRT_2 / 2.0;
    private static final double diag3 = MathUtil.SQRT_3 / 3.0;

    public static final MathUtil.Vec2D[] unitVec2D = new MathUtil.Vec2D[] { new MathUtil.Vec2D(0, 1),
        new MathUtil.Vec2D(0, -1), new MathUtil.Vec2D(1, 0), new MathUtil.Vec2D(-1, 0),
        new MathUtil.Vec2D(diag2, diag2), new MathUtil.Vec2D(diag2, -diag2), new MathUtil.Vec2D(-diag2, diag2),
        new MathUtil.Vec2D(-diag2, -diag2), };

    public static final MathUtil.Vec3D[] unitVec3D = new MathUtil.Vec3D[] { new MathUtil.Vec3D(0, 0, 1),
        new MathUtil.Vec3D(0, 0, -1), new MathUtil.Vec3D(0, 1, 0), new MathUtil.Vec3D(0, -1, 0),
        new MathUtil.Vec3D(1, 0, 0), new MathUtil.Vec3D(-1, 0, 0), new MathUtil.Vec3D(diag3, diag3, diag3),
        new MathUtil.Vec3D(diag3, diag3, -diag3), new MathUtil.Vec3D(diag3, -diag3, diag3),
        new MathUtil.Vec3D(diag3, -diag3, -diag3), new MathUtil.Vec3D(-diag3, diag3, diag3),
        new MathUtil.Vec3D(-diag3, diag3, -diag3), new MathUtil.Vec3D(-diag3, -diag3, diag3),
        new MathUtil.Vec3D(-diag3, -diag3, -diag3), };
    public static final MathUtil.Vec4D[] unitVec4D = new MathUtil.Vec4D[] { new MathUtil.Vec4D(0, 0, 0, 1),
        new MathUtil.Vec4D(0, 0, 0, -1), new MathUtil.Vec4D(0, 0, 1, 0), new MathUtil.Vec4D(0, 0, -1, 0),
        new MathUtil.Vec4D(0, 1, 0, 0), new MathUtil.Vec4D(0, -1, 0, 0), new MathUtil.Vec4D(1, 0, 0, 0),
        new MathUtil.Vec4D(-1, 0, 0, 0), new MathUtil.Vec4D(0.5, 0.5, 0.5, 0.5),
        new MathUtil.Vec4D(0.5, 0.5, 0.5, -0.5), new MathUtil.Vec4D(0.5, 0.5, -0.5, 0.5),
        new MathUtil.Vec4D(0.5, 0.5, -0.5, -0.5), new MathUtil.Vec4D(0.5, -0.5, 0.5, 0.5),
        new MathUtil.Vec4D(0.5, -0.5, 0.5, -0.5), new MathUtil.Vec4D(0.5, -0.5, -0.5, 0.5),
        new MathUtil.Vec4D(0.5, -0.5, -0.5, -0.5), new MathUtil.Vec4D(-0.5, 0.5, 0.5, 0.5),
        new MathUtil.Vec4D(-0.5, 0.5, 0.5, -0.5), new MathUtil.Vec4D(-0.5, 0.5, -0.5, 0.5),
        new MathUtil.Vec4D(-0.5, 0.5, -0.5, -0.5), new MathUtil.Vec4D(-0.5, -0.5, 0.5, 0.5),
        new MathUtil.Vec4D(-0.5, -0.5, 0.5, -0.5), new MathUtil.Vec4D(-0.5, -0.5, -0.5, 0.5),
        new MathUtil.Vec4D(-0.5, -0.5, -0.5, -0.5), };

    /**
     * generate a seeded random unit vector
     *
     * @param seed which seed to generate the vector from
     * @param dim  dimension of the vector
     * @return the vector
     */
    public static double[] getRandomVector(long seed, int dim) {
        Random rand = new Random(seed);
        return switch (dim) {
            case 0 -> new double[] {}; // haha funny.
            case 1 -> new double[] { rand.nextInt() % 2 == 0 ? -1.D : 1.D };
            case 2 -> {
                double theta = rand.nextDouble() * Math.PI * 2.D;
                yield new double[] { Math.cos(theta), Math.sin(theta) };
            }
            case 3 -> {
                double cos_theta = rand.nextDouble() * 2.D - 1.D;
                double theta = Math.acos(cos_theta);
                double phi = rand.nextDouble() * Math.PI * 2.D;
                // classic parametrization of the sphere
                double x = cos_theta;
                double y = Math.sin(theta) * Math.cos(phi);
                double z = Math.sin(theta) * Math.sin(phi);
                yield new double[] { x, y, z };
            }
            case 4 -> {
                // generate two random points inside a disk
                // could do it cheaper by generating inside a square then discarding outside a circle,
                // but it would need 3 square roots anyway for the 4D sphere so not that much cheaper
                double r1 = rand.nextDouble();
                double r1_sqrt = Math.sqrt(r1);
                double theta1 = rand.nextDouble() * Math.PI * 2.D;
                double r2 = rand.nextDouble();
                double r2_sqrt = Math.sqrt(r2);
                double theta2 = rand.nextDouble() * Math.PI * 2.D;
                // convert to cartesian coordinates, using sqrt(r) so that the distribution is uniform
                double x = r1_sqrt * Math.cos(theta1);
                double y = r1_sqrt * Math.sin(theta1);
                double z = r2_sqrt * Math.cos(theta2);
                double w = r2_sqrt * Math.sin(theta2);
                // renormalize z and w to be on the surface of the hypersphere
                double n = Math.sqrt((1.D - r1) / r2);
                z = z / n;
                w = w / n;
                yield new double[] { x, y, z, w };
            }
            default -> throw new RuntimeException(
                "this method generalize for higher dimensions but it is left as an exercise to the reader\n"
                    + "https://pubs.aip.org/aip/cip/article/2/6/55/137070/An-efficient-method-for-generating-a-uniform");
        };
    }

    public static MathUtil.Vec2D getFast2DVector(long seed) {
        return unitVec2D[new Random(seed).nextInt() % unitVec2D.length];
    };

    public static MathUtil.Vec3D getFast3DVector(long seed) {
        return unitVec3D[new Random(seed).nextInt() % unitVec3D.length];
    };

    public static MathUtil.Vec4D getFast4DVector(long seed) {
        return unitVec4D[new Random(seed).nextInt() % unitVec4D.length];
    };

}
