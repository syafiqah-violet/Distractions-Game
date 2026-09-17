package com.fridgegame.llm;

import java.net.URI;
import java.nio.file.Path;
import java.util.Properties;
import java.util.function.UnaryOperator;

/**
 * Where to reach the Piper speech server, and with what voice.
 *
 * <p>Deliberately shaped like {@link LlmConfig} — same precedence, same helpers:
 * <ol>
 *   <li>environment: {@code TTS_ENABLED}, {@code TTS_BASE_URL}, {@code TTS_VOICE},
 *       {@code TTS_SPAWN}, {@code TTS_PYTHON}, {@code TTS_DATA_DIR},
 *       {@code TTS_LENGTH_SCALE}</li>
 *   <li>the same {@code llm.properties} file, under {@code tts.*} keys</li>
 *   <li>built-in defaults</li>
 * </ol>
 *
 * <p>It shares {@code llm.properties} rather than adding a second file so that the
 * gitignore entry protecting the one already covers the other.
 *
 * <p>There is no API key here, and that is the point of the whole exercise: Piper runs on
 * this machine, for free, with no account and no per-minute meter. The defaults describe a
 * server this game starts for itself on the loopback interface, so an unconfigured player
 * gets a talking rival without knowing any of this exists.
 */
public record TtsConfig(
        boolean enabled,
        String baseUrl,
        String voice,
        boolean spawn,
        String python,
        String dataDir,
        Double lengthScale) {

    /** Piper's own default port, and nothing in this project competes for it. */
    public static final String DEFAULT_BASE_URL = "http://127.0.0.1:5000";

    /**
     * Piper's reference voice, and the one every piece of its documentation uses. Medium
     * quality is the latency/quality sweet spot: {@code high} is slower to synthesize for
     * a difference nobody hears under game audio, {@code low} gives back the robot.
     */
    public static final String DEFAULT_VOICE = "en_US-lessac-medium";

    /** Resolved on PATH. Overridable because a venv or the {@code py} launcher is common. */
    public static final String DEFAULT_PYTHON = "python";

    private static final String ENV_ENABLED = "TTS_ENABLED";
    private static final String ENV_BASE_URL = "TTS_BASE_URL";
    private static final String ENV_VOICE = "TTS_VOICE";
    private static final String ENV_SPAWN = "TTS_SPAWN";
    private static final String ENV_PYTHON = "TTS_PYTHON";
    private static final String ENV_DATA_DIR = "TTS_DATA_DIR";
    private static final String ENV_LENGTH_SCALE = "TTS_LENGTH_SCALE";

    public TtsConfig {
        baseUrl = trimTrailingSlash(blankToNull(baseUrl) == null ? DEFAULT_BASE_URL : baseUrl);
        voice = blankToNull(voice) == null ? DEFAULT_VOICE : voice.trim();
        python = blankToNull(python) == null ? DEFAULT_PYTHON : python.trim();
        dataDir = blankToNull(dataDir) == null ? defaultDataDir() : dataDir.trim();
    }

    /**
     * Where the voice models live.
     *
     * <p>Under the home directory rather than the working directory, which is what Piper
     * itself defaults to: that default drops a 60MB {@code .onnx} into whatever folder the
     * game was launched from — for this project, straight into the repository root.
     */
    private static String defaultDataDir() {
        return Path.of(System.getProperty("user.home", "."), ".piper-voices").toString();
    }

    /** Reads the real environment and working directory. */
    public static TtsConfig load() {
        return load(System::getenv, LlmConfig.loadProperties(Path.of(LlmConfig.PROPERTIES_FILE)));
    }

    /** Testable core: {@code env} stands in for {@link System#getenv}. */
    static TtsConfig load(UnaryOperator<String> env, Properties props) {
        return new TtsConfig(
                // Absent means on. Only an explicit "false" turns the voice off, so a
                // player who has never heard of this setting still gets a talking rival.
                !isFalse(pick(env.apply(ENV_ENABLED), props.getProperty("tts.enabled"))),
                pick(env.apply(ENV_BASE_URL), props.getProperty("tts.baseUrl")),
                pick(env.apply(ENV_VOICE), props.getProperty("tts.voice")),
                !isFalse(pick(env.apply(ENV_SPAWN), props.getProperty("tts.spawn"))),
                pick(env.apply(ENV_PYTHON), props.getProperty("tts.python")),
                pick(env.apply(ENV_DATA_DIR), props.getProperty("tts.dataDir")),
                toDouble(pick(env.apply(ENV_LENGTH_SCALE), props.getProperty("tts.lengthScale"))));
    }

    /** Where captions are sent to be spoken. */
    public URI synthesizeUri() {
        return URI.create(baseUrl + "/synthesize");
    }

    /** Answered only once the voice model is loaded, which is what makes it a readiness check. */
    public URI infoUri() {
        return URI.create(baseUrl + "/info");
    }

    /**
     * The port to start a spawned server on, taken from the base URL so the two can never
     * disagree. Falls back to Piper's default when the URL names no port.
     */
    public int port() {
        int port = URI.create(baseUrl).getPort();
        return port == -1 ? 5000 : port;
    }

    /** Short description for the console log. */
    public String describe() {
        return voice + " @ " + baseUrl;
    }

    /** Absent, blank and anything that is not "false" all mean on. */
    private static boolean isFalse(String value) {
        return "false".equalsIgnoreCase(blankToNull(value));
    }

    /**
     * Unparseable means unset rather than fatal. A typo in a tuning knob should leave the
     * server picking its own tempo, not stop the game from starting.
     */
    private static Double toDouble(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Double.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
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
