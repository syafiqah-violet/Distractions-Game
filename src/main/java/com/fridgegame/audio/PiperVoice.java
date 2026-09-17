package com.fridgegame.audio;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fridgegame.llm.LlmLog;
import com.fridgegame.llm.TtsConfig;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.media.AudioClip;

/**
 * The rival's voice, spoken by a local Piper server.
 *
 * <p>Piper is a neural (VITS) engine that runs entirely on this machine, for free and
 * without an account. That combination is the whole reason it is here: the Windows SAPI
 * stack this replaced was free but sounded like a robot, and the hosted services that
 * sound better meter by the minute, which does not survive a development loop that speaks
 * a line every few seconds.
 *
 * <p><b>A server, not a command, and one server for the session.</b> Piper's CLI reloads
 * the voice model on every invocation; its HTTP server loads once and keeps it resident.
 * Measured on this project: ~1.9s for the first synthesis including warm-up, then
 * <b>~160ms</b> for each line after. That is the difference between audio that lands with
 * the caption card and audio that trails it — the same lesson the SAPI helper learned, so
 * the lifecycle here is deliberately the same shape.
 *
 * <p>The game owns that process. It is started on demand, polled until the model is
 * loaded, and destroyed on exit — nothing else would ever reap it.
 *
 * <p>Audio comes back as a complete WAV per line, which is what makes interruption honest:
 * a new caption or a pause stops an {@link AudioClip} outright. A streamed backend would
 * have to guess where one utterance ended and the next began.
 */
public final class PiperVoice implements RivalVoice {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** How often to ask a starting server whether its model is loaded yet. */
    private static final Duration POLL_INTERVAL = Duration.ofMillis(500);

    /**
     * Per-poll budget. Deliberately longer than {@link #POLL_INTERVAL}: a request that
     * expires sooner than the client is allowed to spend connecting would report a
     * loaded server as absent whenever the machine is briefly busy.
     */
    private static final Duration POLL_TIMEOUT = Duration.ofSeconds(3);

    /** Long enough for any caption; a runaway line is truncated rather than sent whole. */
    private static final int MAX_CHARS = 400;

    private final TtsConfig config;
    private final HttpClient http;
    private final Duration requestTimeout;

    /** The server this instance started, or null when it attached to someone else's. */
    private final Process process;

    /** Where a spawned server's complaints went, so a failure can say what went wrong. */
    private final Path errorLog;

    /** The clip currently sounding, so a new line — or a pause — can cut it off. */
    private final AtomicReference<AudioClip> playing = new AtomicReference<>();

    /**
     * Stops a dead server from narrating every caption for the rest of the session. The
     * first failure is worth a line; the two hundredth is noise.
     */
    private final AtomicBoolean reportedFailure = new AtomicBoolean();

    private PiperVoice(TtsConfig config, Duration requestTimeout, Process process, Path errorLog) {
        this.config = config;
        this.requestTimeout = requestTimeout;
        this.process = process;
        this.errorLog = errorLog;
        this.http = HttpClient.newBuilder()
                // HTTP/1.1 for the same reason as LlmClient: Java defaults to HTTP/2,
                // which over plaintext means an h2c upgrade attempt that some Python
                // servers mishandle by dropping the POST body.
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    /**
     * Brings up a voice, or resolves to {@link RivalVoice#SILENT} if it cannot.
     *
     * <p>Starts a server unless {@code tts.spawn=false}, then polls {@code /info} until the
     * voice model is loaded. Polling rather than a fixed sleep because model load time is a
     * property of the machine, and a laptop that takes twelve seconds should still get a
     * voice.
     *
     * <p><b>Never completes exceptionally.</b> A missing Python, a missing voice or an
     * occupied port are all expected states on a machine that has not been set up yet, and
     * none of them is worth interrupting the game for — the captions still read as text.
     *
     * @param readinessTimeout how long to wait for the model to load before giving up
     */
    public static CompletableFuture<RivalVoice> start(
            TtsConfig config, Duration requestTimeout, Duration readinessTimeout) {
        PiperVoice attached = new PiperVoice(config, requestTimeout, null, null);
        return attached.ping().thenCompose(alreadyServing -> {
            if (alreadyServing) {
                // Someone is already on that port: this run's own server surviving an
                // unclean exit, or one the player started by hand. Either way, spawning a
                // second would only fail to bind, and the failure would surface as a
                // readiness timeout that says nothing about the real cause.
                LlmLog.note("rival voice: attached to the piper already on " + config.baseUrl());
                return CompletableFuture.completedFuture((RivalVoice) attached);
            }
            attached.close();
            if (!config.spawn()) {
                LlmLog.note("rival voice: nothing answering " + config.infoUri()
                        + " and tts.spawn=false, so nothing was started");
                return CompletableFuture.completedFuture(RivalVoice.SILENT);
            }
            return spawn(config, requestTimeout, readinessTimeout);
        });
    }

    private static CompletableFuture<RivalVoice> spawn(
            TtsConfig config, Duration requestTimeout, Duration readinessTimeout) {
        Process process;
        Path errorLog;
        try {
            // Piper writes its startup failures to stderr and then exits. Keeping them is
            // what lets a failed start say "Unable to find voice" instead of just timing
            // out with no explanation.
            errorLog = Files.createTempFile("fridge-piper-", ".log");
            errorLog.toFile().deleteOnExit();
            process = new ProcessBuilder(command(config))
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(errorLog.toFile())
                    .start();
        } catch (Exception e) {
            LlmLog.note("rival voice: could not start " + config.python() + " - " + e.getMessage());
            return CompletableFuture.completedFuture(RivalVoice.SILENT);
        }
        LlmLog.note("rival voice: starting piper " + config.voice() + " on " + config.baseUrl());
        PiperVoice voice = new PiperVoice(config, requestTimeout, process, errorLog);
        return voice.awaitReady(readinessTimeout).thenApply(ready -> {
            if (ready) {
                return (RivalVoice) voice;
            }
            voice.close();
            return RivalVoice.SILENT;
        });
    }

    /**
     * One shot at {@code /info}, to find out whether a server is already there.
     *
     * <p>Deliberately generous with time for a single request: this decides whether to
     * spawn a competing process, and answering "no" too eagerly is the expensive mistake.
     */
    private CompletableFuture<Boolean> ping() {
        HttpRequest request = HttpRequest.newBuilder(config.infoUri())
                .timeout(Duration.ofSeconds(2))
                .GET()
                .build();
        return http.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                .thenApply(response -> response.statusCode() == 200)
                .exceptionally(e -> false);
    }

    /** The server command line. Package-private so a test can assert it without spawning. */
    static List<String> command(TtsConfig config) {
        List<String> command = new ArrayList<>(List.of(
                config.python(),
                // "-m piper.http_server" rather than the piper.exe shim: pip installs that
                // shim into a Scripts directory that is frequently not on PATH, while the
                // module is always reachable from the interpreter that owns the package.
                "-m", "piper.http_server",
                "-m", config.voice(),
                "--data-dir", config.dataDir(),
                // Loopback only. This voice is for the player sitting at this machine, and
                // binding it wider would put an unauthenticated synthesis endpoint on the
                // network for no benefit.
                "--host", "127.0.0.1",
                "--port", String.valueOf(config.port())));
        if (config.lengthScale() != null) {
            command.add("--length-scale");
            command.add(String.valueOf(config.lengthScale()));
        }
        return command;
    }

    /**
     * Polls {@code /info} until the voice model is loaded.
     *
     * <p>Gives up early if a spawned server has already exited — a bad voice name fails in
     * under a second, and waiting out the full timeout for a process that is gone would
     * turn an instant, explainable failure into a long silent one.
     */
    private CompletableFuture<Boolean> awaitReady(Duration timeout) {
        CompletableFuture<Boolean> ready = new CompletableFuture<>();
        ScheduledExecutorService poller = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "piper-readiness");
            t.setDaemon(true);
            return t;
        });
        long deadline = System.nanoTime() + timeout.toNanos();
        poll(ready, poller, deadline);
        ready.whenComplete((result, error) -> poller.shutdownNow());
        return ready;
    }

    private void poll(
            CompletableFuture<Boolean> ready, ScheduledExecutorService poller, long deadline) {
        if (process != null && !process.isAlive()) {
            LlmLog.note("rival voice: piper exited at startup - " + startupError());
            ready.complete(false);
            return;
        }
        if (System.nanoTime() > deadline) {
            // Report what Piper was saying, not merely that time ran out. A bare timeout
            // is the one failure here that tells you nothing about how to fix it.
            LlmLog.note("rival voice: piper did not answer " + config.infoUri()
                    + " in time - last words: " + startupError());
            ready.complete(false);
            return;
        }
        HttpRequest request = HttpRequest.newBuilder(config.infoUri())
                .timeout(POLL_TIMEOUT)
                .GET()
                .build();
        http.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                .thenAccept(response -> {
                    if (response.statusCode() == 200) {
                        ready.complete(true);
                    } else {
                        schedule(ready, poller, deadline);
                    }
                })
                .exceptionally(e -> {
                    // Connection refused is the normal state of a server still starting.
                    schedule(ready, poller, deadline);
                    return null;
                });
    }

    private void schedule(
            CompletableFuture<Boolean> ready, ScheduledExecutorService poller, long deadline) {
        try {
            poller.schedule(
                    () -> poll(ready, poller, deadline),
                    POLL_INTERVAL.toMillis(),
                    TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            ready.complete(false); // the poller was shut down; nothing left to wait for
        }
    }

    /** The most useful line Piper wrote before dying, for the console. */
    private String startupError() {
        if (errorLog == null) {
            return "nothing on stderr";
        }
        try {
            List<String> lines = Files.readAllLines(errorLog, StandardCharsets.UTF_8);
            for (int i = lines.size() - 1; i >= 0; i--) {
                String line = lines.get(i).trim();
                if (!line.isEmpty()) {
                    return line;
                }
            }
        } catch (Exception e) {
            // Nothing to add; the caller still reports the failure itself.
        }
        return "nothing on stderr";
    }

    @Override
    public void speak(String line) {
        String text = sanitize(line);
        if (text.isEmpty()) {
            return;
        }
        stop();
        HttpRequest request = HttpRequest.newBuilder(config.synthesizeUri())
                .header("Content-Type", "application/json")
                .timeout(requestTimeout)
                .POST(HttpRequest.BodyPublishers.ofString(body(text), StandardCharsets.UTF_8))
                .build();
        http.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
                .thenAccept(response -> {
                    if (response.statusCode() != 200) {
                        noteOnce("tts failed: HTTP " + response.statusCode());
                        return;
                    }
                    play(response.body());
                })
                .exceptionally(e -> {
                    noteOnce("tts failed: " + e.getMessage());
                    return null;
                });
    }

    /**
     * The synthesis request body. Visible for testing.
     *
     * <p>Only the text travels. The voice is fixed when the server starts, and passing an
     * unknown one here is silently accepted rather than rejected, so sending it would look
     * like control the caller does not actually have.
     */
    static String body(String text) {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("text", text);
        return body.toString();
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
        return "Piper " + config.describe() + (process == null ? " (external)" : "");
    }

    @Override
    public void close() {
        stop();
        if (process == null) {
            return;
        }
        process.destroy();
        try {
            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }

    /**
     * Writes the audio to a temp file and plays it.
     *
     * <p>Via a file because both JavaFX audio classes take a URI and neither accepts bytes.
     * The file is deleted on exit rather than after playback: {@link AudioClip} gives no
     * completion callback, and deleting one still being read is worse than leaving a few
     * kilobytes in the temp directory for the length of a session.
     */
    private void play(byte[] audio) {
        try {
            Path file = Files.createTempFile("fridge-rival-", ".wav");
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
                    noteOnce("tts playback failed: " + t.getMessage());
                }
            });
        } catch (Exception e) {
            noteOnce("tts could not be written: " + e.getMessage());
        }
    }

    /** Says it the first time and stays quiet after that. */
    private void noteOnce(String message) {
        if (reportedFailure.compareAndSet(false, true)) {
            LlmLog.note(message + " - the rival's captions stay text-only");
        }
    }

    /**
     * Trims a caption to something worth synthesizing.
     *
     * <p>The model is told to answer in a single short line and does, but "does" is not
     * "must", and a runaway paragraph would otherwise become a runaway monologue the player
     * cannot skip.
     *
     * <p>Visible for testing.
     */
    static String sanitize(String line) {
        if (line == null) {
            return "";
        }
        String flattened = line.replaceAll("[\\r\\n]+", " ").trim();
        return flattened.length() <= MAX_CHARS ? flattened : flattened.substring(0, MAX_CHARS);
    }
}
