package com.fridgegame.controller;

import com.fridgegame.llm.CommentaryEvent;
import com.fridgegame.llm.LlmCommentator;
import com.fridgegame.llm.LlmLog;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * Decides which of the game's many events actually becomes a caption.
 *
 * <p>The throttle is the whole design. Events fire far faster than anyone reads: a single
 * four-row clear plus two drops can produce five in a second. Without a gate the card
 * would flicker through lines nobody finishes, and the endpoint would be handling
 * overlapping requests whose answers arrive out of order. So:
 *
 * <ul>
 *   <li><b>One request in flight.</b> Events arriving during one are folded into a single
 *       pending slot where the highest {@link CommentaryEvent#priority()} wins — a top-out
 *       displaces a streak, never the reverse.</li>
 *   <li><b>A floor between lines.</b> A caption stays up long enough to be read.</li>
 *   <li><b>Late replies are dropped.</b> A taunt about a mistake from eight seconds ago
 *       reads as the rival being confused, which is worse than it saying nothing.</li>
 * </ul>
 *
 * <p>The clock and the UI executor are injectable, so the throttle can be tested with no
 * toolkit and no sleeping.
 */
public class CommentaryController {

    /** Minimum gap between displayed lines. Roughly how long a short caption takes to read. */
    static final long MIN_GAP_MILLIS = 5_000;

    /** How long a reply may take before it is no longer about anything current. */
    static final long STALE_AFTER_MILLIS = 8_000;

    /** Silence after which the rival volunteers something unprompted. */
    static final long IDLE_NUDGE_MILLIS = 12_000;

    private final LlmCommentator commentator;
    private final Consumer<String> onLine;
    private final LongSupplier clock;
    private final Consumer<Runnable> uiExecutor;

    private boolean enabled;
    private boolean inFlight;
    private CommentaryEvent pending;
    private long lastShownAt;
    private long lastActivityAt;

    /** Fed back into the next prompt so the rival does not repeat itself verbatim. */
    private String lastLine;

    /**
     * @param onLine     receives each caption that survives the throttle
     * @param clock      millisecond source; {@code System::currentTimeMillis} in the game
     * @param uiExecutor hops onto the thread that owns the state and the view. All mutation
     *                   of this object happens there, so replies arriving on HTTP threads
     *                   cannot race the game loop. {@code Platform::runLater} in the game,
     *                   {@code Runnable::run} in tests.
     */
    public CommentaryController(
            LlmCommentator commentator,
            Consumer<String> onLine,
            LongSupplier clock,
            Consumer<Runnable> uiExecutor) {
        this.commentator = commentator;
        this.onLine = onLine;
        this.clock = clock;
        this.uiExecutor = uiExecutor;
    }

    /**
     * Opens for business — only the combined level should do this.
     *
     * <p>Also the offline switch: with no reachable endpoint the commentator is never
     * built, so this is never called and no card appears.
     */
    public void start() {
        enabled = true;
        pending = null;
        inFlight = false;
        lastShownAt = 0;
        lastLine = null;
        lastActivityAt = clock.getAsLong();
    }

    public void stop() {
        enabled = false;
        pending = null;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Records that the player did something, without asking for a line about it.
     *
     * <p>Separate from {@link #offer} because most of what a player does is not worth
     * remarking on — a piece that lands cleanly, an item sorted mid-streak. Those are
     * still activity, and without counting them the idle timer fires at someone who is
     * playing perfectly well and accuses them of staring at the screen.
     */
    public void noteActivity() {
        if (enabled) {
            lastActivityAt = clock.getAsLong();
        }
    }

    /**
     * Offers an event for comment.
     *
     * <p>Fire-and-forget: nothing here blocks, and dropping the event is a normal outcome
     * rather than a failure.
     */
    public void offer(CommentaryEvent event, String context) {
        if (!enabled) {
            return;
        }
        lastActivityAt = clock.getAsLong();
        if (pending == null || event.priority() >= pending.priority()) {
            pending = event;
        }
        dispatch(context);
    }

    /**
     * Called once a second by the game timer: releases a queued event whose turn has come,
     * and volunteers an idle line when nothing has happened for a while.
     */
    public void poll(String context) {
        if (!enabled) {
            return;
        }
        if (pending == null
                && !inFlight
                && clock.getAsLong() - lastActivityAt >= IDLE_NUDGE_MILLIS) {
            pending = CommentaryEvent.of(CommentaryEvent.Kind.STALLED,
                    "nothing has happened for a while");
            lastActivityAt = clock.getAsLong();
        }
        dispatch(context);
    }

    private void dispatch(String context) {
        if (pending == null || inFlight) {
            return;
        }
        long now = clock.getAsLong();
        if (lastShownAt != 0 && now - lastShownAt < MIN_GAP_MILLIS) {
            return; // still reading the last one; the event stays pending
        }

        CommentaryEvent event = pending;
        pending = null;
        inFlight = true;
        long requestedAt = now;
        LlmLog.event(event.kind() + " - " + event.detail() + "  (" + context + ")");

        commentator.react(event, context, lastLine).whenComplete((line, error) ->
                uiExecutor.accept(() -> deliver(line, error, requestedAt)));
    }

    /** Publishes a reply, or explains on the console why it was thrown away. */
    private void deliver(String line, Throwable error, long requestedAt) {
        long now = clock.getAsLong();
        inFlight = false;
        if (error != null) {
            LlmLog.note("commentary failed: " + rootMessage(error));
            return;
        }
        if (!enabled) {
            LlmLog.note("commentary dropped: level already over - \"" + line + "\"");
            return;
        }
        if (now - requestedAt > STALE_AFTER_MILLIS) {
            LlmLog.note("commentary dropped as stale after "
                    + (now - requestedAt) + "ms - \"" + line + "\"");
            return;
        }
        lastShownAt = now;
        lastLine = line;
        LlmLog.thinking(line);
        onLine.accept(line);
    }

    private static String rootMessage(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        return message == null || message.isBlank() ? cause.getClass().getSimpleName() : message;
    }
}
