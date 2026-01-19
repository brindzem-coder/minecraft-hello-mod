package com.example.hellomod;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.UUID;


import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod("hellomod")
public class HelloMod {

    // Для режиму "ai run:" — тут зберігаємо рядки скрипта для кожного гравця
    private static final Map<UUID, List<String>> SCRIPT_BUFFERS = new HashMap<>();

    public HelloMod() {
        // Реєструємо цей клас як слухача подій Forge (чат, логін)
        MinecraftForge.EVENT_BUS.register(this);

        // Реєструємо слухача події реєстрації команд (для /ai tower)
        MinecraftForge.EVENT_BUS.addListener(this::onRegisterCommands);

        System.out.println("HelloMod завантажено!");
    }

    // Останній валідний скрипт для кожного гравця (після DRY-RUN без помилок)
    private static final Map<UUID, List<String>> LAST_VALID_SCRIPTS = new HashMap<>();

    /**
     * Цей метод викликається Forge, коли треба зареєструвати команди.
     * Тут підключаємо наш ModCommands.register(...)
     */
    private void onRegisterCommands(RegisterCommandsEvent event) {
        ModCommands.register(event.getDispatcher());
    }

    // ─────────────────────────────────────────
    // Подія: гравець зайшов у світ
    // ─────────────────────────────────────────
    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        event.getPlayer().sendMessage(
                new TextComponent("Привіт Minecraft від мого мода!"),
                event.getPlayer().getUUID()
        );
    }

    // ─────────────────────────────────────────
    // Подія: гравець написав щось у чат
    // ─────────────────────────────────────────
    @SubscribeEvent
    public void onServerChat(ServerChatEvent event) {
        String msg = event.getMessage().trim();
        ServerPlayer player = event.getPlayer();
        UUID id = player.getUUID();

        if (SCRIPT_BUFFERS.containsKey(id)) {

            // Якщо гравець пише END або EXEC — завершуємо режим
            if (msg.equalsIgnoreCase("END") || msg.equalsIgnoreCase("EXEC")) {
                List<String> lines = SCRIPT_BUFFERS.remove(id);

                ServerLevel level = (ServerLevel) player.getLevel();

                // 1) DRY-RUN
                AiScriptValidator.Report report = AiScriptValidator.validateLines(lines);
                AiScriptValidator.sendReportToPlayer(player, report);

                // 2) Якщо все OK — зберігаємо як "останній валідний скрипт"
                if (report.ok()) {
                    AiMemory.saveLastValid(id, lines);
                    player.sendMessage(
                            new TextComponent("Збережено як останній валідний скрипт. Виконати: /ai exec_last"),
                            player.getUUID()
                    );
                }

                // 3) Якщо EXEC і OK — виконуємо
                if (msg.equalsIgnoreCase("EXEC") && report.ok()) {
                    ScriptRunner.run(level, player, lines);
                } else if (msg.equalsIgnoreCase("EXEC")) {
                    player.sendMessage(
                            new TextComponent("Виконання скасовано через помилки у DRY-RUN."),
                            player.getUUID()
                    );
                }

                event.setCanceled(true);
                return;
            }

            // Інакше: це звичайний рядок скрипта — додаємо в буфер
            SCRIPT_BUFFERS.get(id).add(msg);

            // (опційно) можна підказувати, скільки рядків уже накопичено
            // player.sendMessage(new TextComponent("Додано рядок. Всього: " + SCRIPT_BUFFERS.get(id).size()), player.getUUID());

            event.setCanceled(true);
            return;
        }

        // 0.1) Старт режиму скрипта: "ai run:"
        if (msg.equalsIgnoreCase("ai run:")) {
            SCRIPT_BUFFERS.put(id, new ArrayList<>());

            player.sendMessage(
                    new TextComponent("Режим скрипта увімкнено. Вводь рядки типу 'PLACE_BLOCK stone 0 0 4'. Напиши 'END', щоб виконати."),
                    player.getUUID()
            );

            event.setCanceled(true);
            return;
        }

        // ДАЛІ — твій існуючий код:
        // ai testplan, ai:, hello, build box...
        // (після цього блоку нічого не видаляємо, тільки йде далі твоя логіка)


        // 0) Тестовий план (ai testplan)
        if (msg.equalsIgnoreCase("ai testplan")) {
            ServerLevel level = (ServerLevel) player.getLevel();

            AiPlan plan = new AiPlan()
                    .add(AiStep.box(0, 0, 0, 12, 6, 12, true))
                    .add(AiStep.box(2, 1, 2, 3, 3, 3, false))
                    .add(AiStep.clearAround(0, 0, 0, 6));

            AiExecutor.executePlan(level, player, plan);
            return;
        }

        // 1) AI-повідомлення: "ai: ..."
        if (msg.toLowerCase().startsWith("ai:")) {
            String prompt = msg.substring(3).trim(); // все, що після "ai:"
            AiAction action = AiParser.parse(prompt);
            ServerLevel level = (ServerLevel) player.getLevel();
            AiExecutor.execute(level, player, action);
            return;
        }

        // 2) Просте "hello"
        if (msg.equalsIgnoreCase("hello")) {
            player.sendMessage(
                    new TextComponent("Привіт, " + player.getName().getString() + "!"),
                    player.getUUID()
            );
            return;
        }

        // 3) Пряма команда "build box w h d" через чат
        String lower = msg.toLowerCase();
        if (lower.startsWith("build box")) {
            String[] parts = msg.split("\\s+");
            if (parts.length != 5) {
                player.sendMessage(
                        new TextComponent(
                                "Формат: build box <width> <height> <depth>, напр. build box 5 4 6"
                        ),
                        player.getUUID()
                );
                return;
            }

            int width, height, depth;
            try {
                width  = Integer.parseInt(parts[2]);
                height = Integer.parseInt(parts[3]);
                depth  = Integer.parseInt(parts[4]);
            } catch (NumberFormatException e) {
                player.sendMessage(
                        new TextComponent("Ширина/висота/глибина мають бути числами."),
                        player.getUUID()
                );
                return;
            }

            if (width <= 0 || height <= 0 || depth <= 0
                    || width > 50 || height > 50 || depth > 50) {
                player.sendMessage(
                        new TextComponent("Розміри мають бути в межах 1–50."),
                        player.getUUID()
                );
                return;
            }

            ServerLevel level = (ServerLevel) player.getLevel();
            BlockPos basePos = player.blockPosition().offset(2, 0, 2);

            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    for (int z = 0; z < depth; z++) {
                        BlockPos p = basePos.offset(x, y, z);
                        level.setBlock(p, Blocks.STONE_BRICKS.defaultBlockState(), 3);
                    }
                }
            }

            player.sendMessage(
                    new TextComponent("Побудовано box " + width + "×" + height + "×" + depth),
                    player.getUUID()
            );
        }
    }
}
