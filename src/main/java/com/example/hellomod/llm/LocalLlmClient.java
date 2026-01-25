package com.example.hellomod.llm;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Мінімальний LLM-клієнт: "відправити текст -> отримати текст".
 *
 * Підтримувані бекенди (вибирається через config):
 * 1) OLLAMA_GENERATE: POST {baseUrl}/api/generate  (дефолт)
 * 2) OPENAI_CHAT:     POST {baseUrl}/v1/chat/completions (LM Studio / llama.cpp server / інші OpenAI-compatible)
 *
 * Конфіг:
 * - файл: run/config/local_llm.json
 * - або system properties (мають пріоритет): ai.llm.mode, ai.llm.baseUrl, ai.llm.model, ai.llm.timeoutSec, ai.llm.apiKey
 *
 * Важливо для Forge: мережеві виклики роби НЕ на серверному треді. Для цього є sendAsync(...).
 */
public final class LocalLlmClient {

    public enum Mode {
        OLLAMA_GENERATE,
        OPENAI_CHAT
    }

    public static final class Config {
        public Mode mode = Mode.OLLAMA_GENERATE;

        /** Напр.: http://localhost:11434 для Ollama, або http://localhost:1234 для LM Studio */
        public String baseUrl = "http://localhost:11434";

        /** Напр.: "llama3.1" для Ollama, або "local-model" для OpenAI-compatible */
        public String model = "llama3.1";

        /** Таймаут на запит */
        public int timeoutSec = 60;

        /** Для OPENAI_CHAT (якщо потрібно). Для локальних зазвичай порожньо. */
        public String apiKey = "";

        /** Температура для OPENAI_CHAT (опційно). */
        public double temperature = 0.2;
    }

    private static final Gson GSON = new Gson();

    private final HttpClient http;
    private final Config cfg;

    public LocalLlmClient(Config cfg) {
        this.cfg = Objects.requireNonNull(cfg, "cfg");
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.max(5, cfg.timeoutSec)))
                .build();
    }

    /** Дефолтний клієнт з run/config/local_llm.json (або дефолти, якщо файла немає). */
    public static LocalLlmClient createDefault() {
        Config cfg = loadConfig();
        applySystemOverrides(cfg);
        return new LocalLlmClient(cfg);
    }

    /** Асинхронний запит (рекомендовано викликати з команди). */
    public CompletableFuture<String> sendAsync(String prompt) {
        HttpRequest req = buildRequest(prompt);
        return http.sendAsync(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(resp -> {
                    int code = resp.statusCode();
                    String body = resp.body() == null ? "" : resp.body();
                    if (code < 200 || code >= 300) {
                        throw new LocalLlmException("LLM HTTP " + code + ": " + trimForLog(body));
                    }
                    return parseResponse(body);
                });
    }

    /** Синхронний запит (не викликай на server thread). */
    public String sendBlocking(String prompt) throws IOException, InterruptedException {
        HttpRequest req = buildRequest(prompt);
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        int code = resp.statusCode();
        String body = resp.body() == null ? "" : resp.body();
        if (code < 200 || code >= 300) {
            throw new LocalLlmException("LLM HTTP " + code + ": " + trimForLog(body));
        }
        return parseResponse(body);
    }

    private HttpRequest buildRequest(String prompt) {
        String p = prompt == null ? "" : prompt;

        return switch (cfg.mode) {
            case OLLAMA_GENERATE -> buildOllamaGenerate(p);
            case OPENAI_CHAT -> buildOpenAiChat(p);
        };
    }

    private HttpRequest buildOllamaGenerate(String prompt) {
        // Ollama: POST /api/generate
        // body: { "model": "...", "prompt": "...", "stream": false }
        JsonObject body = new JsonObject();
        body.addProperty("model", cfg.model);
        body.addProperty("prompt", prompt);
        body.addProperty("stream", false);

        String url = normalizeBase(cfg.baseUrl) + "/api/generate";

        return HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(Math.max(5, cfg.timeoutSec)))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body), StandardCharsets.UTF_8))
                .build();
    }

    private HttpRequest buildOpenAiChat(String prompt) {
        // OpenAI-compatible: POST /v1/chat/completions
        // body: { model, messages:[{role:"user",content:"..."}], temperature }
        JsonObject body = new JsonObject();
        body.addProperty("model", cfg.model);

        JsonArray messages = new JsonArray();
        JsonObject msg = new JsonObject();
        msg.addProperty("role", "user");
        msg.addProperty("content", prompt);
        messages.add(msg);

        body.add("messages", messages);
        body.addProperty("temperature", cfg.temperature);

        String url = normalizeBase(cfg.baseUrl) + "/v1/chat/completions";

        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(Math.max(5, cfg.timeoutSec)))
                .header("Content-Type", "application/json");

        if (cfg.apiKey != null && !cfg.apiKey.isBlank()) {
            b.header("Authorization", "Bearer " + cfg.apiKey.trim());
        }

        return b.POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body), StandardCharsets.UTF_8))
                .build();
    }

    private String parseResponse(String json) {
        String raw = json == null ? "" : json.trim();
        if (raw.isEmpty()) {
            throw new LocalLlmException("Empty response body.");
        }

        try {
            JsonObject obj = GSON.fromJson(raw, JsonObject.class);
            if (obj == null) {
                throw new LocalLlmException("Invalid JSON response.");
            }

            // Ollama: { response: "..." }
            if (cfg.mode == Mode.OLLAMA_GENERATE) {
                if (obj.has("response")) {
                    return obj.get("response").getAsString();
                }
                // fallback
                throw new LocalLlmException("Ollama response missing 'response' field.");
            }

            // OpenAI chat: { choices:[{message:{content:"..."}}] }
            if (cfg.mode == Mode.OPENAI_CHAT) {
                if (!obj.has("choices")) {
                    throw new LocalLlmException("OpenAI response missing 'choices'.");
                }
                JsonArray choices = obj.getAsJsonArray("choices");
                if (choices.size() == 0) {
                    throw new LocalLlmException("OpenAI response has empty 'choices'.");
                }
                JsonObject choice0 = choices.get(0).getAsJsonObject();
                if (choice0.has("message")) {
                    JsonObject message = choice0.getAsJsonObject("message");
                    if (message.has("content")) {
                        return message.get("content").getAsString();
                    }
                }
                // деякі сервери можуть повертати "text"
                if (choice0.has("text")) {
                    return choice0.get("text").getAsString();
                }
                throw new LocalLlmException("OpenAI response missing message.content.");
            }

            throw new LocalLlmException("Unsupported mode: " + cfg.mode);
        } catch (RuntimeException e) {
            throw new LocalLlmException("Failed to parse LLM response JSON: " + e.getMessage() + " | body=" + trimForLog(raw), e);
        }
    }

    private static Config loadConfig() {
        // run/config/local_llm.json (відносно робочої директорії run/)
        Config cfg = new Config();
        Path path = Path.of("config", "local_llm.json"); // якщо запускаєш з run/, це буде run/config/local_llm.json
        if (!Files.exists(path)) {
            return cfg;
        }

        try {
            String json = Files.readString(path, StandardCharsets.UTF_8);
            Config from = GSON.fromJson(json, Config.class);
            if (from != null) {
                // мерджимо акуратно (щоб null не затирав дефолти)
                if (from.mode != null) cfg.mode = from.mode;
                if (from.baseUrl != null && !from.baseUrl.isBlank()) cfg.baseUrl = from.baseUrl;
                if (from.model != null && !from.model.isBlank()) cfg.model = from.model;
                if (from.timeoutSec > 0) cfg.timeoutSec = from.timeoutSec;
                if (from.apiKey != null) cfg.apiKey = from.apiKey;
                cfg.temperature = from.temperature;
            }
        } catch (Exception ignored) {
            // На цьому етапі без шуму: якщо конфіг битий — працюємо з дефолтами.
        }

        return cfg;
    }

    private static void applySystemOverrides(Config cfg) {
        // system properties override
        String mode = System.getProperty("ai.llm.mode");
        if (mode != null && !mode.isBlank()) {
            try {
                cfg.mode = Mode.valueOf(mode.trim().toUpperCase());
            } catch (Exception ignored) { }
        }

        String baseUrl = System.getProperty("ai.llm.baseUrl");
        if (baseUrl != null && !baseUrl.isBlank()) cfg.baseUrl = baseUrl.trim();

        String model = System.getProperty("ai.llm.model");
        if (model != null && !model.isBlank()) cfg.model = model.trim();

        String timeout = System.getProperty("ai.llm.timeoutSec");
        if (timeout != null && !timeout.isBlank()) {
            try {
                int t = Integer.parseInt(timeout.trim());
                if (t > 0) cfg.timeoutSec = t;
            } catch (Exception ignored) { }
        }

        String apiKey = System.getProperty("ai.llm.apiKey");
        if (apiKey != null) cfg.apiKey = apiKey; // може бути пустий
    }

    private static String normalizeBase(String baseUrl) {
        String b = (baseUrl == null) ? "" : baseUrl.trim();
        if (b.endsWith("/")) b = b.substring(0, b.length() - 1);
        return b;
    }

    private static String trimForLog(String s) {
        if (s == null) return "";
        String t = s.replace("\n", "\\n");
        if (t.length() > 400) return t.substring(0, 400) + "...";
        return t;
    }

    public static final class LocalLlmException extends RuntimeException {
        public LocalLlmException(String message) { super(message); }
        public LocalLlmException(String message, Throwable cause) { super(message, cause); }
    }
}
