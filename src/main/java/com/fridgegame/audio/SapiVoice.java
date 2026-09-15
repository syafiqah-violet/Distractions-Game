package com.fridgegame.audio;

import com.fridgegame.llm.LlmLog;
import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * The rival's voice via the Windows speech stack, for when no TTS server is reachable.
 *
 * <p><b>One long-lived helper process, not one per line.</b> That is the whole design, and
 * it came from measuring: a fresh {@code powershell.exe} costs ~1.3s wall clock per
 * utterance, of which ~900ms is interpreter startup, while a warm process synthesizes the
 * same caption in ~430ms. Since a caption is on screen for about five seconds, the
 * difference is between a voice that tracks the card and one that trails it.
 *
 * <p>The script arrives by {@code -EncodedCommand} rather than {@code -Command -} because
 * the latter makes PowerShell read its <i>script</i> from stdin, which is the pipe this
 * needs for the captions. Passing the script as an argument leaves stdin free for data —
 * and that in turn means caption text is never parsed as PowerShell, so there is no
 * quoting to get wrong and no way for a line the model wrote to become a command.
 *
 * <p>{@code SpeakAsync} plus {@code SpeakAsyncCancelAll} gives the interrupt behaviour for
 * free: a new caption cuts off the one still being spoken, and an explicit stop covers
 * pause and level end.
 *
 * <p>Audio leaves through SAPI's own output rather than JavaFX, so it mixes alongside the
 * sound effects and is not affected by {@link Sfx#setMuted}.
 */
public final class SapiVoice implements RivalVoice {

    /**
     * Reads one command per line from stdin. {@code [Console]::In} rather than the
     * pipeline, because a script started by {@code -EncodedCommand} has no pipeline input.
     */
    private static final String SCRIPT = """
            Add-Type -AssemblyName System.Speech
            $ErrorActionPreference = 'SilentlyContinue'
            $ProgressPreference = 'SilentlyContinue'
            $s = New-Object System.Speech.Synthesis.SpeechSynthesizer
            $s.Volume = 90
            $s.Rate = 1
            [Console]::Out.WriteLine('READY')
            [Console]::Out.Flush()
            while ($null -ne ($line = [Console]::In.ReadLine())) {
              if ($line -eq 'QUIT') { break }
              elseif ($line -eq 'STOP') { $s.SpeakAsyncCancelAll() }
              elseif ($line.StartsWith('SPEAK ')) {
                $s.SpeakAsyncCancelAll()
                [void]$s.SpeakAsync($line.Substring(6))
              }
            }
            $s.Dispose()
            """;

    /** Long enough for any caption; a runaway line is truncated rather than sent whole. */
    private static final int MAX_CHARS = 400;

    private final Process process;
    private final BufferedWriter toHelper;

    /**
     * Writes happen here, never on the caller's thread.
     *
     * <p>A pipe write is normally instant, but it blocks once the OS buffer fills and the
     * child is not draining it — a wedged helper would otherwise freeze the game mid-level
     * on what is meant to be a fire-and-forget call. Single-threaded, so commands still
     * reach the helper in the order they were spoken.
     */
    private final ExecutorService writer = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "rival-voice");
        t.setDaemon(true);
        return t;
    });

    private SapiVoice(Process process) {
        this.process = process;
        this.toHelper = new BufferedWriter(
                new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
    }

    /** Whether this platform has any chance of running the helper. */
    public static boolean isSupported() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    /**
     * Starts the helper, or returns {@link RivalVoice#SILENT} if it will not start.
     *
     * <p>Never throws: no local speech is an expected state on a machine that has none,
     * not an error worth interrupting the game for.
     */
    public static RivalVoice start() {
        if (!isSupported()) {
            return RivalVoice.SILENT;
        }
        try {
            String encoded = Base64.getEncoder()
                    // PowerShell decodes -EncodedCommand as UTF-16LE, not UTF-8.
                    .encodeToString(SCRIPT.getBytes(StandardCharsets.UTF_16LE));
            Process process = new ProcessBuilder(
                    "powershell.exe", "-NoProfile", "-NonInteractive", "-EncodedCommand", encoded)
                    // The helper narrates its own startup in CLIXML on stderr; none of it
                    // is useful here, and left connected it would fill a pipe nobody reads.
                    .redirectErrorStream(false)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            if (!process.isAlive()) {
                return RivalVoice.SILENT;
            }
            return new SapiVoice(process);
        } catch (Exception e) {
            LlmLog.note("local voice unavailable: " + e.getMessage());
            return RivalVoice.SILENT;
        }
    }

    @Override
    public void speak(String line) {
        String text = sanitize(line);
        if (!text.isEmpty()) {
            send("SPEAK " + text);
        }
    }

    @Override
    public void stop() {
        send("STOP");
    }

    @Override
    public String describe() {
        return "Windows SAPI (local)";
    }

    @Override
    public void close() {
        send("QUIT");
        writer.shutdown();
        try {
            // Give the queued QUIT a moment to reach a helper that is mid-sentence.
            if (!writer.awaitTermination(500, TimeUnit.MILLISECONDS)) {
                writer.shutdownNow();
            }
            toHelper.close();
            if (!process.waitFor(500, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        } catch (Exception e) {
            process.destroyForcibly();
        }
    }

    private void send(String command) {
        if (!process.isAlive()) {
            return; // the helper died at some point; captions carry on as text
        }
        try {
            writer.execute(() -> {
                try {
                    toHelper.write(command);
                    toHelper.newLine();
                    toHelper.flush();
                } catch (Exception e) {
                    // A broken pipe means the helper is gone. Nothing to recover and
                    // nothing worth saying twice — every later send sees !isAlive.
                }
            });
        } catch (Exception e) {
            // Rejected because close() already shut the executor down.
        }
    }

    /**
     * Makes a caption safe to send as one line of a line-oriented protocol.
     *
     * <p>Newlines are the framing, so a caption containing one would be read as a second
     * command. The model is told to answer in a single short line and does, but "does" is
     * not "must" — this is the layer that makes that a formatting detail rather than a way
     * to inject {@code QUIT}.
     */
    private static String sanitize(String line) {
        if (line == null) {
            return "";
        }
        String flattened = line.replaceAll("[\\r\\n]+", " ").trim();
        return flattened.length() <= MAX_CHARS ? flattened : flattened.substring(0, MAX_CHARS);
    }
}
