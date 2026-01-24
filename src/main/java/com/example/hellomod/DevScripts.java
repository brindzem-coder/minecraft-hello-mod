package com.example.hellomod;

import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * DevScripts – утиліта для читання dev-скрипта з диска.
 *
 * Шлях (від робочої директорії dev-сервера):
 *   run/config/ai_dev_script.txt
 *
 * Формат файлу:
 *   кожен рядок = команда скрипта, наприклад:
 *
 *   CLEAR_AROUND 10
 *   PLACE_BLOCK minecraft:oak_planks 0 0 3
 *   PLACE_BLOCK minecraft:oak_planks 0 0 4
 *   PLACE_BLOCK minecraft:oak_fence 1 1 3
 *   PLACE_BLOCK minecraft:oak_fence -1 1 3
 */
public class DevScripts {

    private static final Path DEV_SCRIPT_PATH = Paths.get("run", "config", "ai_dev_script.txt");

    /**
     * Читає dev-скрипт із файлу.
     *
     * @param player гравець для повідомлень про помилки
     * @return вміст файлу або null, якщо сталася помилка (в цьому випадку команда нічого не робить)
     */
    public static String loadDevScript(ServerPlayer player) {
        Path path = DEV_SCRIPT_PATH;

        try {
            if (!Files.exists(path)) {
                player.sendMessage(
                        new TextComponent("DEV_SCRIPT файл не знайдено: " + path.toAbsolutePath()),
                        player.getUUID()
                );
                return null;
            }

            String content = Files.readString(path, StandardCharsets.UTF_8);
            if (content == null || content.trim().isEmpty()) {
                player.sendMessage(
                        new TextComponent("DEV_SCRIPT файл порожній: " + path.toAbsolutePath()),
                        player.getUUID()
                );
                return null;
            }

            return content;
        } catch (IOException e) {
            player.sendMessage(
                    new TextComponent("Помилка читання DEV_SCRIPT: " + e.getMessage()),
                    player.getUUID()
            );
            return null;
        }
    }

    /**
     * Для дебагу, якщо треба побачити повний шлях у логах/консолі.
     */
    public static String getDevScriptPathForDebug() {
        return DEV_SCRIPT_PATH.toAbsolutePath().toString();
    }

    private DevScripts() {
        // utility class
    }
}
