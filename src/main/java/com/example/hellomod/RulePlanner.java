package com.example.hellomod;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

public class RulePlanner {

    public static String plan(ServerLevel level, ServerPlayer player, String thing) {
        return switch (thing) {
            case "platform" -> planPlatform(level, player);
            case "bridge" -> planBridge(level, player);
            default -> """
                # Unknown build type
                # Try: /ai build platform
                # Try: /ai build bridge
                """;
        };
    }

    // 1) Платформа 7x7 під ногами (вирівнює та робить настил)
    private static String planPlatform(ServerLevel level, ServerPlayer player) {
        int size = 7;
        int half = size / 2;

        StringBuilder sb = new StringBuilder();
        sb.append("# PLATFORM ").append(size).append("x").append(size).append("\n");

        // Очистимо рослинність навколо, щоб було видно
        sb.append("CLEAR_AROUND 6\n");

        // Ставимо дошки на рівні ніг гравця (y)
        // (простий варіант: будуємо на поточному Y гравця)
        for (int dx = -half; dx <= half; dx++) {
            for (int dz = -half; dz <= half; dz++) {
                sb.append("PLACE_BLOCK oak_planks ").append(dx).append(" 0 ").append(dz).append("\n");
            }
        }
        return sb.toString();
    }

    // 2) Міст: шукаємо воду в напрямку погляду гравця і кладемо настил через неї
    private static String planBridge(ServerLevel level, ServerPlayer player) {
        BlockPos origin = player.blockPosition();

        // 1) Визначаємо напрямок "вперед" за поглядом гравця (лише по X/Z, до найближчої з 4 сторін)
        Vec3 look = player.getLookAngle();
        int fx, fz; // forward (куди будемо будувати міст)

        if (Math.abs(look.x) > Math.abs(look.z)) {
            // Сильніше дивиться по X
            fx = look.x > 0 ? 1 : -1;
            fz = 0;
        } else {
            // Сильніше дивиться по Z
            fz = look.z > 0 ? 1 : -1;
            fx = 0;
        }

        // "Вбік" від напрямку руху (для ширини мосту, поручнів тощо)
        // Поворот на 90°: якщо forward = (fx, fz), то side = (-fz, fx)
        int sx = -fz;
        int sz = fx;

        int maxForward = 25;   // наскільки далеко дивимось вперед
        int waterStart = -1;
        int waterEnd = -1;

        // 2) Шукаємо перший блок води в напрямку погляду (трохи нижче за ноги гравця)
        for (int f = 1; f <= maxForward; f++) {
            BlockPos p = origin.offset(fx * f, -1, fz * f);
            if (level.getBlockState(p).is(Blocks.WATER)) {
                waterStart = f;
                break;
            }
        }

        if (waterStart == -1) {
            return """
                # BRIDGE: не знайшов воду попереду в напрямку погляду
                """;
        }

        // 3) Знаходимо, де вода закінчується
        for (int f = waterStart; f <= maxForward; f++) {
            BlockPos p = origin.offset(fx * f, -1, fz * f);
            if (!level.getBlockState(p).is(Blocks.WATER)) {
                waterEnd = f - 1;
                break;
            }
        }
        if (waterEnd == -1) waterEnd = maxForward;

        // Будуємо міст через воду + трохи на береги
        int start = Math.max(1, waterStart - 1);
        int end = waterEnd + 1;

        StringBuilder sb = new StringBuilder();
        sb.append("# BRIDGE from ").append(start).append(" to ").append(end)
                .append(" forward=(").append(fx).append(",").append(fz).append(")")
                .append(" side=(").append(sx).append(",").append(sz).append(")\n");

        // 4) Настил шириною 3 (side = -1..1)
        for (int f = start; f <= end; f++) {
            for (int side = -1; side <= 1; side++) {
                int dx = fx * f + sx * side;
                int dz = fz * f + sz * side;
                sb.append("PLACE_BLOCK oak_planks ")
                        .append(dx).append(" ")
                        .append(0).append(" ")
                        .append(dz).append("\n");
            }
        }

        // 5) Поручні – огорожа по краях (side = -2 і +2, на висоті 1)
        for (int f = start; f <= end; f++) {
            int dxLeft = fx * f + sx * -2;
            int dzLeft = fz * f + sz * -2;
            int dxRight = fx * f + sx * 2;
            int dzRight = fz * f + sz * 2;

            sb.append("PLACE_BLOCK oak_fence ")
                    .append(dxLeft).append(" ")
                    .append(1).append(" ")
                    .append(dzLeft).append("\n");

            sb.append("PLACE_BLOCK oak_fence ")
                    .append(dxRight).append(" ")
                    .append(1).append(" ")
                    .append(dzRight).append("\n");
        }

        // 6) Факели на початку та кінці мосту (на поручнях)
        {
            int f = start;
            int dxLeft = fx * f + sx * -2;
            int dzLeft = fz * f + sz * -2;
            int dxRight = fx * f + sx * 2;
            int dzRight = fz * f + sz * 2;

            sb.append("PLACE_BLOCK torch ")
                    .append(dxLeft).append(" ")
                    .append(2).append(" ")
                    .append(dzLeft).append("\n");

            sb.append("PLACE_BLOCK torch ")
                    .append(dxRight).append(" ")
                    .append(2).append(" ")
                    .append(dzRight).append("\n");
        }

        {
            int f = end;
            int dxLeft = fx * f + sx * -2;
            int dzLeft = fz * f + sz * -2;
            int dxRight = fx * f + sx * 2;
            int dzRight = fz * f + sz * 2;

            sb.append("PLACE_BLOCK torch ")
                    .append(dxLeft).append(" ")
                    .append(2).append(" ")
                    .append(dzLeft).append("\n");

            sb.append("PLACE_BLOCK torch ")
                    .append(dxRight).append(" ")
                    .append(2).append(" ")
                    .append(dzRight).append("\n");
        }

        return sb.toString();
    }
}
