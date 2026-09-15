package com.fridgegame.llm;

import java.net.URI;
import java.nio.file.Path;
import java.util.Properties;
import java.util.function.UnaryOperator;

/**
 * Where to reach a text-to-speech server, and with what voice.
 *
 * <p>Deliberately shaped like {@link LlmConfig} — same precedence, same helpers, same
 * refusal to bake a key into the jar:
 * <ol>
 *   <li>environment: {@code TTS_ENABLED}, {@code TTS_BASE_URL}, {@code TTS_MODEL},
 *       {@code TTS_VOICE}, {@code TTS_API_KEY}</li>
 *   <li>the same {@code llm.properties} file, under {@code tts.*} keys</li>
 *   <li>built-in defaults — never for the key</li>
 * </ol>
 *
 * <p>It shares {@code llm.properties} rather than adding a second file so that the
 * gitignore entry protecting the one already covers the other.
 *
 * <p>The base URL defaults to whatever the LLM is using. That is a guess, but an informed
 * one: if a TTS server is ever stood up for this game it will most likely be on the same
 * box that already serves the model. At the time of writing that host answers 404 on
 * {@code /v1/audio/speech}, which is exactly what the startup probe is for.
 */
public record TtsConfig(boolean enabled, String baseUrl, String model, String voice, String apiKey) {

    public static final String DEFAULT_MODEL = "tts-1";
    public static final String DEFAULT_VOICE = "alloy";

    private static final String ENV_ENABLED = "TTS_ENABLED";
    private static final String ENV_BASE_URL = "TTS_BASE_URL";
    private static final String ENV_MODEL = "TTS_MODEL";
    private static final String ENV_VOICE = "TTS_VOICE";
    private static final String ENV_API_KEY = "TTS_API_KEY";

    public TtsConfig {
        baseUrl = trimTrailingSlash(baseUrl == null ? LlmConfig.DEFAULT_BASE_URL : baseUrl);
        model = blankToNull(model) == null ? DEFAULT_MODEL : model.trim();
        voice = blankToNull(voice) == null ? DEFAULT_VOICE : voice.trim();
        apiKey = blankToNull(apiKey);
    }

    /** Reads the real environment and working directory, defaulting to the LLM's host. */
    public static TtsConfig load(String fallbackBaseUrl) {
        return load(System::getenv,
                LlmConfig.loadProperties(Path.of(LlmConfig.PROPERTIES_FILE)),
                fallbackBaseUrl);
    }

    /** Testable core: {@code env} stands in for {@link System#getenv}. */
    static TtsConfig load(UnaryOperator<String> env, Properties props, String fallbackBaseUrl) {
        String configuredUrl = pick(env.apply(ENV_BASE_URL), props.getProperty("tts.baseUrl"));
        return new TtsConfig(
                // Absent means on. Only an explicit "false" turns the voice off, so a
                // player who has never heard of this setting still gets a talking rival.
                !"false".equalsIgnoreCase(
                        pick(env.apply(ENV_ENABLED), props.getProperty("tts.enabled"))),
                configuredUrl != null ? configuredUrl : blankToNull(fallbackBaseUrl),
                pick(env.apply(ENV_MODEL), props.getProperty("tts.model")),
                pick(env.apply(ENV_VOICE), props.getProperty("tts.voice")),
                pick(env.apply(ENV_API_KEY), props.getProperty("tts.apiKey")));
    }

    /** Whether to send an {@code Authorization} header at all. */
    public boolean hasApiKey() {
        return apiKey != null;
    }

    /** The OpenAI-compatible synthesis endpoint. */
    public URI speechUri() {
        return URI.create(baseUrl + "/v1/audio/speech");
    }

    /** Safe for logs — never includes the key. */
    public String describe() {
        return voice + "/" + model + " @ " + baseUrl;
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
