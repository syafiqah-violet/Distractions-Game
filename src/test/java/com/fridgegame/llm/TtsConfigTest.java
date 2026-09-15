package com.fridgegame.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;

/**
 * Guards the settings that decide whether the rival can speak.
 *
 * <p>No network and no process: every case here is pure resolution of environment over
 * properties over defaults.
 */
class TtsConfigTest {

    private static Properties props(String... keyValues) {
        Properties p = new Properties();
        for (int i = 0; i < keyValues.length; i += 2) {
            p.setProperty(keyValues[i], keyValues[i + 1]);
        }
        return p;
    }

    private static java.util.function.UnaryOperator<String> env(Map<String, String> values) {
        return values::get;
    }

    private static TtsConfig defaults() {
        return TtsConfig.load(env(Map.of()), new Properties());
    }

    @Test
    void anUnconfiguredPlayerGetsALocalTalkingRival() {
        TtsConfig config = defaults();

        assertTrue(config.enabled(), "the voice is on unless someone turns it off");
        assertTrue(config.spawn(), "and the game starts the server itself");
        assertEquals(TtsConfig.DEFAULT_BASE_URL, config.baseUrl());
        assertEquals(TtsConfig.DEFAULT_VOICE, config.voice());
        assertEquals(TtsConfig.DEFAULT_PYTHON, config.python());
        assertNull(config.lengthScale(), "tempo is the server's business until asked otherwise");
    }

    @Test
    void theDefaultBaseUrlIsLoopback() {
        assertTrue(defaults().baseUrl().startsWith("http://127.0.0.1"),
                "an unauthenticated synthesis endpoint must not be offered to the network");
    }

    @Test
    void voiceModelsDefaultOutsideTheWorkingDirectory() {
        String dataDir = defaults().dataDir();

        assertFalse(dataDir.isBlank());
        assertTrue(dataDir.endsWith(".piper-voices"),
                "Piper's own default is the working directory, which would drop a 60MB "
                        + "model into the repository root");
    }

    @Test
    void onlyAnExplicitFalseTurnsTheVoiceOff() {
        assertFalse(TtsConfig.load(
                env(Map.of("TTS_ENABLED", "false")), new Properties()).enabled());
        assertFalse(TtsConfig.load(
                env(Map.of("TTS_ENABLED", "FALSE")), new Properties()).enabled(),
                "the check is case-insensitive");
        assertTrue(TtsConfig.load(
                env(Map.of("TTS_ENABLED", "yes")), new Properties()).enabled(),
                "anything that is not \"false\" leaves it on");
    }

    @Test
    void spawningCanBeDeclinedForAnExternallyManagedServer() {
        assertFalse(TtsConfig.load(env(Map.of("TTS_SPAWN", "false")), new Properties()).spawn());
        assertFalse(TtsConfig.load(env(Map.of()), props("tts.spawn", "false")).spawn());
        assertTrue(TtsConfig.load(env(Map.of("TTS_SPAWN", "true")), new Properties()).spawn());
    }

    @Test
    void thePropertiesFileIsUsedWhenTheEnvironmentIsSilent() {
        TtsConfig config = TtsConfig.load(env(Map.of()), props(
                "tts.baseUrl", "http://127.0.0.1:5100",
                "tts.voice", "en_GB-alba-medium",
                "tts.python", "py",
                "tts.dataDir", "D:\\voices"));

        assertEquals("http://127.0.0.1:5100", config.baseUrl());
        assertEquals("en_GB-alba-medium", config.voice());
        assertEquals("py", config.python());
        assertEquals("D:\\voices", config.dataDir());
    }

    @Test
    void theEnvironmentWinsOverTheFile() {
        TtsConfig config = TtsConfig.load(
                env(Map.of("TTS_VOICE", "en_US-amy-medium")),
                props("tts.voice", "en_GB-alba-medium"));

        assertEquals("en_US-amy-medium", config.voice());
    }

    @Test
    void blankSettingsFallBackRatherThanOverride() {
        TtsConfig config = TtsConfig.load(
                env(Map.of("TTS_VOICE", "   ", "TTS_BASE_URL", "")), new Properties());

        assertEquals(TtsConfig.DEFAULT_VOICE, config.voice());
        assertEquals(TtsConfig.DEFAULT_BASE_URL, config.baseUrl());
    }

    @Test
    void aTrailingSlashDoesNotProduceADoubledPath() {
        TtsConfig config = TtsConfig.load(
                env(Map.of("TTS_BASE_URL", "http://127.0.0.1:5000/")), new Properties());

        assertEquals("http://127.0.0.1:5000/synthesize", config.synthesizeUri().toString());
        assertEquals("http://127.0.0.1:5000/info", config.infoUri().toString());
    }

    @Test
    void theSpawnedPortIsTakenFromTheBaseUrlSoTheyCannotDisagree() {
        assertEquals(5100, TtsConfig.load(
                env(Map.of("TTS_BASE_URL", "http://127.0.0.1:5100")), new Properties()).port());
        assertEquals(5000, TtsConfig.load(
                env(Map.of("TTS_BASE_URL", "http://127.0.0.1")), new Properties()).port(),
                "a URL naming no port means Piper's default");
    }

    @Test
    void tempoIsReadWhenNumeric() {
        assertEquals(0.9,
                TtsConfig.load(env(Map.of("TTS_LENGTH_SCALE", "0.9")), new Properties())
                        .lengthScale());
    }

    @Test
    void aMistypedTempoIsIgnoredRatherThanFatal() {
        assertNull(TtsConfig.load(env(Map.of("TTS_LENGTH_SCALE", "fast")), new Properties())
                        .lengthScale(),
                "a typo in a tuning knob must not stop the game from starting");
    }

    @Test
    void describeNamesTheVoiceAndWhereItIs() {
        assertEquals(TtsConfig.DEFAULT_VOICE + " @ " + TtsConfig.DEFAULT_BASE_URL,
                defaults().describe());
    }
}
