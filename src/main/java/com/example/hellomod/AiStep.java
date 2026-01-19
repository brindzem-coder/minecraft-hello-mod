package com.example.hellomod;

public class AiStep {

    public enum Type {
        BOX,
        CLEAR_AROUND
    }

    public final Type type;

    // Зміщення від точки "origin" (зазвичай від гравця)
    public final int dx;
    public final int dy;
    public final int dz;

    // Параметри для BOX
    public final int width;
    public final int height;
    public final int depth;
    public final boolean hollow;

    // Параметри для CLEAR_AROUND
    public final int radius;

    private AiStep(Type type, int dx, int dy, int dz,
                   int width, int height, int depth, boolean hollow,
                   int radius) {
        this.type = type;
        this.dx = dx;
        this.dy = dy;
        this.dz = dz;
        this.width = width;
        this.height = height;
        this.depth = depth;
        this.hollow = hollow;
        this.radius = radius;
    }

    public static AiStep box(int dx, int dy, int dz, int w, int h, int d, boolean hollow) {
        return new AiStep(Type.BOX, dx, dy, dz, w, h, d, hollow, 0);
    }

    public static AiStep clearAround(int dx, int dy, int dz, int radius) {
        return new AiStep(Type.CLEAR_AROUND, dx, dy, dz, 0, 0, 0, false, radius);
    }
}
