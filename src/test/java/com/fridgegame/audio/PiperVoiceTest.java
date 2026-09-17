package com.fridgegame.audio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fridgegame.llm.TtsConfig;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Guards the request shape, the server command line, and the promise that a missing or
 * broken Piper degrades to silence.
 *
 * <p><b>No network, no JavaFX, no spawned server.</b> The two cases that do touch
 * {@code java.net.http} aim at the discard port, which refuses instantly and
 * deterministically — the same trick {@code LlmClientTest} uses.
 */
class PiperVoiceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static TtsConfig config(boolean spawn, String baseUrl) {
        return new TtsConfig(true, baseUrl, "en_US-lessac-medium", spawn, "python", "/voices", null);
    }

    @Test
    void theRequestCarriesOnlyTheText() throws Exception {
        JsonNode body = MAPPER.readTree(PiperVoice.body("That shelf is for dairy."));

        assertEquals("That shelf is for dairy.", body.get("text").asText());
        assertFalse(body.has("voice"),
                "the voice is fixed when the server starts, and an unknown one here is "
                        + "silently accepted rather than rejected");
    }

    @Test
    void theServerIsStartedThroughTheInterpreterThatOwnsThePackage() {
        List<String> command = PiperVoice.command(config(true, "http://127.0.0.1:5000"));

        assertEquals("python", command.get(0));
        assertEquals(List.of("-m", "piper.http_server"), command.subList(1, 3));
        assertTrue(command.containsAll(List.of("-m", "en_US-lessac-medium")));
        assertTrue(command.containsAll(List.of("--data-dir", "/voices")));
        assertTrue(command.containsAll(List.of("--port", "5000")));
    }

    @Test
    void theServerBindsLoopbackOnly() {
        List<String> command = PiperVoice.command(config(true, "http://127.0.0.1:5000"));

        int host = command.indexOf("--host");
        assertTrue(host >= 0, "the host must be pinned rather than left to Piper's default");
        assertEquals("127.0.0.1", command.get(host + 1),
                "an unauthenticated synthesis endpoint must not be offered to the network");
    }

    @Test
    void tempoReachesTheServerOnlyWhenAskedFor() {
        assertFalse(PiperVoice.command(config(true, "http://127.0.0.1:5000"))
                .contains("--length-scale"));

        TtsConfig tuned = new TtsConfig(
                true, "http://127.0.0.1:5000", "en_US-lessac-medium", true, "python", "/v", 0.9);
        List<String> command = PiperVoice.command(tuned);
        assertEquals("0.9", command.get(command.indexOf("--length-scale") + 1));
    }

    @Test
    void anAbsentServerIsSilenceRatherThanAFailure() throws Exception {
        // Port 9 is the discard port: the connection is refused fast and deterministically.
        RivalVoice voice = PiperVoice.start(
                        config(false, "http://127.0.0.1:9"),
                        Duration.ofMillis(500),
                        Duration.ofMillis(800))
                .get();

        assertSame(RivalVoice.SILENT, voice, "no voice is an expected state, not an error");
    }

    @Test
    void anUnstartableInterpreterIsSilenceRatherThanAFailure() throws Exception {
        TtsConfig missingPython = new TtsConfig(
                true, "http://127.0.0.1:9", "en_US-lessac-medium", true,
                "no-such-python-executable", "/voices", null);

        RivalVoice voice = PiperVoice.start(
                missingPython, Duration.ofMillis(500), Duration.ofMillis(800)).get();

        assertSame(RivalVoice.SILENT, voice,
                "a machine without Piper installed still plays the game");
    }

    @Test
    void spawningIsDeclinedWhenNothingAnswersAndTheCallerSaidNotTo() throws Exception {
        RivalVoice voice = PiperVoice.start(
                        config(false, "http://127.0.0.1:9"),
                        Duration.ofMillis(500),
                        Duration.ofMillis(800))
                .get();

        assertSame(RivalVoice.SILENT, voice,
                "tts.spawn=false means connect or stay quiet, never start a server");
    }

    @Test
    void captionsAreFlattenedToOneLineAndCapped() {
        assertEquals("one two", PiperVoice.sanitize("one\r\ntwo"));
        assertEquals("trimmed", PiperVoice.sanitize("  trimmed  "));
        assertEquals("", PiperVoice.sanitize(null));
        assertEquals(400, PiperVoice.sanitize("x".repeat(500)).length(),
                "a runaway line must not become a monologue the player cannot skip");
    }
}
