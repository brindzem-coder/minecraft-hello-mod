package com.example.hellomod;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

import java.util.List;

public class AiExecutor {

    /**
     * Виконати ОДНУ дію AiAction.
     */
    public static void execute(ServerLevel level, net.minecraft.server.level.ServerPlayer player, AiAction action) {
        switch (action.type) {
            case BUILD_BOX -> buildBox(level, player, action.width, action.height, action.depth);
            case CLEAR_AROUND -> clearAround(level, player, action.radius);
            case PLACE_BLOCK -> placeBlock(level, player, action.block, action.offset);   // ← тут тепер є реалізація
            default -> player.sendMessage(
                    new net.minecraft.network.chat.TextComponent("Не зрозумів запит. Приклади: ai: build box 5 4 6 | ai: clear around 5"),
                    player.getUUID()
            );
        }
    }

    /**
     * Виконати СПИСОК дій (це ми будемо викликати з SimplePlanner: вежа, структури тощо).
     */
    public static void executeAll(ServerLevel level, net.minecraft.server.level.ServerPlayer player, List<AiAction> actions) {
        if (actions == null || actions.isEmpty()) {
            player.sendMessage(
                    new net.minecraft.network.chat.TextComponent("План порожній — немає дій для виконання."),
                    player.getUUID()
            );
            return;
        }

        for (AiAction action : actions) {
            execute(level, player, action);
        }

        player.sendMessage(
                new net.minecraft.network.chat.TextComponent("AI виконав план. Кроків: " + actions.size()),
                player.getUUID()
        );
    }

    /**
     * Виконання плану AiPlan/AiStep (твоя більш просунута система).
     * Її я не чіпав — залишаємо як є.
     */
    public static void executePlan(ServerLevel level, net.minecraft.server.level.ServerPlayer player, AiPlan plan) {
        if (plan == null || plan.steps.isEmpty()) {
            player.sendMessage(
                    new net.minecraft.network.chat.TextComponent("План порожній — нічого виконувати."),
                    player.getUUID()
            );
            return;
        }

        // “Origin” — точка, від якої відраховуємо всі dx/dy/dz (зазвичай позиція гравця)
        net.minecraft.core.BlockPos origin = player.blockPosition().offset(2, 0, 2);

        // Простий ліміт, щоб випадково не згенерувати монстра
        int maxSteps = 100;
        if (plan.steps.size() > maxSteps) {
            player.sendMessage(
                    new net.minecraft.network.chat.TextComponent("Забагато кроків у плані: " + plan.steps.size() + " (max " + maxSteps + ")"),
                    player.getUUID()
            );
            return;
        }

        for (AiStep step : plan.steps) {
            switch (step.type) {
                case BOX -> buildBoxStep(level, player, origin, step);
                case CLEAR_AROUND -> clearAroundStep(level, player, origin, step);
            }
        }

        player.sendMessage(
                new net.minecraft.network.chat.TextComponent("AI план виконано. Кроків: " + plan.steps.size()),
                player.getUUID()
        );
    }

    // ───────────────────────────
    // НИЖЧЕ ТВОЇ ВЖЕ ІСНУЮЧІ МЕТОДИ
    // ───────────────────────────

    private static void buildBox(ServerLevel level, net.minecraft.server.level.ServerPlayer player, int w, int h, int d) {
        if (!validSize(player, w, h, d)) return;

        BlockPos base = player.blockPosition().offset(2, 0, 2);

        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                for (int z = 0; z < d; z++) {
                    level.setBlock(base.offset(x, y, z), Blocks.STONE_BRICKS.defaultBlockState(), 3);
                }
            }
        }

        player.sendMessage(
                new net.minecraft.network.chat.TextComponent("AI виконав: BUILD_BOX " + w + "×" + h + "×" + d),
                player.getUUID()
        );
    }

    private static boolean validSize(net.minecraft.server.level.ServerPlayer player, int w, int h, int d) {
        if (w <= 0 || h <= 0 || d <= 0 || w > 50 || h > 50 || d > 50) {
            player.sendMessage(
                    new net.minecraft.network.chat.TextComponent("Розміри мають бути 1–50."),
                    player.getUUID()
            );
            return false;
        }
        return true;
    }

    private static void clearAround(ServerLevel level, net.minecraft.server.level.ServerPlayer player, int radius) {
        if (radius < 1 || radius > 15) {
            player.sendMessage(
                    new net.minecraft.network.chat.TextComponent("Радіус має бути 1–15."),
                    player.getUUID()
            );
            return;
        }

        BlockPos c = player.blockPosition();
        int y1 = c.getY();
        int y2 = c.getY() + 10;

        // Безпечно: чистимо тільки "листя/траву/квіти" поки що — не чіпаємо камінь/будівлі.
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                for (int y = 0; y <= (y2 - y1); y++) {
                    BlockPos p = c.offset(x, y, z);
                    var state = level.getBlockState(p);
                    if (state.is(Blocks.TALL_GRASS) || state.is(Blocks.GRASS) || state.is(Blocks.DANDELION) || state.is(Blocks.POPPY) || state.is(Blocks.OAK_LEAVES)) {
                        level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
        }

        player.sendMessage(
                new net.minecraft.network.chat.TextComponent("AI виконав: CLEAR_AROUND radius=" + radius),
                player.getUUID()
        );
    }

    private static void buildBoxStep(ServerLevel level, net.minecraft.server.level.ServerPlayer player,
                                     net.minecraft.core.BlockPos origin, AiStep step) {

        int w = step.width, h = step.height, d = step.depth;
        if (w <= 0 || h <= 0 || d <= 0 || w > 50 || h > 50 || d > 50) {
            player.sendMessage(
                    new net.minecraft.network.chat.TextComponent("BOX: розміри мають бути 1–50."),
                    player.getUUID()
            );
            return;
        }

        net.minecraft.core.BlockPos base = origin.offset(step.dx, step.dy, step.dz);

        // якщо hollow=true — будуємо тільки оболонку
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                for (int z = 0; z < d; z++) {

                    boolean isShell = (x == 0 || x == w - 1 || y == 0 || y == h - 1 || z == 0 || z == d - 1);
                    if (step.hollow && !isShell) continue;

                    level.setBlock(base.offset(x, y, z), net.minecraft.world.level.block.Blocks.STONE_BRICKS.defaultBlockState(), 3);
                }
            }
        }
    }

    private static void clearAroundStep(ServerLevel level, net.minecraft.server.level.ServerPlayer player,
                                        net.minecraft.core.BlockPos origin, AiStep step) {

        int radius = step.radius;
        if (radius < 1 || radius > 15) {
            player.sendMessage(
                    new net.minecraft.network.chat.TextComponent("CLEAR_AROUND: радіус 1–15."),
                    player.getUUID()
            );
            return;
        }

        net.minecraft.core.BlockPos center = origin.offset(step.dx, step.dy, step.dz);
        int y1 = center.getY();
        int y2 = center.getY() + 10;

        // Твій “безпечний” варіант: чистимо тільки деякі блоки рослинності
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                for (int y = y1; y <= y2; y++) {
                    net.minecraft.core.BlockPos p = new net.minecraft.core.BlockPos(center.getX() + x, y, center.getZ() + z);
                    var state = level.getBlockState(p);

                    if (state.is(net.minecraft.world.level.block.Blocks.TALL_GRASS)
                            || state.is(net.minecraft.world.level.block.Blocks.GRASS)
                            || state.is(net.minecraft.world.level.block.Blocks.DANDELION)
                            || state.is(net.minecraft.world.level.block.Blocks.POPPY)
                            || state.is(net.minecraft.world.level.block.Blocks.OAK_LEAVES)) {
                        level.setBlock(p, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
        }
    }

    /**
     * НОВИЙ МЕТОД: поставити один блок відносно позиції гравця.
     */
    private static void placeBlock(ServerLevel level,
                                   net.minecraft.server.level.ServerPlayer player,
                                   net.minecraft.world.level.block.Block block,
                                   BlockPos offset) {

        BlockPos base = player.blockPosition();
        BlockPos target = base.offset(offset.getX(), offset.getY(), offset.getZ());

        level.setBlock(target, block.defaultBlockState(), 3);
    }
}
