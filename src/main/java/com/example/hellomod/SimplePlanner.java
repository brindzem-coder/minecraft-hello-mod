package com.example.hellomod;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;

public class SimplePlanner {

    /**
     * План: очистити навколо гравця і побудувати маленьку 3x3 вежу перед ним.
     */
    public static List<AiAction> createTowerPlan(ServerPlayer player) {
        List<AiAction> actions = new ArrayList<>();

        // 1) Очистити площу навколо гравця (це твій існуючий CLEAR_AROUND)
        actions.add(AiAction.clearAround(5));

        // 2) Побудувати маленьку 3x3 вежу висотою 5 блоків попереду гравця
        int baseForward = 4; // на скільки блоків вперед від гравця
        int halfSize = 1;    // 3x3 (від -1 до +1)
        int height = 5;

        for (int y = 0; y < height; y++) {
            for (int dx = -halfSize; dx <= halfSize; dx++) {
                for (int dz = -halfSize; dz <= halfSize; dz++) {

                    // Робимо тільки стінки (рамку), щоб була "вежа", а не суцільний куб
                    boolean isEdge = Math.abs(dx) == halfSize || Math.abs(dz) == halfSize;
                    if (!isEdge) continue;

                    BlockPos offset = new BlockPos(dx, y, dz + baseForward);
                    actions.add(AiAction.placeBlock(Blocks.STONE, offset));
                }
            }
        }

        // 3) Факел зверху
        BlockPos torchOffset = new BlockPos(0, height, baseForward);
        actions.add(AiAction.placeBlock(Blocks.TORCH, torchOffset));

        return actions;
    }
}
