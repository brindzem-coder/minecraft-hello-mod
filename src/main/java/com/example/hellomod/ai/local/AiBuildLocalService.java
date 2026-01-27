package com.example.hellomod.ai.local;

import com.example.hellomod.AiMemory;
import com.example.hellomod.AiScriptValidator;
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

    // Окремий потік для LLM-запитів (щоб не підвісити server thread)
    private static final ExecutorService LLM_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "hellomod-llm");
        t.setDaemon(true);
        return t;
    });

    // Скільки рядків DSL показуємо в чаті як preview (щоб не заспамити)
    private static final int CHAT_PREVIEW_MAX_LINES = 20;

    private AiBuildLocalService() {}

    /**
     * Асинхронний старт build_local пайплайна:
     * - збирає промпт
     * - робить LLM HTTP запит в окремому потоці
     * - повертається у main server thread і тільки тоді пише в чат/пам’ять
     */
    public static void start(ServerLevel level, ServerPlayer player, BlockPos origin, String thing) {
        final MinecraftServer server = level.getServer();
        if (server == null) return;

        final UUID playerId = player.getUUID();
        final String safeThing = (thing == null) ? "" : thing.trim();

        // 1) Зібрати промпт (worldContext поки порожній — пункт 2.6)
        final String prompt = BuildLocalPromptBuilder.buildPrompt(safeThing, "");

        LOGGER.info("[ai build_local] start player={} dim={} origin=({}, {}, {}) thing='{}'",
                player.getGameProfile().getName(),
                level.dimension().location(),
                origin.getX(), origin.getY(), origin.getZ(),
                safeThing
        );

        // 2) Запит до LLM — НЕ на server thread
        LLM_EXECUTOR.execute(() -> {
            try {
                LocalLlmClient client = LocalLlmClient.createDefault();

                LOGGER.info("[ai build_local] LLM cfg: {}", client.describe());
                LOGGER.info("[ai build_local] Prompt chars={}", prompt.length());

                String rawResponse = client.sendBlocking(prompt);

                LOGGER.info("[ai build_local] Response chars={}", rawResponse == null ? 0 : rawResponse.length());


                // 3) Повертаємось в main server thread для чат/пам’ять
                server.execute(() -> handleResponseOnServerThread(server, playerId, rawResponse));

            } catch (Exception e) {
                LOGGER.error("[ai build_local] LLM request failed", e);

                // 3) Повертаємось в main server thread навіть для помилки
                server.execute(() -> {
                    ServerPlayer p = server.getPlayerList().getPlayer(playerId);
                    if (p == null) return;
                    p.sendMessage(new TextComponent("build_local: LLM request failed: " + safeMsg(e)), p.getUUID());
                });
            }
        });
    }

    private static void handleResponseOnServerThread(MinecraftServer server, UUID playerId, String rawResponse) {
        ServerPlayer p = server.getPlayerList().getPlayer(playerId);
        if (p == null) return;

        // 1) Парсимо відповідь у DSL
        LlmResponseParser.ParseResult parsed = LlmResponseParser.parse(rawResponse);
        if (!parsed.ok()) {
            p.sendMessage(new TextComponent("build_local: invalid LLM response: " + parsed.error()), p.getUUID());
            return;
        }

        List<String> lines = parsed.dslLines();

        // 2) Лог у консоль/лог-файл (повністю)
        LOGGER.info("[ai build_local] DSL lines={} usedMarkers={} warnings={}\n{}",
                lines.size(),
                parsed.usedMarkers(),
                parsed.warnings(),
                String.join("\n", lines)
        );

        // 3) Валідатор (без виконання)
        AiScriptValidator.Report report = AiScriptValidator.validateLines(lines);

        if (!report.ok()) {
            p.sendMessage(new TextComponent("build_local: script blocked by validator (NOT saved). Preview:"), p.getUUID());
            sendPreviewToChat(p, lines);
            AiScriptValidator.sendReportToPlayer(p, report);
            return;
        }

        // 4) Зберігаємо як "останній валідний скрипт" (без виконання)
        AiMemory.saveLastValid(p.getUUID(), lines);

        // 5) Показуємо preview у чаті
        p.sendMessage(new TextComponent("build_local: script saved as last_valid. Preview:"), p.getUUID());
        sendPreviewToChat(p, lines);
        p.sendMessage(new TextComponent("Use /ai exec_last to run."), p.getUUID());
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
