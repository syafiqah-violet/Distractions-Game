package com.fridgegame.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

/** Reply parsing and the prompt contract. No socket: a fake {@link JsonChat} stands in. */
class LlmCommentatorTest {

    private static LlmCommentator replying(String content) {
        return new LlmCommentator((system, user) -> CompletableFuture.completedFuture(content));
    }

    @Test
    void readsTheLineOutOfAWellFormedReply() {
        assertEquals("Milk in the freezer. Bold.",
                LlmCommentator.parse("{\"line\":\"Milk in the freezer. Bold.\"}"));
    }

    @Test
    void toleratesWhitespaceAndPrettyPrinting() {
        assertEquals("Nice.", LlmCommentator.parse("{\n  \"line\": \"  Nice.  \"\n}"));
    }

    @Test
    void ignoresExtraFieldsTheModelVolunteers() {
        assertEquals("Sure.",
                LlmCommentator.parse("{\"line\":\"Sure.\",\"mood\":\"smug\"}"));
    }

    @Test
    void rejectsProseInsteadOfJson() {
        assertThrows(IllegalArgumentException.class,
                () -> LlmCommentator.parse("I think you should try harder"));
    }

    @Test
    void rejectsARepliesWithNoLineField() {
        assertThrows(IllegalArgumentException.class,
                () -> LlmCommentator.parse("{\"comment\":\"wrong key\"}"));
    }

    @Test
    void rejectsABlankLineRatherThanShowingAnEmptyCard() {
        assertThrows(IllegalArgumentException.class,
                () -> LlmCommentator.parse("{\"line\":\"   \"}"));
        assertThrows(IllegalArgumentException.class,
                () -> LlmCommentator.parse("{\"line\":\"\"}"));
    }

    @Test
    void rejectsANonStringLine() {
        assertThrows(IllegalArgumentException.class,
                () -> LlmCommentator.parse("{\"line\":42}"));
    }

    @Test
    void truncatesAnOverlongLineRatherThanOverflowingTheCard() {
        String longLine = "x".repeat(400);

        String parsed = LlmCommentator.parse("{\"line\":\"" + longLine + "\"}");

        assertEquals(90, parsed.length(),
                "guided decoding caps this, but a proxy in between might not");
    }

    @Test
    void reactSendsTheEventDetailAndTheGameStateToTheModel() throws Exception {
        StringBuilder seen = new StringBuilder();
        LlmCommentator commentator = new LlmCommentator((system, user) -> {
            seen.append(user);
            return CompletableFuture.completedFuture("{\"line\":\"ok\"}");
        });

        commentator.react(
                CommentaryEvent.of(CommentaryEvent.Kind.WRONG_DROP, "milk went in the freezer"),
                "score=30 lives=2").get();

        assertTrue(seen.toString().contains("milk went in the freezer"),
                "the model must be told the specific thing to react to");
        assertTrue(seen.toString().contains("score=30 lives=2"),
                "state travels with it so the tone can track how they are doing");
    }

    @Test
    void reactPassesThePreviousLineBackSoTheModelCanVaryIt() throws Exception {
        StringBuilder seen = new StringBuilder();
        LlmCommentator commentator = new LlmCommentator((system, user) -> {
            seen.append(user);
            return CompletableFuture.completedFuture("{\"line\":\"ok\"}");
        });

        commentator.react(
                CommentaryEvent.of(CommentaryEvent.Kind.NEW_HOLES, "buried a cell"),
                "score=30",
                "You buried a cell. How careless.").get();

        assertTrue(seen.toString().contains("You buried a cell. How careless."));
        assertTrue(seen.toString().contains("say something different"));
    }

    @Test
    void reactOmitsThePreviousLineWhenThereIsNotOne() throws Exception {
        StringBuilder seen = new StringBuilder();
        LlmCommentator commentator = new LlmCommentator((system, user) -> {
            seen.append(user);
            return CompletableFuture.completedFuture("{\"line\":\"ok\"}");
        });

        commentator.react(
                CommentaryEvent.of(CommentaryEvent.Kind.LEVEL_START, "started"), "ctx", "  ").get();

        assertTrue(!seen.toString().contains("You already said"),
                "a blank previous line must not become an empty instruction");
    }

    @Test
    void aFailedRequestSurfacesAsAFailedFuture() {
        LlmCommentator commentator = new LlmCommentator((system, user) ->
                CompletableFuture.failedFuture(new IllegalStateException("endpoint down")));

        assertThrows(java.util.concurrent.ExecutionException.class,
                () -> commentator.react(
                        CommentaryEvent.of(CommentaryEvent.Kind.STALLED, "quiet"), "ctx").get());
    }

    @Test
    void badJsonFromTheEndpointFailsTheFutureRatherThanThrowingInline() {
        CompletableFuture<String> future = replying("not json")
                .react(CommentaryEvent.of(CommentaryEvent.Kind.TOP_OUT, "died"), "ctx");

        assertTrue(future.isCompletedExceptionally());
    }

    @Test
    void theSchemaCapsTheLineLengthAndForbidsExtraFields() {
        assertEquals(90, LlmCommentator.LINE_SCHEMA
                .path("properties").path("line").path("maxLength").asInt());
        assertTrue(LlmCommentator.LINE_SCHEMA.path("required").toString().contains("line"));
        assertEquals(false, LlmCommentator.LINE_SCHEMA.path("additionalProperties").asBoolean(true));
    }

    @Test
    void eventPrioritiesRankTheLoudestThingsHighest() {
        assertTrue(CommentaryEvent.Kind.TOP_OUT.priority()
                > CommentaryEvent.Kind.WRONG_DROP.priority());
        assertTrue(CommentaryEvent.Kind.WRONG_DROP.priority()
                > CommentaryEvent.Kind.ROWS_CLEARED.priority());
        assertTrue(CommentaryEvent.Kind.ROWS_CLEARED.priority()
                > CommentaryEvent.Kind.STALLED.priority());
    }
}
