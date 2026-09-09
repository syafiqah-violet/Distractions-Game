package com.fridgegame.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Minimal client for the local vLLM OpenAI-compatible endpoint.
 *
 * <p>Two request details are load-bearing and were established by probing the live
 * endpoint rather than assumed:
 *
 * <ul>
 *   <li><b>{@code chat_template_kwargs: {enable_thinking: false}}</b> — Qwen3.6 is a
 *       reasoning model. Left on, it spends its whole token budget in a separate
 *       {@code reasoning} field and returns {@code content: null} with
 *       {@code finish_reason: "length"}, taking ~4s to say nothing at all. Off, it
 *       answers in ~650ms.</li>
 *   <li><b>{@code response_format: json_schema}</b> — vLLM guided decoding, so the reply
 *       is schema-valid by construction and needs no prose stripping.</li>
 * </ul>
 *
 * <p>Every call is non-blocking; nothing here may be invoked in a way that stalls the
 * JavaFX thread.
 */
public final class LlmClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final double TEMPERATURE = 0.2;

    private final LlmConfig config;
    private final HttpClient http;
    private final Duration requestTimeout;

    public LlmClient(LlmConfig config, Duration requestTimeout) {
        this.config = config;
        this.requestTimeout = requestTimeout;
        this.http = HttpClient.newBuilder()
                // HTTP/1.1 is not a preference, it is required. Java's default is HTTP/2,
                // which over plaintext means an h2c upgrade attempt; vLLM's uvicorn server
                // mishandles that and drops the request body, so every POST comes back as
                // "400 Field required: body". curl works by default because it sends 1.1.
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    public LlmConfig config() {
        return config;
    }

    /**
     * Outcome of a reachability probe.
     *
     * @param online whether the endpoint answered
     * @param detail short human-readable reason, shown in the UI — never contains the key
     */
    public record Reachability(boolean online, String detail) {
    }

    /**
     * Startup reachability probe against {@code /v1/models}.
     *
     * <p>Never completes exceptionally — an unreachable endpoint is an expected state,
     * not an error, so this reports {@code online = false} and the caller keeps the local
     * bot. The reason travels with it so a misconfigured URL or a refused connection is
     * visible on screen instead of being indistinguishable from "the box is off".
     */
    public CompletableFuture<Reachability> reachable() {
        HttpRequest request = authorized(HttpRequest.newBuilder(config.modelsUri()))
                .timeout(Duration.ofSeconds(6))
                .GET()
                .build();
        return http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> response.statusCode() == 200
                        ? new Reachability(true, "LLM online")
                        : new Reachability(false, "HTTP " + response.statusCode()))
                .exceptionally(e -> new Reachability(false, rootMessage(e)));
    }

    /**
     * A caption a player can act on, rather than the raw exception name.
     *
     * <p>The usual failures surface as {@code ClosedChannelException} or
     * {@code ConnectException} depending on platform, neither of which means anything to
     * someone who just wants to know whether to start the DGX box.
     */
    private static String rootMessage(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        if (cause instanceof java.net.http.HttpConnectTimeoutException
                || cause instanceof java.net.SocketTimeoutException) {
            return "connection timed out";
        }
        if (cause instanceof java.nio.channels.ClosedChannelException
                || cause instanceof java.net.ConnectException) {
            return "endpoint unreachable";
        }
        // HttpClient reports a bad hostname as UnresolvedAddressException, not the
        // UnknownHostException you would expect from the older socket APIs.
        if (cause instanceof java.nio.channels.UnresolvedAddressException
                || cause instanceof java.net.UnknownHostException) {
            return "unknown host";
        }
        String message = cause.getMessage();
        if (message == null || message.isBlank()) {
            message = cause.getClass().getSimpleName();
        }
        return message.length() <= 40 ? message : message.substring(0, 40) + "...";
    }

    /**
     * Sends one schema-constrained completion.
     *
     * @param schema JSON Schema the reply must satisfy, enforced by guided decoding
     * @return the assistant's {@code content}, or a failed future explaining what went wrong
     */
    public CompletableFuture<String> chat(
            String systemPrompt, String userPrompt, JsonNode schema, String schemaName, int maxTokens) {

        HttpRequest request;
        try {
            request = authorized(HttpRequest.newBuilder(config.chatCompletionsUri()))
                    .timeout(requestTimeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            body(systemPrompt, userPrompt, schema, schemaName, maxTokens)))
                    .build();
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }

        return http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(LlmClient::extractContent);
    }

    /** Binds {@code schema} so callers can hand the result around as a plain {@link JsonChat}. */
    public JsonChat bind(JsonNode schema, String schemaName, int maxTokens) {
        return (system, user) -> chat(system, user, schema, schemaName, maxTokens);
    }

    private HttpRequest.Builder authorized(HttpRequest.Builder builder) {
        if (config.hasApiKey()) {
            builder.header("Authorization", "Bearer " + config.apiKey());
        }
        return builder;
    }

    /** Visible for testing: the wire protocol must stay HTTP/1.1. See the constructor. */
    HttpClient.Version httpVersion() {
        return http.version();
    }

    /** Visible for testing: the exact JSON posted to {@code /v1/chat/completions}. */
    String body(
            String systemPrompt, String userPrompt, JsonNode schema, String schemaName, int maxTokens) {

        ObjectNode root = MAPPER.createObjectNode();
        root.put("model", config.model());
        root.put("max_tokens", maxTokens);
        root.put("temperature", TEMPERATURE);

        // Without this the model returns content:null and reasons until it runs out of tokens.
        root.putObject("chat_template_kwargs").put("enable_thinking", false);

        ObjectNode jsonSchema = root.putObject("response_format")
                .put("type", "json_schema")
                .putObject("json_schema");
        jsonSchema.put("name", schemaName);
        jsonSchema.put("strict", true);
        jsonSchema.set("schema", schema);

        var messages = root.putArray("messages");
        messages.addObject().put("role", "system").put("content", systemPrompt);
        messages.addObject().put("role", "user").put("content", userPrompt);

        try {
            return MAPPER.writeValueAsString(root);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("Could not serialise the chat request", e);
        }
    }

    /** Pulls {@code choices[0].message.content} out, or explains why there isn't one. */
    private static String extractContent(HttpResponse<String> response) {
        if (response.statusCode() != 200) {
            throw new IllegalStateException(
                    "LLM returned HTTP " + response.statusCode() + ": " + snippet(response.body()));
        }
        JsonNode message;
        try {
            message = MAPPER.readTree(response.body()).path("choices").path(0).path("message");
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("LLM reply was not JSON: " + snippet(response.body()), e);
        }

        JsonNode content = message.get("content");
        if (content == null || content.isNull() || content.asText().isBlank()) {
            JsonNode reasoning = message.get("reasoning");
            if (reasoning != null && !reasoning.isNull()) {
                throw new IllegalStateException(
                        "LLM reasoned instead of answering — is enable_thinking:false still set?");
            }
            throw new IllegalStateException("LLM returned an empty reply");
        }
        return content.asText();
    }

    private static String snippet(String body) {
        if (body == null) {
            return "<no body>";
        }
        return body.length() <= 200 ? body : body.substring(0, 200) + "...";
    }
}
