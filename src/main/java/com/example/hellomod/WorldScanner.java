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
     * Формат кожного рядка:
     *
     * cell dx=<int> dy=<int> dz=<int> kind=<KIND> block=<BLOCK_NAME>
     *
     * де:
     *  - dx, dz – зсув від гравця по X/Z (у блоках);
     *  - dy – відносна висота (surfaceY - playerY);
     *  - KIND – груба категорія блоку (WATER/SOLID/LEAVES/...);
     *  - BLOCK_NAME – просте ім'я блока (stone, grass_block, oak_log, ...).
     *
     * Рядки йдуть у порядку z від -radius до +radius,
     * а всередині кожного z — x від -radius до +radius.
     *
     * ВАЖЛИВО:
     *  - заголовок типу "# WORLD_SCAN ..." тут більше не додається,
     *    щоб AiRequestBuilder сам керував секціями.
     */
    public static String scanAround(ServerLevel level, ServerPlayer player,
                                    int radius, int up, int down) {

        BlockPos origin = player.blockPosition();
        int originY = origin.getY();

        StringBuilder sb = new StringBuilder();

        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {

                BlockPos surface = findSurface(level, origin, dx, dz, up, down);
                if (surface == null) {
                    // Якщо в колонці взагалі нічого немає (лише повітря) – пропускаємо.
                    continue;
                }

                BlockState state = level.getBlockState(surface);
                Block block = state.getBlock();
                String kind = classifyBlock(state);
                String name = blockName(block);

                int dy = surface.getY() - originY;

                // Формат, який легко читати ШІ: key=value, стабільний порядок полів.
                sb.append("cell ")
                        .append("dx=").append(dx).append(" ")
                        .append("dy=").append(dy).append(" ")
                        .append("dz=").append(dz).append(" ")
                        .append("kind=").append(kind).append(" ")
                        .append("block=").append(name)
                        .append("\n");
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
     * Наприклад, "minecraft:stone" -> "stone".
     */
    private static String blockName(Block block) {
        String raw = block.getRegistryName() != null
                ? block.getRegistryName().toString()
                : "unknown";

        int idx = raw.indexOf(':');
        if (idx >= 0 && idx < raw.length() - 1) {
            raw = raw.substring(idx + 1);
        }
        return raw.toLowerCase(Locale.ROOT);
    }

    /**
     * Допоміжна команда: відсканувати й написати в лог + коротке повідомлення гравцю.
     *
     * Тут ми додаємо свій заголовок, щоб у консолі було видно параметри скану.
     */
    public static void scanAndReport(ServerLevel level, ServerPlayer player) {
        int radius = 8;
        int up = 6;
        int down = 4;

        String snapshot = scanAround(level, player, radius, up, down);

        // Лог у консоль сервера / IDE — з коротким заголовком.
        System.out.println("# WORLD_SCAN radius=" + radius
                + " up=" + up
                + " down=" + down
                + " originY=" + player.blockPosition().getY());
        System.out.println(snapshot);

        int lines = snapshot.isEmpty() ? 0 : snapshot.split("\\r?\\n").length;
        player.sendMessage(
                new TextComponent("WORLD SCAN готово. Рядків: " + lines + " (дивись консоль сервера)."),
                player.getUUID()
        );
    }
}
