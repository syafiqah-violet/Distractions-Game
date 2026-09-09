package com.fridgegame.llm;

/**
 * The LLM's running commentary on itself, printed to stdout.
 *
 * <p>{@code javafx:run} inherits the JVM's stdout, so this shows up in the terminal that
 * launched the game with no plugin configuration. It exists because the model's visible
 * output is one short caption on screen: without a log there is no way to tell a taunt
 * that was suppressed by the throttle from one the endpoint never answered, or to see the
 * reasoning behind a difficulty change the player only experiences as "level 3 felt easier".
 *
 * <p>{@link #thinking} is reserved for the model's own words, verbatim. Anything the game
 * decided goes through {@link #note} or {@link #event}, so the two are never confused.
 */
public final class LlmLog {

    private LlmLog() {
    }

    /** One line the model produced, quoted exactly as it said it. */
    public static void thinking(String text) {
        System.out.println("LLM thinking: \"" + text + "\"");
    }

    /** Connection state, timeouts, clamped decisions — the game talking about the model. */
    public static void note(String text) {
        System.out.println("[llm] " + text);
    }

    /** What happened in the game that prompted a request. */
    public static void event(String text) {
        System.out.println("[commentary] " + text);
    }

    /** A level-director stats line or decision. */
    public static void director(String text) {
        System.out.println("[director] " + text);
    }
}
