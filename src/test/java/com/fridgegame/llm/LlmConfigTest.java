package com.fridgegame.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LlmConfigTest {

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
    void fallsBackToBuiltInDefaultsWhenNothingIsConfigured() {
        LlmConfig config = LlmConfig.load(env(Map.of()), new Properties());

        assertEquals(LlmConfig.DEFAULT_BASE_URL, config.baseUrl());
        assertEquals(LlmConfig.DEFAULT_MODEL, config.model());
        assertNull(config.apiKey(), "there must be no default API key");
        assertFalse(config.hasApiKey());
    }

    @Test
    void thePropertiesFileIsUsedWhenTheEnvironmentIsSilent() {
        LlmConfig config = LlmConfig.load(env(Map.of()), props(
                "llm.baseUrl", "http://box:9000",
                "llm.model", "from-file",
                "llm.apiKey", "sk-file"));

        assertEquals("http://box:9000", config.baseUrl());
        assertEquals("from-file", config.model());
        assertEquals("sk-file", config.apiKey());
        assertTrue(config.hasApiKey());
    }

    @Test
    void theEnvironmentBeatsThePropertiesFile() {
        LlmConfig config = LlmConfig.load(
                env(Map.of(
                        "LLM_BASE_URL", "http://env:1234",
                        "LLM_MODEL", "from-env",
                        "LLM_API_KEY", "sk-env")),
                props(
                        "llm.baseUrl", "http://box:9000",
                        "llm.model", "from-file",
                        "llm.apiKey", "sk-file"));

        assertEquals("http://env:1234", config.baseUrl());
        assertEquals("from-env", config.model());
        assertEquals("sk-env", config.apiKey());
    }

    @Test
    void eachSettingFallsBackIndependently() {
        LlmConfig config = LlmConfig.load(
                env(Map.of("LLM_MODEL", "from-env")),
                props("llm.baseUrl", "http://box:9000"));

        assertEquals("http://box:9000", config.baseUrl(), "file value survives");
        assertEquals("from-env", config.model(), "env value wins");
        assertNull(config.apiKey(), "unset everywhere");
    }

    @Test
    void blankValuesAreTreatedAsUnset() {
        LlmConfig config = LlmConfig.load(
                env(Map.of("LLM_BASE_URL", "   ", "LLM_API_KEY", "")),
                props("llm.baseUrl", "http://box:9000"));

        assertEquals("http://box:9000", config.baseUrl(), "blank env must not mask the file");
        assertFalse(config.hasApiKey());
    }

    @Test
    void aTrailingSlashOnTheBaseUrlDoesNotDoubleUpInPaths() {
        LlmConfig config = new LlmConfig("http://box:9000/", null, null);

        assertEquals("http://box:9000/v1/chat/completions", config.chatCompletionsUri().toString());
        assertEquals("http://box:9000/v1/models", config.modelsUri().toString());
    }

    @Test
    void endpointPathsMatchTheOpenAiCompatibleApi() {
        LlmConfig config = new LlmConfig("http://172.23.35.112:8001", "qwen3.6-35b", null);

        assertEquals("http://172.23.35.112:8001/v1/chat/completions",
                config.chatCompletionsUri().toString());
        assertEquals("http://172.23.35.112:8001/v1/models", config.modelsUri().toString());
    }

    @Test
    void describeNeverLeaksTheApiKey() {
        LlmConfig config = new LlmConfig("http://box:9000", "some-model", "sk-supersecret");

        assertEquals("some-model @ http://box:9000", config.describe());
        assertFalse(config.describe().contains("sk-supersecret"));
    }

    @Test
    void aMissingPropertiesFileYieldsEmptyPropertiesRatherThanThrowing() {
        Properties loaded = LlmConfig.loadProperties(Path.of("definitely-not-here.properties"));

        assertTrue(loaded.isEmpty());
    }

    @Test
    void aRealPropertiesFileIsRead(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("llm.properties");
        Files.writeString(file, "llm.model=disk-model\nllm.baseUrl=http://disk:1\n");

        Properties loaded = LlmConfig.loadProperties(file);

        assertEquals("disk-model", loaded.getProperty("llm.model"));
        assertEquals("http://disk:1", loaded.getProperty("llm.baseUrl"));
    }
}
