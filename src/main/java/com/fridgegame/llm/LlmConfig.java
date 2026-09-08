package com.fridgegame.llm;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.function.UnaryOperator;

/**
 * Where to reach the local LLM, and with what credentials.
 *
 * <p>Resolved in precedence order, first non-blank value winning:
 * <ol>
 *   <li>environment: {@code LLM_BASE_URL}, {@code LLM_MODEL}, {@code LLM_API_KEY}</li>
 *   <li>a {@code llm.properties} file in the working directory (gitignored)</li>
 *   <li>built-in defaults — for the URL and model only, <b>never</b> for the key</li>
 * </ol>
 *
 * <p>No API key is baked into the source or the jar. The DGX endpoint currently accepts
 * unauthenticated requests, so a missing key is a supported configuration rather than an
 * error: the {@code Authorization} header is simply omitted.
 */
public record LlmConfig(String baseUrl, String model, String apiKey) {

    public static final String DEFAULT_BASE_URL = "http://172.23.35.112:8001";
    public static final String DEFAULT_MODEL = "qwen3.6-35b";

    /** File consulted when the environment says nothing. Not committed. */
    public static final String PROPERTIES_FILE = "llm.properties";

    private static final String ENV_BASE_URL = "LLM_BASE_URL";
    private static final String ENV_MODEL = "LLM_MODEL";
    private static final String ENV_API_KEY = "LLM_API_KEY";

    public LlmConfig {
        baseUrl = trimTrailingSlash(blankToNull(baseUrl) == null ? DEFAULT_BASE_URL : baseUrl);
        model = blankToNull(model) == null ? DEFAULT_MODEL : model;
        apiKey = blankToNull(apiKey);
    }

    /** Reads the real environment and working directory. */
    public static LlmConfig load() {
        return load(System::getenv, loadProperties(Path.of(PROPERTIES_FILE)));
    }

    /** Testable core: {@code env} stands in for {@link System#getenv}. */
    static LlmConfig load(UnaryOperator<String> env, Properties props) {
        return new LlmConfig(
                pick(env.apply(ENV_BASE_URL), props.getProperty("llm.baseUrl")),
                pick(env.apply(ENV_MODEL), props.getProperty("llm.model")),
                pick(env.apply(ENV_API_KEY), props.getProperty("llm.apiKey")));
    }

    static Properties loadProperties(Path path) {
        Properties props = new Properties();
        if (!Files.isReadable(path)) {
            return props;
        }
        try (InputStream in = Files.newInputStream(path)) {
            props.load(in);
        } catch (IOException e) {
            // An unreadable config file must not stop the game from starting; the
            // opponent just falls back to the local bot.
            return new Properties();
        }
        return props;
    }

    /** Whether to send an {@code Authorization} header at all. */
    public boolean hasApiKey() {
        return apiKey != null;
    }

    public URI chatCompletionsUri() {
        return URI.create(baseUrl + "/v1/chat/completions");
    }

    /** Cheap endpoint used for the startup reachability check. */
    public URI modelsUri() {
        return URI.create(baseUrl + "/v1/models");
    }

    /** Safe for logs and the HUD — never includes the key. */
    public String describe() {
        return model + " @ " + baseUrl;
    }

    private static String pick(String first, String second) {
        String value = blankToNull(first);
        return value != null ? value : blankToNull(second);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String trimTrailingSlash(String url) {
        String trimmed = url.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }
}
