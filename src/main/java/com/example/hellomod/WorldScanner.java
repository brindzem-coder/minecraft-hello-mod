package com.example.hellomod;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Locale;

public class WorldScanner {

    /**
     * Знімає "знімок" світу навколо гравця в текстовому форматі.
     *
     * Формат рядків:
     * CELL dx dy dz KIND BLOCK_NAME
     *
     * де:
     *  - dx, dz – зсув від гравця по X/Z
     *  - dy – відносна висота (surfaceY - playerY)
     *  - KIND – категорія (WATER/SOLID/LEAVES/...)
     *  - BLOCK_NAME – ім'я блока (спрощене)
     */
    public static String scanAround(ServerLevel level, ServerPlayer player,
                                    int radius, int up, int down) {

        BlockPos origin = player.blockPosition();
        int originY = origin.getY();

        StringBuilder sb = new StringBuilder();

        sb.append("# WORLD_SCAN radius=").append(radius)
                .append(" up=").append(up)
                .append(" down=").append(down)
                .append(" originY=").append(originY).append("\n");

        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {

                BlockPos surface = findSurface(level, origin, dx, dz, up, down);
                if (surface == null) {
                    continue; // в цій колонці нічого цікавого не знайшли
                }

                BlockState state = level.getBlockState(surface);
                Block block = state.getBlock();
                String kind = classifyBlock(state);
                String name = blockName(block);

                int dy = surface.getY() - originY;

                sb.append("CELL ")
                        .append(dx).append(" ")
                        .append(dy).append(" ")
                        .append(dz).append(" ")
                        .append(kind).append(" ")
                        .append(name).append("\n");
            }
        }

        return sb.toString();
    }

    /**
     * Шукає "поверхню" в колонці (dx, dz): перший не-повітряний блок при скануванні згори вниз.
     */
    private static BlockPos findSurface(ServerLevel level, BlockPos origin,
                                        int dx, int dz, int up, int down) {

        int startY = origin.getY() + up;
        int endY = origin.getY() - down;

        for (int y = startY; y >= endY; y--) {
            BlockPos p = new BlockPos(origin.getX() + dx, y, origin.getZ() + dz);
            BlockState state = level.getBlockState(p);
            if (!state.isAir()) {
                return p;
            }
        }
        return null;
    }

    /**
     * Груба класифікація блоків у категорії, які буде легко зрозуміти ШІ.
     */
    private static String classifyBlock(BlockState state) {
        Block b = state.getBlock();

        if (state.is(Blocks.WATER)) return "WATER";
        if (state.is(Blocks.LAVA)) return "LAVA";

        if (state.is(Blocks.OAK_LEAVES) || state.is(Blocks.SPRUCE_LEAVES)
                || state.is(Blocks.BIRCH_LEAVES) || state.is(Blocks.JUNGLE_LEAVES)
                || state.is(Blocks.ACACIA_LEAVES) || state.is(Blocks.DARK_OAK_LEAVES)) {
            return "LEAVES";
        }

        if (state.is(Blocks.OAK_LOG) || state.is(Blocks.SPRUCE_LOG)
                || state.is(Blocks.BIRCH_LOG) || state.is(Blocks.JUNGLE_LOG)
                || state.is(Blocks.ACACIA_LOG) || state.is(Blocks.DARK_OAK_LOG)) {
            return "LOG";
        }

        if (state.is(Blocks.SAND) || state.is(Blocks.RED_SAND)) return "SAND";
        if (state.is(Blocks.DIRT) || state.is(Blocks.COARSE_DIRT) || state.is(Blocks.GRASS_BLOCK)) return "DIRT";

        if (state.is(Blocks.STONE) || state.is(Blocks.COBBLESTONE)
                || state.is(Blocks.STONE_BRICKS)) {
            return "SOLID";
        }

        if (state.is(Blocks.OAK_PLANKS) || state.is(Blocks.SPRUCE_PLANKS)
                || state.is(Blocks.BIRCH_PLANKS) || state.is(Blocks.JUNGLE_PLANKS)
                || state.is(Blocks.ACACIA_PLANKS) || state.is(Blocks.DARK_OAK_PLANKS)) {
            return "PLANKS";
        }

        // Можна додавати ще, але для початку достатньо
        return "OTHER";
    }

    /**
     * Спрощене ім'я блоку (без namespace, у нижньому регістрі).
     */
    private static String blockName(Block block) {
        String raw = block.getRegistryName() != null
                ? block.getRegistryName().toString()
                : "unknown";

        // наприклад "minecraft:stone" -> "stone"
        int idx = raw.indexOf(':');
        if (idx >= 0 && idx < raw.length() - 1) {
            raw = raw.substring(idx + 1);
        }
        return raw.toLowerCase(Locale.ROOT);
    }

    /**
     * Допоміжна команда: відсканувати й написати в лог + коротке повідомлення гравцю.
     */
    public static void scanAndReport(ServerLevel level, ServerPlayer player) {
        String snapshot = scanAround(level, player, 8, 6, 4); // радіус 8, вгору 6, вниз 4

        // Лог у консоль сервера / IDE
        System.out.println(snapshot);

        int lines = snapshot.split("\\r?\\n").length;
        player.sendMessage(
                new TextComponent("WORLD SCAN готово. Рядків: " + lines + " (дивись консоль сервера)."),
                player.getUUID()
        );
    }
}
