package com.example.hellomod.ai.local;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Парсер відповіді LLM у DSL-рядки.
 *
 * Контракт (підтримуються обидва варіанти):
 * A) Відповідь містить ТІЛЬКИ DSL-рядки (без пояснень) -> беремо всі змістовні рядки.
 * B) DSL знаходиться між маркерами BEGIN_DSL / END_DSL -> беремо тільки між ними.
 *
 * Додатково:
 * - ігнорує рядки з markdown code fence: ``` або ```dsl
 * - видаляє порожні рядки
 */
public final class LlmResponseParser {

    public static final String BEGIN = "BEGIN_DSL";
    public static final String END = "END_DSL";

    private LlmResponseParser() {}

    public static ParseResult parse(String rawResponse) {
        if (rawResponse == null) rawResponse = "";

        String raw = rawResponse.trim();
        if (raw.isEmpty()) {
            return ParseResult.error("Empty LLM response.");
        }

        // 1) Prefer markers if present
        int beginIdx = indexOfIgnoreCase(raw, BEGIN);
        int endIdx = indexOfIgnoreCase(raw, END);

        String payload;
        boolean usedMarkers = false;
        List<String> warnings = new ArrayList<>();

        if (beginIdx >= 0) {
            usedMarkers = true;

            int beginPayloadStart = beginIdx + BEGIN.length();

            if (endIdx >= 0 && endIdx > beginPayloadStart) {
                payload = raw.substring(beginPayloadStart, endIdx);
            } else {
                // BEGIN present but END missing -> take everything after BEGIN
                payload = raw.substring(beginPayloadStart);
                warnings.add("END_DSL marker not found; taking everything after BEGIN_DSL.");
            }
        } else {
            // No markers -> assume variant A (all DSL lines)
            payload = raw;
        }

        List<String> lines = splitAndCleanLines(payload, warnings);

        if (lines.isEmpty()) {
            if (usedMarkers) {
                return ParseResult.error("No DSL lines found between markers.");
            }
            return ParseResult.error("No DSL lines found in response.");
        }

        return new ParseResult(true, usedMarkers, Collections.unmodifiableList(lines), Collections.unmodifiableList(warnings));
    }

    private static List<String> splitAndCleanLines(String text, List<String> warnings) {
        if (text == null) return Collections.emptyList();

        String normalized = text.replace("\r\n", "\n").replace("\r", "\n");

        String[] rawLines = normalized.split("\n");
        List<String> out = new ArrayList<>(rawLines.length);

        int removedFences = 0;
        int removedMarkers = 0;

        for (String line : rawLines) {
            if (line == null) continue;
            String t = line.trim();

            if (t.isEmpty()) continue;

            // ignore markdown code fences
            if (t.startsWith("```")) {
                removedFences++;
                continue;
            }

            // ignore markers if they leak into payload
            if (equalsIgnoreCase(t, BEGIN) || equalsIgnoreCase(t, END)) {
                removedMarkers++;
                continue;
            }

            out.add(t);
        }

        if (removedFences > 0) {
            warnings.add("Removed markdown code fences: " + removedFences);
        }
        if (removedMarkers > 0) {
            warnings.add("Removed marker lines found inside payload: " + removedMarkers);
        }

        return out;
    }

    private static int indexOfIgnoreCase(String haystack, String needle) {
        return haystack.toLowerCase().indexOf(needle.toLowerCase());
    }

    private static boolean equalsIgnoreCase(String a, String b) {
        return a != null && b != null && a.equalsIgnoreCase(b);
    }

    public static final class ParseResult {
        private final boolean ok;
        private final boolean usedMarkers;
        private final List<String> dslLines;
        private final List<String> warnings;
        private final String error;

        private ParseResult(boolean ok, boolean usedMarkers, List<String> dslLines, List<String> warnings, String error) {
            this.ok = ok;
            this.usedMarkers = usedMarkers;
            this.dslLines = dslLines;
            this.warnings = warnings;
            this.error = error;
        }

        public ParseResult(boolean ok, boolean usedMarkers, List<String> dslLines, List<String> warnings) {
            this(ok, usedMarkers, dslLines, warnings, null);
        }

        public static ParseResult error(String message) {
            return new ParseResult(false, false, Collections.emptyList(), Collections.emptyList(), message);
        }

        public boolean ok() {
            return ok;
        }

        public boolean usedMarkers() {
            return usedMarkers;
        }

        public List<String> dslLines() {
            return dslLines;
        }

        public List<String> warnings() {
            return warnings;
        }

        public String error() {
            return error;
        }

        public String dslAsMultiline() {
            if (!ok || dslLines.isEmpty()) return "";
            return String.join("\n", dslLines);
        }
    }
}
