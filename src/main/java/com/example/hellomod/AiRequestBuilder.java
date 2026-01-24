package com.example.hellomod;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.StringJoiner;

/**
 * AiRequestBuilder – збирає всі потрібні дані для ШІ в одному текстовому блоці:
 *
 * === AI REQUEST START ===
 * ### WORLD_SCAN
 * cell ...
 *
 * ### USER_INTENT
 * ...
 *
 * ### CONSTRAINTS
 * ...
 * === AI REQUEST END ===
 */
public class AiRequestBuilder {

    // Налаштування скану світу (можеш міняти при бажанні)
    private static final int SCAN_RADIUS = 8;
    private static final int SCAN_UP = 6;
    private static final int SCAN_DOWN = 4;

    /**
     * Створює повний текст запиту для ШІ.
     *
     * @param level      світ
     * @param player     гравець (центр координат)
     * @param userPrompt вільний текст з команди /ai_build
     * @return готовий промпт для ШІ
     */
    public static String buildRequest(ServerLevel level, ServerPlayer player, String userPrompt) {
        StringBuilder sb = new StringBuilder();

        // 1) Заголовок
        sb.append("=== AI REQUEST START ===").append("\n");

        // 2) WORLD_SCAN
        sb.append("### WORLD_SCAN").append("\n");
        String worldScan = WorldScanner.scanAround(level, player, SCAN_RADIUS, SCAN_UP, SCAN_DOWN);
        if (worldScan != null && !worldScan.isEmpty()) {
            sb.append(worldScan);
            if (!worldScan.endsWith("\n")) {
                sb.append("\n");
            }
        }
        sb.append("\n");

        // 3) USER_INTENT
        sb.append("### USER_INTENT").append("\n");
        if (userPrompt != null && !userPrompt.isBlank()) {
            sb.append(userPrompt.trim()).append("\n");
        } else {
            sb.append("(empty)").append("\n");
        }
        sb.append("\n");

        // 4) CONSTRAINTS – беремо частину з AiPolicy
        sb.append("### CONSTRAINTS").append("\n");
        appendConstraints(sb);
        sb.append("\n");

        // 5) Кінець
        sb.append("=== AI REQUEST END ===").append("\n");

        return sb.toString();
    }

    private static void appendConstraints(StringBuilder sb) {
        // Тут ми не прив'язані 1:1 до політики, це скоріше інформація для ШІ.
        sb.append("max_radius=").append(20).append("\n"); // логічний радіус побудови
        sb.append("max_commands=").append(AiPolicy.MAX_ACTIONS).append("\n");

        // allowed_blocks з AiPolicy.ALLOWED_BLOCKS
        StringJoiner joiner = new StringJoiner(",");
        for (String block : AiPolicy.ALLOWED_BLOCKS) {
            joiner.add(block);
        }
        sb.append("allowed_blocks=").append(joiner.toString()).append("\n");

        sb.append("coordinate_system=relative_to_player").append("\n");
        sb.append("version=1.18.1").append("\n");
    }

    private AiRequestBuilder() {
        // utility class
    }
}
