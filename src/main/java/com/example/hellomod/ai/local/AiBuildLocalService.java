package com.example.hellomod.ai.local;

import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class AiBuildLocalService {
    private static final Logger LOGGER = LogManager.getLogger(AiBuildLocalService.class);

    private AiBuildLocalService() {}

    public static void handle(ServerLevel level, ServerPlayer player, String prompt) {
        String safePrompt = (prompt == null) ? "" : prompt.trim();

        LOGGER.info("[ai build_local] player={} dim={} prompt='{}'",
                player.getGameProfile().getName(),
                level.dimension().location(),
                safePrompt
        );

        player.sendMessage(
                new TextComponent("build_local: заглушка сервісу. Запит: " + safePrompt),
                player.getUUID()
        );
    }
}
