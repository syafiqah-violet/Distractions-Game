package com.fridgegame.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fridgegame.llm.CommentaryEvent;
import com.fridgegame.llm.JsonChat;
import com.fridgegame.llm.LlmCommentator;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

/**
 * The throttle, which is what makes the rival readable instead of a flickering wall.
 *
 * <p>Driven by a fake clock and a manually-completed chat, so no sleeping and no toolkit.
 */
class CommentaryThrottleTest {

    /** A chat whose replies are completed by hand, so requests can be left in flight. */
    private static final class ManualChat implements JsonChat {
        final List<String> prompts = new ArrayList<>();
        final List<CompletableFuture<String>> pending = new ArrayList<>();

        @Override
        public CompletableFuture<String> send(String system, String user) {
            prompts.add(user);
            CompletableFuture<String> future = new CompletableFuture<>();
            pending.add(future);
            return future;
        }

        void answerLast(String line) {
            pending.get(pending.size() - 1).complete("{\"line\":\"" + line + "\"}");
        }

        int requests() {
            return prompts.size();
        }
    }

    private final ManualChat chat = new ManualChat();
    private final List<String> shown = new ArrayList<>();
    private long now = 1_000_000;

    private CommentaryController controller() {
        CommentaryController controller = new CommentaryController(
                new LlmCommentator(chat), shown::add, () -> now, Runnable::run);
        controller.start();
        return controller;
    }

    private static CommentaryEvent event(CommentaryEvent.Kind kind) {
        return CommentaryEvent.of(kind, kind.name().toLowerCase(java.util.Locale.ROOT));
    }

    @Test
    void anEventBeforeAnythingElseGoesStraightOut() {
        CommentaryController controller = controller();

        controller.offer(event(CommentaryEvent.Kind.WRONG_DROP), "ctx");
        chat.answerLast("first");

        assertEquals(1, chat.requests());
        assertEquals(List.of("first"), shown);
    }

    @Test
    void onlyOneRequestIsEverInFlight() {
        CommentaryController controller = controller();

        controller.offer(event(CommentaryEvent.Kind.WRONG_DROP), "ctx");
        controller.offer(event(CommentaryEvent.Kind.ROWS_CLEARED), "ctx");
        controller.offer(event(CommentaryEvent.Kind.NEW_HOLES), "ctx");

        assertEquals(1, chat.requests(),
                "overlapping requests would answer out of order and hammer the endpoint");
    }

    @Test
    void theHighestPriorityPendingEventWins() {
        CommentaryController controller = controller();
        controller.offer(event(CommentaryEvent.Kind.WRONG_DROP), "ctx");
        chat.answerLast("first");
        shown.clear();

        // All queued while the gap is still closed.
        controller.offer(event(CommentaryEvent.Kind.STREAK), "ctx");
        controller.offer(event(CommentaryEvent.Kind.TOP_OUT), "ctx");
        controller.offer(event(CommentaryEvent.Kind.ROWS_CLEARED), "ctx");
        assertEquals(1, chat.requests(), "the gap has not elapsed yet");

        now += CommentaryController.MIN_GAP_MILLIS;
        controller.poll("ctx");

        assertEquals(2, chat.requests());
        assertTrue(chat.prompts.get(1).contains("top_out"),
                "a top-out must displace a streak, never the reverse");
    }

    @Test
    void aSecondLineWaitsForTheReadingGap() {
        CommentaryController controller = controller();
        controller.offer(event(CommentaryEvent.Kind.WRONG_DROP), "ctx");
        chat.answerLast("first");

        now += CommentaryController.MIN_GAP_MILLIS - 1;
        controller.offer(event(CommentaryEvent.Kind.TOP_OUT), "ctx");
        assertEquals(1, chat.requests(), "one millisecond short of the gap");

        now += 1;
        controller.poll("ctx");
        assertEquals(2, chat.requests(), "and now it goes");
    }

    @Test
    void anEventHeldBackByTheGapIsNotLost() {
        CommentaryController controller = controller();
        controller.offer(event(CommentaryEvent.Kind.WRONG_DROP), "ctx");
        chat.answerLast("first");
        shown.clear();

        controller.offer(event(CommentaryEvent.Kind.TOP_OUT), "ctx");
        now += CommentaryController.MIN_GAP_MILLIS;
        controller.poll("ctx");
        chat.answerLast("second");

        assertEquals(List.of("second"), shown);
    }

    @Test
    void aReplyThatArrivesTooLateIsDiscarded() {
        CommentaryController controller = controller();
        controller.offer(event(CommentaryEvent.Kind.WRONG_DROP), "ctx");

        now += CommentaryController.STALE_AFTER_MILLIS + 1;
        chat.answerLast("stale news");

        assertTrue(shown.isEmpty(),
                "a taunt about a mistake from eight seconds ago reads as confusion");
    }

    @Test
    void aReplyArrivingAfterTheLevelEndsIsDiscarded() {
        CommentaryController controller = controller();
        controller.offer(event(CommentaryEvent.Kind.WRONG_DROP), "ctx");

        controller.stop();
        chat.answerLast("too late");

        assertTrue(shown.isEmpty());
    }

    @Test
    void aFailedRequestDoesNotWedgeTheThrottle() {
        CommentaryController controller = controller();
        controller.offer(event(CommentaryEvent.Kind.WRONG_DROP), "ctx");
        chat.pending.get(0).completeExceptionally(new IllegalStateException("endpoint down"));

        now += CommentaryController.MIN_GAP_MILLIS;
        controller.offer(event(CommentaryEvent.Kind.TOP_OUT), "ctx");
        chat.answerLast("recovered");

        assertEquals(2, chat.requests(), "a failure must clear the in-flight latch");
        assertEquals(List.of("recovered"), shown);
    }

    @Test
    void silenceEventuallyProducesAnUnpromptedLine() {
        CommentaryController controller = controller();

        now += CommentaryController.IDLE_NUDGE_MILLIS;
        controller.poll("ctx");

        assertEquals(1, chat.requests());
        assertTrue(chat.prompts.get(0).contains("nothing has happened"));
    }

    @Test
    void unremarkableActivityStillHoldsOffTheIdleNudge() {
        CommentaryController controller = controller();

        // A player placing pieces cleanly: activity, but nothing worth a line about.
        for (int i = 0; i < 4; i++) {
            now += CommentaryController.IDLE_NUDGE_MILLIS - 1_000;
            controller.noteActivity();
            controller.poll("ctx");
        }

        assertEquals(0, chat.requests(),
                "accusing an actively-playing player of staring at the screen is the bug");
    }

    @Test
    void theIdleNudgeStillFiresOnceActivityActuallyStops() {
        CommentaryController controller = controller();
        controller.noteActivity();

        now += CommentaryController.IDLE_NUDGE_MILLIS;
        controller.poll("ctx");

        assertEquals(1, chat.requests());
    }

    @Test
    void activityOnAStoppedControllerIsIgnored() {
        CommentaryController controller = controller();
        controller.stop();

        controller.noteActivity();

        assertEquals(0, chat.requests());
    }

    @Test
    void thePreviousLineIsFedBackSoTheRivalDoesNotRepeatItself() {
        CommentaryController controller = controller();
        controller.offer(event(CommentaryEvent.Kind.NEW_HOLES), "ctx");
        chat.answerLast("You buried a cell. How careless.");

        now += CommentaryController.MIN_GAP_MILLIS;
        controller.offer(event(CommentaryEvent.Kind.NEW_HOLES), "ctx");

        assertTrue(chat.prompts.get(1).contains("You buried a cell. How careless."),
                "each call is a fresh conversation, so the model needs telling");
        assertTrue(chat.prompts.get(1).contains("say something different"));
    }

    @Test
    void theFirstLineOfALevelHasNoPreviousLineToAvoid() {
        CommentaryController controller = controller();
        controller.offer(event(CommentaryEvent.Kind.LEVEL_START), "ctx");
        chat.answerLast("Here we go.");

        controller.start();
        now += CommentaryController.MIN_GAP_MILLIS;
        controller.offer(event(CommentaryEvent.Kind.LEVEL_START), "ctx");

        assertTrue(!chat.prompts.get(1).contains("You already said"),
                "a new level starts the rival's memory over");
    }

    @Test
    void pollDoesNothingWhileTheRivalIsStillWithinTheIdleWindow() {
        CommentaryController controller = controller();

        now += CommentaryController.IDLE_NUDGE_MILLIS - 1;
        controller.poll("ctx");

        assertEquals(0, chat.requests());
    }

    @Test
    void aStoppedControllerIgnoresEverything() {
        CommentaryController controller = controller();
        controller.stop();

        controller.offer(event(CommentaryEvent.Kind.TOP_OUT), "ctx");
        now += CommentaryController.IDLE_NUDGE_MILLIS;
        controller.poll("ctx");

        assertEquals(0, chat.requests());
    }

    @Test
    void startingAFreshLevelClearsAnyHeldOverEvent() {
        CommentaryController controller = controller();
        controller.offer(event(CommentaryEvent.Kind.WRONG_DROP), "ctx");
        chat.answerLast("first");
        controller.offer(event(CommentaryEvent.Kind.TOP_OUT), "ctx"); // held by the gap

        controller.start();
        controller.poll("ctx");

        assertEquals(1, chat.requests(), "last level's pending taunt must not open the next one");
    }
}
