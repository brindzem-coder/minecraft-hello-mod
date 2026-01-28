package com.example.hellomod.ai.local;

import com.example.hellomod.AiMemory;
import com.example.hellomod.AiScriptValidator;
import com.example.hellomod.ScriptRunner;
import com.example.hellomod.ai.AiRateLimiter;
import com.example.hellomod.llm.LocalLlmClient;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class AiBuildLocalService {
    private static final Logger LOGGER = LogManager.getLogger(AiBuildLocalService.class);

    /** ExecMode = PREVIEW | EXECUTE */
    public enum ExecMode {
        PREVIEW,
        EXECUTE
    }

    // Cooldowns
    private static final long PREVIEW_COOLDOWN_MS = 5_000;
    private static final long EXECUTE_COOLDOWN_MS = 20_000;

    // Окремий потік для LLM-запитів (щоб не підвісити server thread)
    private static final ExecutorService LLM_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "hellomod-llm");
        t.setDaemon(true);
        return t;
    });

    // Скільки рядків DSL показуємо в чаті як preview (щоб не заспамити)
    private static final int CHAT_PREVIEW_MAX_LINES = 20;

    // ===== STRICT SAFETY (тільки для build_local_exec) =====
    // Рекомендація для auto-exec:
    private static final int STRICT_MAX_TOTAL_LINES = 200;
    private static final int STRICT_MAX_TOTAL_COST = 4000;
    private static final int STRICT_MAX_PLACE_BLOCK = 25;
    private static final int STRICT_MAX_CLEAR_AROUND_RADIUS = 8;
    private static final int STRICT_MAX_BOX_W = 12;
    private static final int STRICT_MAX_BOX_H = 10;
    private static final int STRICT_MAX_BOX_D = 12;
    // =======================================================

    private AiBuildLocalService() {}

    public static void start(ServerLevel level, ServerPlayer player, BlockPos origin, String thing, ExecMode mode) {
        final MinecraftServer server = level.getServer();
        if (server == null) return;

        final UUID playerId = player.getUUID();
        final String safeThing = (thing == null) ? "" : thing.trim();
        final ExecMode safeMode = (mode == null) ? ExecMode.PREVIEW : mode;

        // ===== COOLDOWN (anti-spam) =====
        AiRateLimiter.Channel channel = (safeMode == ExecMode.EXECUTE)
                ? AiRateLimiter.Channel.EXECUTE
                : AiRateLimiter.Channel.PREVIEW;

        long cooldownMs = (safeMode == ExecMode.EXECUTE) ? EXECUTE_COOLDOWN_MS : PREVIEW_COOLDOWN_MS;
        long remaining = AiRateLimiter.tryAcquire(playerId, channel, cooldownMs);

        if (remaining > 0) {
            long sec = Math.max(1, (remaining + 999) / 1000);
            String msg = (safeMode == ExecMode.EXECUTE)
                    ? ("build_local_exec: cooldown " + sec + "s")
                    : ("build_local: cooldown " + sec + "s");

            player.sendMessage(new TextComponent(msg), player.getUUID());
            return;
        }
        // ===============================

        // Prompt (worldContext поки порожній — пункт 2.6)
        final String prompt = BuildLocalPromptBuilder.buildPrompt(safeThing, "");

        LOGGER.info("[ai build_local] start mode={} player={} dim={} origin=({}, {}, {}) thing='{}'",
                safeMode,
                player.getGameProfile().getName(),
                level.dimension().location(),
                origin.getX(), origin.getY(), origin.getZ(),
                safeThing
        );

        // LLM — off-thread
        LLM_EXECUTOR.execute(() -> {
            try {
                LocalLlmClient client = LocalLlmClient.createDefault();

                LOGGER.info("[ai build_local] LLM cfg: {}", client.describe());
                LOGGER.info("[ai build_local] Prompt chars={}", prompt.length());

                String rawResponse = client.sendBlocking(prompt);

                LOGGER.info("[ai build_local] Response chars={}", rawResponse == null ? 0 : rawResponse.length());

                // Back to server thread
                server.execute(() -> handleResponseOnServerThread(server, playerId, rawResponse, safeMode));

            } catch (Exception e) {
                LOGGER.error("[ai build_local] LLM request failed", e);

                server.execute(() -> {
                    ServerPlayer p = server.getPlayerList().getPlayer(playerId);
                    if (p == null) return;
                    p.sendMessage(new TextComponent("build_local: LLM request failed: " + safeMsg(e)), p.getUUID());
                });
            }
        });
    }

    /**
     * Однакові кроки після відповіді LLM:
     * parse -> validate -> save last -> preview
     * Різниця лише в кінці: PREVIEW message або EXECUTE через "exec_last" механізм.
     */
    private static void handleResponseOnServerThread(MinecraftServer server, UUID playerId, String rawResponse, ExecMode mode) {
        ServerPlayer p = server.getPlayerList().getPlayer(playerId);
        if (p == null) return;

        // 1) Parse -> DSL
        LlmResponseParser.ParseResult parsed = LlmResponseParser.parse(rawResponse);
        if (!parsed.ok()) {
            p.sendMessage(new TextComponent("build_local: invalid LLM response: " + parsed.error()), p.getUUID());
            return;
        }

        List<String> lines = parsed.dslLines();

        LOGGER.info("[ai build_local] mode={} DSL lines={} usedMarkers={} warnings={}\n{}",
                mode,
                lines.size(),
                parsed.usedMarkers(),
                parsed.warnings(),
                String.join("\n", lines)
        );

        // 2) Validate (легкий)
        AiScriptValidator.Report report = AiScriptValidator.validateLines(lines);
        if (!report.ok()) {
            p.sendMessage(new TextComponent("build_local: script blocked by validator (NOT saved). Preview:"), p.getUUID());
            sendPreviewToChat(p, lines);
            AiScriptValidator.sendReportToPlayer(p, report);
            return;
        }

        // 3) Save (last script)
        AiMemory.saveLastValid(p.getUUID(), lines);

        // 4) Preview (always)
        p.sendMessage(new TextComponent("build_local: last script saved. Preview:"), p.getUUID());
        sendPreviewToChat(p, lines);

        // 5) Difference only here
        if (mode == ExecMode.PREVIEW) {
            p.sendMessage(new TextComponent("Saved as last script. Use /ai exec_last"), p.getUUID());
            return;
        }

        // EXECUTE: як exec_last, але зі strict-гейтом
        executeViaExecLastMechanism(p, true);
    }

    private static void executeViaExecLastMechanism(ServerPlayer p, boolean strict) {
        List<String> lines = AiMemory.getLastValid(p.getUUID());
        if (lines == null || lines.isEmpty()) {
            p.sendMessage(new TextComponent("build_local: nothing to execute (last script is empty)."), p.getUUID());
            return;
        }

        // Як у /ai exec_last: validate + report
        AiScriptValidator.Report report = AiScriptValidator.validateLines(lines);
        AiScriptValidator.sendReportToPlayer(p, report);

        if (!report.ok()) {
            p.sendMessage(new TextComponent("build_local: exec canceled (validator failed)."), p.getUUID());
            return;
        }

        // Додатковий strict-гейт ТІЛЬКИ для auto-exec
        if (strict) {
            String strictFail = strictSafetyCheck(lines);
            if (strictFail != null) {
                p.sendMessage(new TextComponent("build_local_exec: STRICT safety check failed: " + strictFail), p.getUUID());
                p.sendMessage(new TextComponent("build_local_exec: saved as last script, but NOT executed. Use /ai exec_last if you still want to run it manually."), p.getUUID());
                return;
            }
        }

        try {
            ServerLevel level = p.getLevel();
            p.sendMessage(new TextComponent("build_local: executing last script..."), p.getUUID());
            ScriptRunner.run(level, p, lines);
            p.sendMessage(new TextComponent("build_local: done."), p.getUUID());
        } catch (Exception e) {
            LOGGER.error("[ai build_local] EXECUTE failed during execution", e);
            p.sendMessage(new TextComponent("build_local: execution failed: " + safeMsg(e)), p.getUUID());
        }
    }

    /**
     * Строгіший “гейт” для auto-exec.
     * Повертає null якщо OK, або текст причини відмови.
     */
    private static String strictSafetyCheck(List<String> lines) {
        if (lines.size() > STRICT_MAX_TOTAL_LINES) {
            return "too many DSL lines (" + lines.size() + " > " + STRICT_MAX_TOTAL_LINES + ")";
        }

        int placeCount = 0;
        long totalCost = 0;

        for (String raw : lines) {
            if (raw == null) continue;
            String line = raw.trim();
            if (line.isEmpty()) continue;

            String[] parts = line.split("\\s+");
            String cmd = parts[0].toUpperCase();

            switch (cmd) {
                case "PLACE_BLOCK" -> {
                    placeCount++;
                    if (placeCount > STRICT_MAX_PLACE_BLOCK) {
                        return "too many PLACE_BLOCK commands (" + placeCount + " > " + STRICT_MAX_PLACE_BLOCK + ")";
                    }
                    totalCost += 1; // 1 блок = 1
                }

                case "CLEAR_AROUND" -> {
                    if (parts.length < 2) return "CLEAR_AROUND missing radius";
                    int r = parseIntSafe(parts[1], -1);
                    if (r < 0) return "CLEAR_AROUND invalid radius: " + parts[1];
                    if (r > STRICT_MAX_CLEAR_AROUND_RADIUS) {
                        return "CLEAR_AROUND radius too large (" + r + " > " + STRICT_MAX_CLEAR_AROUND_RADIUS + ")";
                    }

                    // Оцінка вартості: площа (2r+1)^2
                    long area = (long) (2 * r + 1) * (2L * r + 1);
                    totalCost += area;
                }

                case "BUILD_BOX" -> {
                    if (parts.length < 4) return "BUILD_BOX missing sizes";
                    int w = parseIntSafe(parts[1], -1);
                    int h = parseIntSafe(parts[2], -1);
                    int d = parseIntSafe(parts[3], -1);
                    if (w < 1 || h < 1 || d < 1) return "BUILD_BOX invalid sizes";
                    if (w > STRICT_MAX_BOX_W || h > STRICT_MAX_BOX_H || d > STRICT_MAX_BOX_D) {
                        return "BUILD_BOX too large (" + w + "x" + h + "x" + d + ") max=("
                                + STRICT_MAX_BOX_W + "x" + STRICT_MAX_BOX_H + "x" + STRICT_MAX_BOX_D + ")";
                    }

                    // Оцінка вартості: обʼєм w*h*d (скільки блоків потенційно поставить)
                    totalCost += (long) w * h * d;
                }

                default -> {
                    // Інші команди вже відсікає AiScriptValidator — тут не дублюємо.
                }
            }

            if (totalCost > STRICT_MAX_TOTAL_COST) {
                return "script cost too high (" + totalCost + " > " + STRICT_MAX_TOTAL_COST + ")";
            }
        }

        return null;
    }

    private static int parseIntSafe(String s, int def) {
        try {
            return Integer.parseInt(s);
        } catch (Exception ignored) {
            return def;
        }
    }

    private static void sendPreviewToChat(ServerPlayer p, List<String> lines) {
        int total = lines == null ? 0 : lines.size();
        int max = Math.min(total, CHAT_PREVIEW_MAX_LINES);

        for (int i = 0; i < max; i++) {
            p.sendMessage(new TextComponent((i + 1) + ") " + lines.get(i)), p.getUUID());
        }

        if (total > CHAT_PREVIEW_MAX_LINES) {
            p.sendMessage(new TextComponent("...(truncated, total lines: " + total + ")"), p.getUUID());
        }
    }

    private static String safeMsg(Exception e) {
        String m = (e.getMessage() == null) ? e.getClass().getSimpleName() : e.getMessage();
        if (m.length() > 220) m = m.substring(0, 220) + "...";
        return m;
    }
}
