package com.example.hellomod.ai.local;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class AiBuildLocalService {
    private static final Logger LOGGER = LogManager.getLogger(AiBuildLocalService.class);

    private AiBuildLocalService() {}

    /**
     * Заглушка старту build_local пайплайна.
     * На цьому етапі: тільки логування отриманих даних, без змін світу і без LLM.
     */
    public static void start(ServerLevel level, ServerPlayer player, BlockPos origin, String thing) {
        String safeThing = (thing == null) ? "" : thing.trim();

        LOGGER.info("[ai build_local started] player={} dim={} origin=({}, {}, {}) thing='{}'",
                player.getGameProfile().getName(),
                level.dimension().location(),
                origin.getX(), origin.getY(), origin.getZ(),
                safeThing
        );
    }
}
