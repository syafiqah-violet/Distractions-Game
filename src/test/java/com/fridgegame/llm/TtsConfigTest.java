package com.fridgegame.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class TtsConfigTest {

    private static final String LLM_HOST = "http://172.23.35.112:8001";

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

    @Test
    void fallsBackToTheLlmHostAndBuiltInDefaults() {
        TtsConfig config = TtsConfig.load(env(Map.of()), new Properties(), LLM_HOST);

        assertEquals(LLM_HOST, config.baseUrl(), "a TTS server would most likely be there");
        assertEquals(TtsConfig.DEFAULT_MODEL, config.model());
        assertEquals(TtsConfig.DEFAULT_VOICE, config.voice());
        assertNull(config.apiKey(), "there must be no default API key");
        assertFalse(config.hasApiKey());
    }

    @Test
    void isOnByDefaultSoAnUnconfiguredPlayerStillGetsAVoice() {
        assertTrue(TtsConfig.load(env(Map.of()), new Properties(), LLM_HOST).enabled());
    }

    @Test
    void onlyAnExplicitFalseTurnsTheVoiceOff() {
        assertFalse(TtsConfig.load(
                env(Map.of("TTS_ENABLED", "false")), new Properties(), LLM_HOST).enabled());
        assertFalse(TtsConfig.load(
                env(Map.of("TTS_ENABLED", "FALSE")), new Properties(), LLM_HOST).enabled(),
                "the check is case-insensitive");
        assertTrue(TtsConfig.load(
                env(Map.of("TTS_ENABLED", "yes")), new Properties(), LLM_HOST).enabled(),
                "anything that is not \"false\" leaves it on");
    }

    @Test
    void thePropertiesFileIsUsedWhenTheEnvironmentIsSilent() {
        TtsConfig config = TtsConfig.load(env(Map.of()), props(
                "tts.baseUrl", "http://box:8880",
                "tts.model", "kokoro",
                "tts.voice", "af_sky",
                "tts.apiKey", "sk-file"), LLM_HOST);

        assertEquals("http://box:8880", config.baseUrl());
        assertEquals("kokoro", config.model());
        assertEquals("af_sky", config.voice());
        assertEquals("sk-file", config.apiKey());
        assertTrue(config.hasApiKey());
    }

    @Test
    void theEnvironmentBeatsThePropertiesFile() {
        TtsConfig config = TtsConfig.load(
                env(Map.of(
                        "TTS_BASE_URL", "http://env:1234",
                        "TTS_MODEL", "from-env",
                        "TTS_VOICE", "from-env-voice")),
                props(
                        "tts.baseUrl", "http://box:8880",
                        "tts.model", "from-file",
                        "tts.voice", "from-file-voice"),
                LLM_HOST);

        assertEquals("http://env:1234", config.baseUrl());
        assertEquals("from-env", config.model());
        assertEquals("from-env-voice", config.voice());
    }

    @Test
    void eachSettingFallsBackIndependently() {
        TtsConfig config = TtsConfig.load(
                env(Map.of("TTS_VOICE", "from-env-voice")),
                props("tts.baseUrl", "http://box:8880"),
                LLM_HOST);

        assertEquals("http://box:8880", config.baseUrl(), "file value survives");
        assertEquals("from-env-voice", config.voice());
        assertEquals(TtsConfig.DEFAULT_MODEL, config.model(), "and the rest default");
    }

    @Test
    void blankValuesAreTreatedAsAbsent() {
        TtsConfig config = TtsConfig.load(
                env(Map.of("TTS_MODEL", "   ", "TTS_VOICE", "")),
                new Properties(),
                LLM_HOST);

        assertEquals(TtsConfig.DEFAULT_MODEL, config.model());
        assertEquals(TtsConfig.DEFAULT_VOICE, config.voice());
    }

    @Test
    void aTrailingSlashNeverProducesADoubleSlashInTheEndpoint() {
        TtsConfig config = TtsConfig.load(
                env(Map.of("TTS_BASE_URL", "http://box:8880/")), new Properties(), LLM_HOST);

        assertEquals("http://box:8880", config.baseUrl());
        assertEquals("http://box:8880/v1/audio/speech", config.speechUri().toString());
    }

    @Test
    void fallsBackToTheBuiltInHostWhenEvenTheLlmUrlIsMissing() {
        TtsConfig config = TtsConfig.load(env(Map.of()), new Properties(), null);

        assertEquals(LlmConfig.DEFAULT_BASE_URL, config.baseUrl());
    }

    @Test
    void describeNeverLeaksTheKey() {
        TtsConfig config = TtsConfig.load(
                env(Map.of("TTS_API_KEY", "sk-secret-value")), new Properties(), LLM_HOST);

        assertFalse(config.describe().contains("sk-secret-value"));
    }
}
