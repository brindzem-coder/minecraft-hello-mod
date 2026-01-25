package com.example.hellomod;

import com.example.hellomod.ai.local.AiBuildLocalService;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

public class ModCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {

        // Головна гілка /ai .
        dispatcher.register(
                Commands.literal("ai")

                        // /ai tower
                        .then(Commands.literal("tower")
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    ServerLevel level = ctx.getSource().getLevel();

                                    List<AiAction> actions = SimplePlanner.createTowerPlan(player);
                                    AiExecutor.executeAll(level, player, actions);

                                    return 1;
                                })
                        )

                        // /ai build <thing>
                        .then(Commands.literal("build")
                                .then(Commands.argument("thing", StringArgumentType.word())
                                        .executes(ctx -> {
                                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                                            ServerLevel level = ctx.getSource().getLevel();

                                            String thing = StringArgumentType.getString(ctx, "thing").toLowerCase();

                                            String script = RulePlanner.plan(level, player, thing);
                                            if (script == null || script.isBlank()) {
                                                // нічого будувати
                                                return 1;
                                            }

                                            ScriptRunner.runFromMultiline(level, player, script);
                                            return 1;
                                        })
                                )
                        )

                        // /ai build_local <thing...>
                        .then(Commands.literal("build_local")
                                .then(Commands.argument("thing", StringArgumentType.greedyString())
                                        .executes(ctx -> {
                                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                                            ServerLevel level = ctx.getSource().getLevel();

                                            String thing = StringArgumentType.getString(ctx, "thing");
                                            AiBuildLocalService.handle(level, player, thing);

                                            return 1;
                                        })
                                )
                        )

                        // /ai scan
                        .then(Commands.literal("scan")
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    ServerLevel level = ctx.getSource().getLevel();

                                    WorldScanner.scanAndReport(level, player);
                                    return 1;
                                })
                        )

                        // /ai exec_last
                        .then(Commands.literal("exec_last")
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    ServerLevel level = ctx.getSource().getLevel();

                                    List<String> lines = AiMemory.getLastValid(player.getUUID());
                                    if (lines == null || lines.isEmpty()) {
                                        player.sendMessage(new net.minecraft.network.chat.TextComponent(
                                                "Немає збереженого валідного скрипта. Спочатку зроби ai run: ... END або EXEC."
                                        ), player.getUUID());
                                        return 1;
                                    }

                                    // Для безпеки можемо ще раз прогнати валідацію (дешево і надійно)
                                    AiScriptValidator.Report report = AiScriptValidator.validateLines(lines);
                                    AiScriptValidator.sendReportToPlayer(player, report);

                                    if (!report.ok()) {
                                        player.sendMessage(new net.minecraft.network.chat.TextComponent(
                                                "exec_last: скрипт більше не проходить валідацію (виконання скасовано)."
                                        ), player.getUUID());
                                        return 1;
                                    }

                                    ScriptRunner.run(level, player, lines);
                                    return 1;
                                })
                        )

                        // /ai exec_dev
                        .then(Commands.literal("exec_dev")
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    ServerLevel level = ctx.getSource().getLevel();

                                    // Читаємо dev-скрипт із run/config/ai_dev_script.txt
                                    String script = DevScripts.loadDevScript(player);
                                    if (script == null) {
                                        // повідомлення вже відправлено всередині DevScripts
                                        return 1;
                                    }

                                    // Виконуємо як багаторядковий скрипт
                                    ScriptRunner.runFromMultiline(level, player, script);

                                    return 1;
                                })
                        )
        );

        // Окрема команда /ai_build "<вільний текст>"
        dispatcher.register(
                Commands.literal("ai_build")
                        .then(Commands.argument("intent", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    ServerLevel level = ctx.getSource().getLevel();

                                    String intent = StringArgumentType.getString(ctx, "intent");

                                    // Будуємо повний запит для ШІ
                                    String request = AiRequestBuilder.buildRequest(level, player, intent);

                                    // Виводимо у консоль (IntelliJ), щоб ти міг його скопіювати
                                    System.out.println(request);

                                    // Коротке повідомлення гравцю
                                    player.sendMessage(
                                            new net.minecraft.network.chat.TextComponent(
                                                    "AI request згенеровано. Відкрий консоль сервера/IDE та скопіюй блок між === AI REQUEST START === і === AI REQUEST END ===."
                                            ),
                                            player.getUUID()
                                    );

                                    return 1;
                                })
                        )
        );
    }
}
