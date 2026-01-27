package com.example.hellomod.ai.local;

import com.example.hellomod.AiPolicy;

import java.util.Locale;
import java.util.StringJoiner;

/**
 * Prompt builder for /ai build_local.
 *
 * Goal: Make the model output ONLY our DSL, reliably, and obey the user's requested block
 * when it is in the allowed list.
 *
 * Contract: DSL strictly between BEGIN_DSL and END_DSL.
 */
public final class BuildLocalPromptBuilder {

    private BuildLocalPromptBuilder() {}

    public static String buildPrompt(String thing, String worldContext) {
        String safeThing = thing == null ? "" : thing.trim();
        String safeWorld = worldContext == null ? "" : worldContext.trim();

        // Keep prompt small to reduce latency.
        final int PROMPT_MAX_TOTAL_CMDS = 40;
        final int PROMPT_MAX_PLACE_BLOCK = 25;

        // Detect if the user clearly asked for a specific allowed block (best effort).
        // This is only used to bias the prompt, not to enforce in code.
        String requestedBlock = tryDetectRequestedAllowedBlock(safeThing);

        StringBuilder sb = new StringBuilder();
        sb.append("You are a Minecraft building SCRIPT generator.\n");
        sb.append("Generate a SHORT script in the DSL below.\n");
        sb.append("Return ONLY DSL between markers. No extra text.\n\n");

        sb.append("PLAYER_REQUEST:\n");
        sb.append(safeThing.isEmpty() ? "(empty)\n" : safeThing).append("\n\n");

        if (!safeWorld.isEmpty()) {
            sb.append("WORLD_CONTEXT:\n").append(safeWorld).append("\n\n");
        }

        sb.append("OUTPUT_FORMAT (STRICT):\n");
        sb.append("BEGIN_DSL\n");
        sb.append("<one DSL command per line>\n");
        sb.append("END_DSL\n");
        sb.append("No markdown. No explanations.\n\n");

        sb.append("DSL_COMMANDS (ONLY THESE ARE ALLOWED):\n");
        sb.append("1) CLEAR_AROUND radius\n");
        sb.append("2) BUILD_BOX width height depth\n");
        sb.append("3) PLACE_BLOCK block dx dy dz\n\n");

        sb.append("ALLOWED_BLOCKS (ONLY these block ids are allowed in PLACE_BLOCK):\n");
        sb.append(joinAllowedBlocks()).append("\n\n");

        sb.append("CRITICAL RULES:\n");
        sb.append("- Use ONLY the allowed DSL commands.\n");
        sb.append("- If the user requests a specific block that is in ALLOWED_BLOCKS, you MUST use that block.\n");
        sb.append("- If the requested block is NOT in ALLOWED_BLOCKS, choose a close substitute FROM ALLOWED_BLOCKS.\n");
        sb.append("- Prefer PLACE_BLOCK for single-block requests (like 'place a torch above me').\n");
        sb.append("- Do NOT output 'OK' or any words outside DSL.\n");
        sb.append("- Keep it short:\n");
        sb.append("  - Max total commands: ").append(PROMPT_MAX_TOTAL_CMDS).append("\n");
        sb.append("  - Max PLACE_BLOCK commands: ").append(PROMPT_MAX_PLACE_BLOCK).append("\n");
        sb.append("- Coordinate limits for PLACE_BLOCK:\n");
        sb.append("  |dx| <= ").append(AiPolicy.MAX_ABS_DX)
                .append(", |dy| <= ").append(AiPolicy.MAX_ABS_DY)
                .append(", |dz| <= ").append(AiPolicy.MAX_ABS_DZ).append("\n\n");

        if (requestedBlock != null) {
            sb.append("REQUESTED_BLOCK_HINT:\n");
            sb.append("- The user asked for block: ").append(requestedBlock).append("\n");
            sb.append("- Use it exactly in PLACE_BLOCK.\n\n");
        }

        sb.append("EXAMPLES (FORMAT + BEHAVIOR):\n");
        sb.append("Example A (torch above player):\n");
        sb.append("BEGIN_DSL\n");
        sb.append("PLACE_BLOCK torch 0 2 0\n");
        sb.append("END_DSL\n\n");

        sb.append("Example B (small fence in front):\n");
        sb.append("BEGIN_DSL\n");
        sb.append("PLACE_BLOCK oak_fence 0 0 2\n");
        sb.append("END_DSL\n\n");

        sb.append("Example C (small platform):\n");
        sb.append("BEGIN_DSL\n");
        sb.append("PLACE_BLOCK stone_bricks 0 0 0\n");
        sb.append("PLACE_BLOCK stone_bricks 1 0 0\n");
        sb.append("PLACE_BLOCK stone_bricks 0 0 1\n");
        sb.append("PLACE_BLOCK stone_bricks 1 0 1\n");
        sb.append("END_DSL\n");

        return sb.toString();
    }

    private static String joinAllowedBlocks() {
        StringJoiner j = new StringJoiner(", ");
        for (String b : AiPolicy.ALLOWED_BLOCKS) j.add(b);
        return j.toString();
    }

    /**
     * Best-effort: if the player explicitly mentions an allowed block id, detect it.
     * This avoids the model "ignoring" the user's requested block.
     */
    private static String tryDetectRequestedAllowedBlock(String thing) {
        if (thing == null) return null;
        String t = thing.toLowerCase(Locale.ROOT);

        // First: exact block id mentions
        for (String b : AiPolicy.ALLOWED_BLOCKS) {
            if (t.contains(b.toLowerCase(Locale.ROOT))) return b;
        }

        // Simple aliases (extend if needed)
        if (t.contains("torch") && containsAllowed("torch")) return "torch";
        if ((t.contains("fence") || t.contains("oak fence")) && containsAllowed("oak_fence")) return "oak_fence";
        if ((t.contains("stone bricks") || t.contains("stone_bricks")) && containsAllowed("stone_bricks")) return "stone_bricks";

        return null;
    }

    private static boolean containsAllowed(String id) {
        for (String b : AiPolicy.ALLOWED_BLOCKS) {
            if (b.equalsIgnoreCase(id)) return true;
        }
        return false;
    }
}
