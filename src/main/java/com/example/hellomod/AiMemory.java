package com.example.hellomod;

import java.util.*;

public class AiMemory {

    private static final Map<UUID, List<String>> LAST_VALID_SCRIPTS = new HashMap<>();

    public static void saveLastValid(UUID playerId, List<String> lines) {
        LAST_VALID_SCRIPTS.put(playerId, new ArrayList<>(lines));
    }

    public static List<String> getLastValid(UUID playerId) {
        List<String> lines = LAST_VALID_SCRIPTS.get(playerId);
        return (lines == null) ? null : new ArrayList<>(lines);
    }

    private AiMemory() {}
}
