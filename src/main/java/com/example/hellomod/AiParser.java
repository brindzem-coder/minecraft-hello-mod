package com.example.hellomod;

public class AiParser {

    // Приймає текст після "ai:" і повертає структуровану дію
    public static AiAction parse(String prompt) {
        String p = prompt.trim().toLowerCase();

        // ai: build box 5 4 6
        if (p.startsWith("build box")) {
            String[] parts = prompt.trim().split("\\s+");
            if (parts.length == 5) {
                try {
                    int w = Integer.parseInt(parts[2]);
                    int h = Integer.parseInt(parts[3]);
                    int d = Integer.parseInt(parts[4]);
                    return AiAction.buildBox(w, h, d);
                } catch (NumberFormatException ignored) {
                    return AiAction.unknown();
                }
            }
            return AiAction.unknown();
        }

        // ai: clear around 5
        if (p.startsWith("clear around")) {
            String[] parts = prompt.trim().split("\\s+");
            if (parts.length == 3) {
                try {
                    int r = Integer.parseInt(parts[2]);
                    return AiAction.clearAround(r);
                } catch (NumberFormatException ignored) {
                    return AiAction.unknown();
                }
            }
            return AiAction.unknown();
        }

        return AiAction.unknown();
    }
}
