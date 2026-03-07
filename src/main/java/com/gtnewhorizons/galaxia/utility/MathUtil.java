package com.gtnewhorizons.galaxia.utility;

public class MathUtil {

    public static final double SQRT_2 = Math.sqrt(2);
    public static final double SQRT_3 = Math.sqrt(3);
    public static final double SQRT_5 = Math.sqrt(5);
    public static final double SQRT_6 = SQRT_2 * SQRT_3;

    public static double lerp(double t, double x1, double x2) {
        return t * (x2 - x1) + x1;
    }

    /**
     * smoothstep with first and second order derivative null at 0 and 1
     *
     * @param s
     * @return s smoothed
     */
    public static double smoothStep(double s) {
        return s * s * s * (s * (s * 6D - 15D) + 10D);
    }

    public static class Vec2D {

        public double x;
        public double y;

        Vec2D(double x, double y) {
            this.x = x;
            this.y = y;
        }

        public double dot(Vec2D other) {
            return this.x * other.x + this.y * other.y;
        }

        public double distSquare() {
            return this.dot(this);
        }

        public double dist() {
            return Math.sqrt(this.distSquare());
        }

        public Vec2D mul(double other) {
            return new Vec2D(this.x * other, this.y * other);
        }

        public Vec2D add(Vec2D other) {
            return new Vec2D(this.x + other.x, this.y + other.y);
        }

        public Vec2D sub(Vec2D other) {
            return new Vec2D(this.x - other.x, this.y - other.y);
        }
    }

    public static class Vec3D {

        public double x;
        public double y;
        public double z;

        public Vec3D(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public double dot(Vec3D other) {
            return this.x * other.x + this.y * other.y + this.z * other.z;
        }

        public double distSquare() {
            return this.dot(this);
        }

        public double dist() {
            return Math.sqrt(this.distSquare());
        }

        public Vec3D mul(double other) {
            return new Vec3D(this.x * other, this.y * other, this.z * other);
        }

        public Vec3D add(Vec3D other) {
            return new Vec3D(this.x + other.x, this.y + other.y, this.z + other.z);
        }

        public Vec3D sub(Vec3D other) {
            return new Vec3D(this.x - other.x, this.y - other.y, this.z - other.z);
        }
    }

    public static class Vec4D {

        public double x;
        public double y;
        public double z;
        public double w;

        public Vec4D(double x, double y, double z, double w) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.w = w;
        }

        public double dot(Vec4D other) {
            return this.x * other.x + this.y * other.y + this.z * other.z + this.w * other.w;
        }

        public double distSquare() {
            return this.dot(this);
        }

        public double dist() {
            return Math.sqrt(this.distSquare());
        }

        public Vec4D mul(double other) {
            return new Vec4D(this.x * other, this.y * other, this.z * other, this.w * other);
        }
    }
}
