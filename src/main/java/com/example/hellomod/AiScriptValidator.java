package com.example.hellomod;

import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

public class AiScriptValidator {

    public static class Report {
        public int totalActions;
        public int placeBlocks;
        public int maxAbsDx;
        public int maxAbsDy;
        public int maxAbsDz;
        public int clearAroundCount;
        public int maxClearRadius;
        public int buildBoxCount;

        public final List<String> errors = new ArrayList<>();
        public final List<String> warnings = new ArrayList<>();

        public boolean ok() {
            return errors.isEmpty();
        }
    }

    public static Report validateLines(List<String> lines) {
        Report r = new Report();

        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("#") || line.startsWith("//")) continue;
            if (line.endsWith(";")) line = line.substring(0, line.length()-1).trim();
            if (line.isEmpty()) continue;

            String[] parts = line.split("\\s+");
            String cmd = parts[0].toUpperCase();

            // Рахуємо дії (включно з невідомими, щоб не засипали сервер сміттям)
            r.totalActions++;

            if (r.totalActions > AiPolicy.MAX_ACTIONS) {
                r.errors.add("Забагато команд: " + r.totalActions + " (max " + AiPolicy.MAX_ACTIONS + ")");
                break;
            }

            switch (cmd) {
                case "PLACE_BLOCK" -> validatePlaceBlock(parts, line, r);
                case "CLEAR_AROUND" -> validateClearAround(parts, line, r);
                case "BUILD_BOX" -> validateBuildBox(parts, line, r);
                default -> r.errors.add("Невідома команда: " + cmd + " у рядку: " + line);
            }
        }

        if (r.placeBlocks > AiPolicy.MAX_PLACE_BLOCKS) {
            r.errors.add("PLACE_BLOCK забагато: " + r.placeBlocks + " (max " + AiPolicy.MAX_PLACE_BLOCKS + ")");
        }

        if (r.maxAbsDx > AiPolicy.MAX_ABS_DX || r.maxAbsDy > AiPolicy.MAX_ABS_DY || r.maxAbsDz > AiPolicy.MAX_ABS_DZ) {
            r.errors.add("Дистанція перевищує ліміт. max(|dx|,|dy|,|dz|)=("
                    + r.maxAbsDx + "," + r.maxAbsDy + "," + r.maxAbsDz + ") "
                    + "limit=(" + AiPolicy.MAX_ABS_DX + "," + AiPolicy.MAX_ABS_DY + "," + AiPolicy.MAX_ABS_DZ + ")");
        }

        return r;
    }

    private static void validatePlaceBlock(String[] parts, String line, Report r) {
        // PLACE_BLOCK block dx dy dz
        if (parts.length != 5) {
            r.errors.add("PLACE_BLOCK формат: PLACE_BLOCK block dx dy dz. Рядок: " + line);
            return;
        }

        String block = parts[1].toLowerCase();
        if (!AiPolicy.ALLOWED_BLOCKS.contains(block)) {
            r.errors.add("PLACE_BLOCK заборонений блок '" + block + "'. Дозволені: " + AiPolicy.ALLOWED_BLOCKS);
            return;
        }

        int dx, dy, dz;
        try {
            dx = Integer.parseInt(parts[2]);
            dy = Integer.parseInt(parts[3]);
            dz = Integer.parseInt(parts[4]);
        } catch (NumberFormatException e) {
            r.errors.add("PLACE_BLOCK: dx/dy/dz мають бути числа. Рядок: " + line);
            return;
        }

        r.placeBlocks++;

        r.maxAbsDx = Math.max(r.maxAbsDx, Math.abs(dx));
        r.maxAbsDy = Math.max(r.maxAbsDy, Math.abs(dy));
        r.maxAbsDz = Math.max(r.maxAbsDz, Math.abs(dz));
    }

    private static void validateClearAround(String[] parts, String line, Report r) {
        if (parts.length != 2) {
            r.errors.add("CLEAR_AROUND формат: CLEAR_AROUND radius. Рядок: " + line);
            return;
        }

        int radius;
        try {
            radius = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            r.errors.add("CLEAR_AROUND: radius має бути числом. Рядок: " + line);
            return;
        }

        r.clearAroundCount++;
        r.maxClearRadius = Math.max(r.maxClearRadius, radius);

        if (radius < 1 || radius > AiPolicy.MAX_CLEAR_RADIUS) {
            r.errors.add("CLEAR_AROUND: radius " + radius + " (дозволено 1–" + AiPolicy.MAX_CLEAR_RADIUS + ")");
        }
    }

    private static void validateBuildBox(String[] parts, String line, Report r) {
        if (parts.length != 4) {
            r.errors.add("BUILD_BOX формат: BUILD_BOX w h d. Рядок: " + line);
            return;
        }

        int w, h, d;
        try {
            w = Integer.parseInt(parts[1]);
            h = Integer.parseInt(parts[2]);
            d = Integer.parseInt(parts[3]);
        } catch (NumberFormatException e) {
            r.errors.add("BUILD_BOX: w/h/d мають бути числа. Рядок: " + line);
            return;
        }

        r.buildBoxCount++;

        if (w < 1 || h < 1 || d < 1 || w > AiPolicy.MAX_BOX_SIZE || h > AiPolicy.MAX_BOX_SIZE || d > AiPolicy.MAX_BOX_SIZE) {
            r.errors.add("BUILD_BOX: розміри " + w + " " + h + " " + d + " (дозволено 1–" + AiPolicy.MAX_BOX_SIZE + ")");
        }
    }

    public static void sendReportToPlayer(ServerPlayer player, Report r) {
        // Короткий summary
        player.sendMessage(new TextComponent(
                        "DRY-RUN: actions=" + r.totalActions +
                                ", place=" + r.placeBlocks +
                                ", clear=" + r.clearAroundCount +
                                ", box=" + r.buildBoxCount +
                                ", maxAbs(dx,dy,dz)=(" + r.maxAbsDx + "," + r.maxAbsDy + "," + r.maxAbsDz + ")"
                ),
                player.getUUID()
        );

        if (r.ok()) {
            player.sendMessage(new TextComponent("DRY-RUN: OK (можна виконувати)"), player.getUUID());
        } else {
            player.sendMessage(new TextComponent("DRY-RUN: НЕ OK (є помилки)"), player.getUUID());
            // Показуємо перші кілька помилок, щоб не спамити
            int limit = Math.min(6, r.errors.size());
            for (int i = 0; i < limit; i++) {
                player.sendMessage(new TextComponent("  - " + r.errors.get(i)), player.getUUID());
            }
            if (r.errors.size() > limit) {
                player.sendMessage(new TextComponent("  ...і ще " + (r.errors.size() - limit) + " помилок"), player.getUUID());
            }
        }
    }
}
