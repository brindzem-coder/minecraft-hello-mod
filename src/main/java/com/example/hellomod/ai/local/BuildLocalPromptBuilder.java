package com.example.hellomod.ai.local;

import com.example.hellomod.AiPolicy;

import java.util.StringJoiner;

/**
 * Формує промпт для build_local так, щоб LLM генерував саме наш DSL
 * і без “пояснювального” тексту.
 *
 * Рекомендований контракт відповіді: DSL між маркерами BEGIN_DSL / END_DSL.
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

        StringBuilder sb = new StringBuilder();

        sb.append("You are a Minecraft building script generator.\n");
        sb.append("Goal: produce a short DSL script that builds what the player asked.\n");
        sb.append("\n");

        sb.append("PLAYER_REQUEST:\n");
        sb.append(safeThing.isEmpty() ? "(empty)\n" : safeThing + "\n");
        sb.append("\n");

        if (!safeWorld.isEmpty()) {
            sb.append("WORLD_CONTEXT (optional):\n");
            sb.append(safeWorld).append("\n");
            sb.append("\n");
        }

        sb.append("OUTPUT_FORMAT (STRICT):\n");
        sb.append("- Return ONLY DSL lines.\n");
        sb.append("- Put DSL strictly between markers:\n");
        sb.append("  BEGIN_DSL\n");
        sb.append("  ...DSL lines...\n");
        sb.append("  END_DSL\n");
        sb.append("- No explanations, no markdown, no code fences, no bullets.\n");
        sb.append("- One command per line.\n");
        sb.append("\n");

        sb.append("DSL_COMMANDS (ALLOWED):\n");
        sb.append("1) CLEAR_AROUND radius\n");
        sb.append("   - Clears only vegetation around player (safe clear).\n");
        sb.append("   - radius must be 1..15\n");
        sb.append("2) BUILD_BOX width height depth\n");
        sb.append("   - Builds a solid stone_bricks box near player.\n");
        sb.append("   - each size must be 1..50\n");
        sb.append("3) PLACE_BLOCK block dx dy dz\n");
        sb.append("   - Places a single block at player_position + (dx,dy,dz).\n");
        sb.append("   - block must be in allowed list.\n");
        sb.append("\n");

        sb.append("ALLOWED_BLOCKS for PLACE_BLOCK:\n");
        sb.append(joinAllowedBlocks()).append("\n");
        sb.append("\n");

        sb.append("SAFETY_CONSTRAINTS (MUST FOLLOW):\n");
        sb.append("- Max total commands: ").append(AiPolicy.MAX_ACTIONS).append("\n");
        sb.append("- Max PLACE_BLOCK commands: ").append(AiPolicy.MAX_PLACE_BLOCKS).append("\n");
        sb.append("- Coordinate limits: |dx|<= ").append(AiPolicy.MAX_ABS_DX)
                .append(", |dy|<= ").append(AiPolicy.MAX_ABS_DY)
                .append(", |dz|<= ").append(AiPolicy.MAX_ABS_DZ).append("\n");
        sb.append("- Do NOT use any other commands.\n");
        sb.append("- If the request is too big, output a smaller minimal version.\n");
        sb.append("\n");

        sb.append("EXAMPLES (STYLE ONLY):\n");
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
