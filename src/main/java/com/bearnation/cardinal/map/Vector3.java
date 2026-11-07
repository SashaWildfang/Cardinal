package com.bearnation.cardinal.map;

public class Vector3 {
    private final String world;
    private final double x, y, z;

    public Vector3(String world, double x, double y, double z) {
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public String getWorld() {
        return world;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    @Override
    public String toString() {
        return world + " (" + x + ", " + y + ", " + z + ")";
    }
}
