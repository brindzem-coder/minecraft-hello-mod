package com.example.hellomod;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

public class ModCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {

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
        );
    }
}
