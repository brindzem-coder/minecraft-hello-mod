package com.example.hellomod;

import java.util.Set;

public class AiPolicy {

    // Загальні ліміти
    public static final int MAX_ACTIONS = 1000;
    public static final int MAX_PLACE_BLOCKS = 600;

    // Дистанція від гравця (відносні dx/dy/dz)
    public static final int MAX_ABS_DX = 40;
    public static final int MAX_ABS_DY = 20;
    public static final int MAX_ABS_DZ = 40;

    // CLEAR_AROUND
    public static final int MAX_CLEAR_RADIUS = 30;

    // BUILD_BOX
    public static final int MAX_BOX_SIZE = 64;

    // Дозволені блоки для PLACE_BLOCK (для безпеки)
    public static final Set<String> ALLOWED_BLOCKS = Set.of(
            "stone",
            "stone_bricks",
            "oak_planks",
            "oak_fence",
            "torch"
    );

    private AiPolicy() {}
}
