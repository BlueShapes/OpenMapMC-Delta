package com.mojang.math;

public final class Axis {
    public static final Axis ZP = new Axis();
    private Axis() {}
    public Rotation rotationDegrees(float degrees) { return new Rotation(Math.toRadians(degrees)); }
    public static final class Rotation {
        public final double radians;
        private Rotation(double radians) { this.radians = radians; }
    }
}
