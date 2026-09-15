package com.fridgegame.audio;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fridgegame.llm.LlmLog;
import com.fridgegame.llm.TtsConfig;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.media.AudioClip;

/**
 * The rival's voice via an OpenAI-compatible TTS server.
 *
 * <p>Speaks {@code POST /v1/audio/speech} with {@code {model, input, voice,
 * response_format}}, the contract implemented by Kokoro-FastAPI, openedai-speech and
 * friends. Nothing on the DGX answers it today — the vLLM instance there is text-only and
 * returns 404 — so this stays inert behind {@link #probe} until a server appears.
 *
 * <p>Two details are inherited from {@code LlmClient} rather than rediscovered:
 * <b>HTTP/1.1 is mandatory</b>, because Java's default HTTP/2 attempts an h2c upgrade that
 * this host's uvicorn mishandles by dropping the POST body; and every call is async,
 * because none of this may run on the JavaFX thread.
 */
public final class RemoteVoice implements RivalVoice {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * WAV rather than the default MP3: it is what {@link AudioClip} handles most reliably,
     * and the clips are seconds long, so the size difference costs nothing over a LAN.
     */
    private static final String FORMAT = "wav";

    private final TtsConfig config;
    private final HttpClient http;
    private final Duration requestTimeout;

    /** The clip currently sounding, so a new line — or a pause — can cut it off. */
    private final AtomicReference<AudioClip> playing = new AtomicReference<>();

    public RemoteVoice(TtsConfig config, Duration requestTimeout) {
        this.config = config;
        this.requestTimeout = requestTimeout;
        this.http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    /**
     * Asks the endpoint to synthesize one short word.
     *
     * <p>There is no GET that proves a TTS server is present — {@code /v1/models} is
     * served by plenty of things that cannot speak, the LLM on this very host among them —
     * so the only honest probe is the real request. It costs one tiny synthesis at
     * startup, and the audio is discarded.
     *
     * <p>Never completes exceptionally: no TTS server is an expected state.
     */
    public CompletableFuture<Boolean> probe() {
        return http.sendAsync(request("hi"), HttpResponse.BodyHandlers.ofByteArray())
                .thenApply(response -> response.statusCode() == 200
                        && response.body() != null
                        && response.body().length > 0)
                .exceptionally(e -> false);
    }

    @Override
    public void speak(String line) {
        if (line == null || line.isBlank()) {
            return;
        }
        stop();
        http.sendAsync(request(line), HttpResponse.BodyHandlers.ofByteArray())
                .thenAccept(response -> {
                    if (response.statusCode() != 200) {
                        LlmLog.note("tts failed: HTTP " + response.statusCode());
                        return;
                    }
                    play(response.body());
                })
                .exceptionally(e -> {
                    LlmLog.note("tts failed: " + e.getMessage());
                    return null;
                });
    }

    @Override
    public void stop() {
        AudioClip clip = playing.getAndSet(null);
        if (clip != null) {
            clip.stop();
        }
    }

    @Override
    public String describe() {
        return config.describe();
    }

    @Override
    public void close() {
        stop();
    }

    /**
     * Writes the audio to a temp file and plays it.
     *
     * <p>Via a file because both JavaFX audio classes take a URI and neither accepts
     * bytes. The file is deleted on exit rather than after playback: {@code AudioClip}
     * gives no completion callback, and deleting one still being read is worse than
     * leaving a few kilobytes in the temp directory for the length of a session.
     */
    private void play(byte[] audio) {
        try {
            Path file = Files.createTempFile("fridge-rival-", "." + FORMAT);
            file.toFile().deleteOnExit();
            Files.copy(new java.io.ByteArrayInputStream(audio), file,
                    StandardCopyOption.REPLACE_EXISTING);
            // AudioClip must be built and played on the FX thread.
            Platform.runLater(() -> {
                try {
                    AudioClip clip = new AudioClip(file.toUri().toString());
                    AudioClip previous = playing.getAndSet(clip);
                    if (previous != null) {
                        previous.stop();
                    }
                    clip.play();
                } catch (Throwable t) {
                    LlmLog.note("tts playback failed: " + t.getMessage());
                }
            });
        } catch (Exception e) {
            LlmLog.note("tts could not be written: " + e.getMessage());
        }
    }

    private HttpRequest request(String input) {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("model", config.model());
        body.put("input", input);
        body.put("voice", config.voice());
        body.put("response_format", FORMAT);

        HttpRequest.Builder builder = HttpRequest.newBuilder(config.speechUri())
                .header("Content-Type", "application/json")
                .timeout(requestTimeout)
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()));
        if (config.hasApiKey()) {
            builder.header("Authorization", "Bearer " + config.apiKey());
        }
        return builder.build();
    }
}
