package com.example.hellomod.ai;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Простий per-player rate limiter (cooldown) по UUID.
 * Thread-safe, працює в пам'яті процесу сервера.
 */
public final class AiRateLimiter {

    private static final ConcurrentHashMap<UUID, Long> LAST_PREVIEW_MS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Long> LAST_EXEC_MS = new ConcurrentHashMap<>();

    private AiRateLimiter() {}

    public enum Channel {
        PREVIEW,
        EXECUTE
    }

    /**
     * @return 0 якщо дозволено зараз; інакше повертає milliseconds remaining до дозволу
     */
    public static long tryAcquire(UUID playerId, Channel channel, long cooldownMs) {
        long now = System.currentTimeMillis();
        ConcurrentHashMap<UUID, Long> map = (channel == Channel.EXECUTE) ? LAST_EXEC_MS : LAST_PREVIEW_MS;

        Long last = map.get(playerId);
        if (last != null) {
            long elapsed = now - last;
            long remaining = cooldownMs - elapsed;
            if (remaining > 0) {
                return remaining;
            }
        }

        map.put(playerId, now);
        return 0L;
    }
}
