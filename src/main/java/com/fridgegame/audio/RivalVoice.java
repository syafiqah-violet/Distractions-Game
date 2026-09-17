package com.fridgegame.audio;

/**
 * Speaks the rival's captions aloud.
 *
 * <p>The seam between the commentary and however the words actually get made: a TTS
 * server when one is reachable, the local Windows speech stack when it is not, and
 * {@link #SILENT} when neither is available.
 *
 * <p>Every method is <b>fire-and-forget and non-blocking</b>, the same rule that governs
 * {@code LlmClient}: these are called from the JavaFX thread while a level is running, and
 * nothing here may stall it. An implementation that cannot speak must degrade to silence
 * rather than throw — a rival that has lost its voice is a smaller problem than a game
 * that stops.
 */
public interface RivalVoice extends AutoCloseable {

    /** Says {@code line}, cutting off whatever was still being said. */
    void speak(String line);

    /** Stops mid-sentence. Called on pause, level end, and before each new line. */
    void stop();

    /** Short description for the console log. Never includes a key. */
    String describe();

    /** Releases whatever the voice owns — a child process, a temp file, a clip. */
    @Override
    void close();

    /** The rival keeps its captions but says nothing. */
    RivalVoice SILENT = new RivalVoice() {

        @Override
        public void speak(String line) {
        }

        @Override
        public void stop() {
        }

        @Override
        public String describe() {
            return "silent";
        }

        @Override
        public void close() {
        }
    };
}
