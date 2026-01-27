package com.example.hellomod.ai.local;

import com.example.hellomod.AiPolicy;

import java.util.StringJoiner;

/**
 * Формує промпт для build_local так, щоб LLM генерував саме наш DSL
 * і без пояснювального тексту.
 *
 * Контракт відповіді: DSL строго між маркерами BEGIN_DSL / END_DSL.
 */
public final class BuildLocalPromptBuilder {

    private BuildLocalPromptBuilder() {}

    /**
     * @param thing        вільний текст від гравця (що будувати)
     * @param worldContext опційний контекст світу (може бути null/blank)
     */
    public static String buildPrompt(String thing, String worldContext) {
        String safeThing = (thing == null) ? "" : thing.trim();
        String safeWorld = (worldContext == null) ? "" : worldContext.trim();

        // Малі ліміти у промпті => модель відповідає швидше і стабільніше.
        // (Фактична безпека все одно забезпечується валідатором.)
        final int PROMPT_MAX_TOTAL_CMDS = 80;
        final int PROMPT_MAX_PLACE_BLOCK = 60;

        StringBuilder sb = new StringBuilder();

        sb.append("You are a Minecraft building SCRIPT generator.\n");
        sb.append("Task: generate a SHORT script in the DSL below.\n");
        sb.append("Return ONLY the DSL between markers. No extra text.\n");
        sb.append("\n");

        sb.append("PLAYER_REQUEST:\n");
        sb.append(safeThing.isEmpty() ? "(empty)\n" : safeThing + "\n");
        sb.append("\n");

        if (!safeWorld.isEmpty()) {
            sb.append("WORLD_CONTEXT:\n");
            sb.append(safeWorld).append("\n\n");
        }

        sb.append("OUTPUT_FORMAT (STRICT):\n");
        sb.append("BEGIN_DSL\n");
        sb.append("<one DSL command per line>\n");
        sb.append("END_DSL\n");
        sb.append("No markdown. No explanations. No bullets.\n");
        sb.append("\n");

        sb.append("DSL_COMMANDS (ONLY THESE ARE ALLOWED):\n");
        sb.append("1) CLEAR_AROUND radius\n");
        sb.append("   - Clears only vegetation around the player.\n");
        sb.append("   - radius: 1..15\n");
        sb.append("2) BUILD_BOX width height depth\n");
        sb.append("   - Builds a solid stone_bricks box near the player.\n");
        sb.append("   - each size: 1..50\n");
        sb.append("3) PLACE_BLOCK block dx dy dz\n");
        sb.append("   - Places one block at player_position + (dx,dy,dz).\n");
        sb.append("   - block must be from ALLOWED_BLOCKS.\n");
        sb.append("\n");

        sb.append("ALLOWED_BLOCKS:\n");
        sb.append(joinAllowedBlocks()).append("\n\n");

        sb.append("SAFETY_RULES (MUST FOLLOW):\n");
        sb.append("- Use ONLY the 3 commands listed above.\n");
        sb.append("- Keep it small and fast:\n");
        sb.append("  - Max total commands: ").append(PROMPT_MAX_TOTAL_CMDS).append("\n");
        sb.append("  - Max PLACE_BLOCK commands: ").append(PROMPT_MAX_PLACE_BLOCK).append("\n");
        sb.append("- Coordinate limits for PLACE_BLOCK:\n");
        sb.append("  |dx| <= ").append(AiPolicy.MAX_ABS_DX)
                .append(", |dy| <= ").append(AiPolicy.MAX_ABS_DY)
                .append(", |dz| <= ").append(AiPolicy.MAX_ABS_DZ).append("\n");
        sb.append("- If the player request is too large, output a minimal small version.\n");
        sb.append("\n");

        sb.append("EXAMPLE (FORMAT ONLY):\n");
        sb.append("BEGIN_DSL\n");
        sb.append("CLEAR_AROUND 6\n");
        sb.append("BUILD_BOX 5 4 6\n");
        sb.append("PLACE_BLOCK oak_planks 2 0 0\n");
        sb.append("PLACE_BLOCK oak_fence 2 1 0\n");
        sb.append("PLACE_BLOCK torch 1 2 1\n");
        sb.append("END_DSL\n");

        return sb.toString();
    }

    private static String joinAllowedBlocks() {
        StringJoiner j = new StringJoiner(", ");
        for (String b : AiPolicy.ALLOWED_BLOCKS) {
            j.add(b);
        }
        return j.toString();
    }
}
