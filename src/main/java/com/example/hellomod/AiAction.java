package com.example.hellomod;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;

public class AiAction {

    public enum Type {
        BUILD_BOX,
        CLEAR_AROUND,
        PLACE_BLOCK,
        UNKNOWN
    }

    public final Type type;

    // BUILD_BOX
    public final int width;
    public final int height;
    public final int depth;

    // CLEAR_AROUND
    public final int radius;

    // PLACE_BLOCK
    public final Block block;
    public final BlockPos offset;

    // Конструктор для BUILD_BOX / CLEAR_AROUND / UNKNOWN
    private AiAction(Type type, int width, int height, int depth, int radius) {
        this.type = type;
        this.width = width;
        this.height = height;
        this.depth = depth;
        this.radius = radius;

        this.block = null;
        this.offset = null;
    }

    // Конструктор для PLACE_BLOCK
    private AiAction(Type type, Block block, BlockPos offset) {
        this.type = type;
        this.block = block;
        this.offset = offset;

        this.width = 0;
        this.height = 0;
        this.depth = 0;
        this.radius = 0;
    }

    public static AiAction buildBox(int w, int h, int d) {
        return new AiAction(Type.BUILD_BOX, w, h, d, 0);
    }

    public static AiAction clearAround(int radius) {
        return new AiAction(Type.CLEAR_AROUND, 0, 0, 0, radius);
    }

    public static AiAction placeBlock(Block block, BlockPos offset) {
        return new AiAction(Type.PLACE_BLOCK, block, offset);
    }

    public static AiAction unknown() {
        return new AiAction(Type.UNKNOWN, 0, 0, 0, 0);
    }
}
