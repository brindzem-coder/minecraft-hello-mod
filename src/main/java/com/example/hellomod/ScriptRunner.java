package com.example.hellomod;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.*;

/**
 * ScriptRunner – перетворює текстовий скрипт на список AiAction і виконує його.
 *
 * Підготовлено під LLM:
 *  - вміє приймати один великий текст (runFromMultiline),
 *  - парсер терпить порожні рядки, коментарі, різні регістри,
 *  - є прості обмеження безпеки.
 */
public class ScriptRunner {

    // Ліміти безпеки – потім легко змінити під конфіг
    private static final int MAX_ACTIONS = 1000;  // максимум дій у скрипті
    private static final int MAX_RADIUS = 30;     // максимум радіус для CLEAR_AROUND
    private static final int MAX_BOX_SIZE = 64;   // максимум розмірів для коробки

    /**
     * Варіант для LLM: приймає один великий текст скрипта з \n-рядками.
     * Наприклад:
     *
     * CLEAR_AROUND 5
     * PLACE_BLOCK stone 0 0 3
     * PLACE_BLOCK stone 1 0 3
     */
    public static void runFromMultiline(ServerLevel level, ServerPlayer player, String scriptText) {
        // Нормалізуємо переводи рядків і ділимо на рядки
        String[] rawLines = scriptText.split("\\r?\\n");
        List<String> lines = new ArrayList<>();
        for (String line : rawLines) {
            lines.add(line);
        }
        run(level, player, lines);
    }

    /**
     * Головний метод запуску скрипта з уже готового списку рядків.
     */
    public static void run(ServerLevel level, ServerPlayer player, List<String> lines) {

        List<AiAction> actions = new ArrayList<>();

        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty()) continue;          // пропускаємо пусті
            if (line.startsWith("#")) continue;    // # коментар
            if (line.startsWith("//")) continue;   // // коментар

            // Дозволимо ставити ; в кінці рядка (LLM іноді любить таке)
            if (line.endsWith(";")) {
                line = line.substring(0, line.length() - 1).trim();
                if (line.isEmpty()) continue;
            }

            String[] parts = line.split("\\s+");
            String cmd = parts[0].toUpperCase(Locale.ROOT);

            try {
                switch (cmd) {
                    case "PLACE_BLOCK" -> parsePlaceBlock(parts, line, actions, player);
                    case "CLEAR_AROUND" -> parseClearAround(parts, line, actions, player);
                    case "BUILD_BOX" -> parseBuildBox(parts, line, actions, player);
                    default -> player.sendMessage(
                            new TextComponent("Невідома команда у скрипті: '" + cmd + "'. Рядок: " + line),
                            player.getUUID()
                    );
                }
            } catch (NumberFormatException e) {
                player.sendMessage(
                        new TextComponent("Помилка числа в рядку скрипта: " + line),
                        player.getUUID()
                );
            }
        }

        if (actions.isEmpty()) {
            player.sendMessage(
                    new TextComponent("Скрипт не містить жодної валідної команди."),
                    player.getUUID()
            );
            return;
        }

        // Ліміт на кількість екшнів
        if (actions.size() > MAX_ACTIONS) {
            player.sendMessage(
                    new TextComponent("Забагато команд у скрипті: " + actions.size() +
                            " (max " + MAX_ACTIONS + "). Обрізаю."),
                    player.getUUID()
            );
            actions = actions.subList(0, MAX_ACTIONS);
        }

        AiExecutor.executeAll(level, player, actions);
    }

    // ─────────────────────────────────────
    // Парсери окремих команд
    // ─────────────────────────────────────

    private static void parsePlaceBlock(String[] parts,
                                        String line,
                                        List<AiAction> actions,
                                        ServerPlayer player) {

        // Формат: PLACE_BLOCK blockName dx dy dz
        if (parts.length != 5) {
            player.sendMessage(
                    new TextComponent("PLACE_BLOCK формат: PLACE_BLOCK block dx dy dz. Пропускаю: " + line),
                    player.getUUID()
            );
            return;
        }

        String blockName = parts[1].toLowerCase(Locale.ROOT);
        Block block = parseBlock(blockName);
        if (block == null) {
            player.sendMessage(
                    new TextComponent("Невідомий блок '" + blockName + "' у рядку: " + line),
                    player.getUUID()
            );
            return;
        }

        int dx = Integer.parseInt(parts[2]);
        int dy = Integer.parseInt(parts[3]);
        int dz = Integer.parseInt(parts[4]);

        actions.add(AiAction.placeBlock(block, new BlockPos(dx, dy, dz)));
    }

    private static void parseClearAround(String[] parts,
                                         String line,
                                         List<AiAction> actions,
                                         ServerPlayer player) {

        // Формат: CLEAR_AROUND radius
        if (parts.length != 2) {
            player.sendMessage(
                    new TextComponent("CLEAR_AROUND формат: CLEAR_AROUND radius. Пропускаю: " + line),
                    player.getUUID()
            );
            return;
        }

        int radius = Integer.parseInt(parts[1]);
        if (radius < 1 || radius > MAX_RADIUS) {
            player.sendMessage(
                    new TextComponent("CLEAR_AROUND: радіус має бути 1–" + MAX_RADIUS +
                            ". Пропускаю: " + line),
                    player.getUUID()
            );
            return;
        }

        actions.add(AiAction.clearAround(radius));
    }

    private static void parseBuildBox(String[] parts,
                                      String line,
                                      List<AiAction> actions,
                                      ServerPlayer player) {

        // Формат: BUILD_BOX w h d
        if (parts.length != 4) {
            player.sendMessage(
                    new TextComponent("BUILD_BOX формат: BUILD_BOX width height depth. Пропускаю: " + line),
                    player.getUUID()
            );
            return;
        }

        int w = Integer.parseInt(parts[1]);
        int h = Integer.parseInt(parts[2]);
        int d = Integer.parseInt(parts[3]);

        if (w < 1 || h < 1 || d < 1 ||
                w > MAX_BOX_SIZE || h > MAX_BOX_SIZE || d > MAX_BOX_SIZE) {

            player.sendMessage(
                    new TextComponent("BUILD_BOX: розміри мають бути 1–" + MAX_BOX_SIZE +
                            ". Пропускаю: " + line),
                    player.getUUID()
            );
            return;
        }

        actions.add(AiAction.buildBox(w, h, d));
    }

    // ─────────────────────────────────────
    // Мапа блоків – тут можна додавати нові
    // ─────────────────────────────────────

    /**
     * Перетворює текстове ім'я блоку (stone, oak_planks, ...) на Block.
     */
    private static Block parseBlock(String name) {
        return switch (name) {
            case "stone" -> Blocks.STONE;
            case "stone_bricks", "stonebrick", "stone_brick" -> Blocks.STONE_BRICKS;
            case "oak_planks", "planks_oak", "oakplanks" -> Blocks.OAK_PLANKS;
            case "oak_fence", "fence_oak" -> Blocks.OAK_FENCE;
            case "torch", "torch_wall" -> Blocks.TORCH;
            // сюди легко додати ще:
            // case "glass" -> Blocks.GLASS;
            // case "dirt" -> Blocks.DIRT;
            default -> null;
        };
    }
}
