package com.fridgegame.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fridgegame.data.ItemCatalog;
import com.fridgegame.director.LevelStats;
import com.fridgegame.model.FoodCategory;
import com.fridgegame.model.GroceryItem;
import com.fridgegame.model.Level;
import com.fridgegame.model.LevelMode;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

/**
 * The clamping, which is the actual contract.
 *
 * <p>Guided decoding constrains the reply's shape but not its judgement, and the game has
 * to survive a model that answers with something schema-valid and unplayable. Several of
 * these replies could not come off the live endpoint at all — that is the point: the
 * bounds must hold without depending on the endpoint enforcing them.
 */
class LlmLevelDirectorTest {

    private static final Level COMBINED_TEMPLATE = new Level(
            3, LevelMode.COMBINED, ItemCatalog.pick(6, List.of(), 1L), 180, 480, 0);
    private static final Level TETRIS_TEMPLATE =
            new Level(2, LevelMode.TETRIS_ONLY, List.of(), 60, 550, 1);
    private static final Level SORT_TEMPLATE = new Level(
            1, LevelMode.SORT_ONLY, ItemCatalog.pick(4, List.of(), 2L), 60, 0, 0);

    private static final LevelStats STATS =
            new LevelStats(LevelMode.TETRIS_ONLY, 30, 4, 0, 0, 1, 0, 60);

    private static String reply(int gravity, int items, String categories, String reason) {
        return "{\"gravityMillis\":" + gravity + ",\"itemCount\":" + items
                + ",\"categories\":" + categories + ",\"reason\":\"" + reason + "\"}";
    }

    private static Level directed(String reply, Level template) throws Exception {
        LlmLevelDirector director = new LlmLevelDirector(
                (system, user) -> CompletableFuture.completedFuture(reply), 99L);
        return director.nextLevel(template, STATS).get();
    }

    // ------------------------------------------------------------- happy path

    @Test
    void appliesAReplyWithinTheBounds() throws Exception {
        Level level = directed(reply(620, 5, "[\"DAIRY\"]", "easing off"), COMBINED_TEMPLATE);

        assertEquals(620, level.gravityMillis());
        assertEquals(5, level.items().size());
    }

    @Test
    void preservesEverythingTheDirectorIsNotAllowedToTouch() throws Exception {
        Level level = directed(reply(300, 10, "[]", "harder"), COMBINED_TEMPLATE);

        assertEquals(COMBINED_TEMPLATE.number(), level.number());
        assertEquals(COMBINED_TEMPLATE.mode(), level.mode());
        assertEquals(COMBINED_TEMPLATE.timeLimitSeconds(), level.timeLimitSeconds(),
                "the clock is the level design, not a difficulty knob");
        assertEquals(COMBINED_TEMPLATE.requiredRows(), level.requiredRows());
    }

    // ---------------------------------------------------------------- clamping

    @Test
    void clampsAnUnplayablyFastGravity() throws Exception {
        Level level = directed(reply(5, 5, "[]", "much harder"), COMBINED_TEMPLATE);

        assertEquals(LlmLevelDirector.MIN_GRAVITY_MILLIS, level.gravityMillis());
    }

    @Test
    void clampsAGravitySoSlowTheLevelWouldBeBoring() throws Exception {
        Level level = directed(reply(99_999, 5, "[]", "much easier"), COMBINED_TEMPLATE);

        assertEquals(LlmLevelDirector.MAX_GRAVITY_MILLIS, level.gravityMillis());
    }

    @Test
    void clampsAQuotaThatCouldNotBeFinished() throws Exception {
        Level level = directed(reply(500, 500, "[]", "many groceries"), COMBINED_TEMPLATE);

        assertEquals(LlmLevelDirector.MAX_ITEMS, level.items().size());
    }

    @Test
    void clampsAQuotaSoSmallTheLevelWouldBeTrivial() throws Exception {
        Level level = directed(reply(500, 0, "[]", "barely anything"), COMBINED_TEMPLATE);

        assertEquals(LlmLevelDirector.MIN_ITEMS, level.items().size());
    }

    @Test
    void clampsANegativeQuota() throws Exception {
        Level level = directed(reply(500, -3, "[]", "negative"), COMBINED_TEMPLATE);

        assertEquals(LlmLevelDirector.MIN_ITEMS, level.items().size());
    }

    // ------------------------------------------------------ mode-aware fields

    @Test
    void aTetrisOnlyLevelKeepsItsEmptyQuotaHoweverManyItemsAreAsked() throws Exception {
        Level level = directed(reply(400, 8, "[\"MEAT\"]", "tighter"), TETRIS_TEMPLATE);

        assertEquals(400, level.gravityMillis(), "gravity is the only knob that means anything");
        assertTrue(level.items().isEmpty(), "there is nothing to sort on that level");
    }

    @Test
    void aSortOnlyLevelKeepsItsZeroGravity() throws Exception {
        Level level = directed(reply(350, 5, "[]", "more to sort"), SORT_TEMPLATE);

        assertEquals(0, level.gravityMillis(), "there is no board to fall on");
        assertEquals(5, level.items().size());
    }

    // -------------------------------------------------------------- categories

    @Test
    void honoursAnEmphasisedCategory() throws Exception {
        Level level = directed(reply(500, 3, "[\"FROZEN\"]", "cold run"), COMBINED_TEMPLATE);

        long frozen = level.items().stream()
                .filter(i -> i.category() == FoodCategory.FROZEN)
                .count();
        assertTrue(frozen >= 2, "both frozen items should have been drawn first");
    }

    @Test
    void ignoresACategoryThatDoesNotExist() throws Exception {
        Level level = directed(
                reply(500, 4, "[\"CONDIMENTS\",\"DAIRY\"]", "mixed"), COMBINED_TEMPLATE);

        assertEquals(4, level.items().size(), "an unknown preference is dropped, not fatal");
    }

    @Test
    void ignoresNonStringCategoryEntries() throws Exception {
        Level level = directed(reply(500, 4, "[7,true]", "confused"), COMBINED_TEMPLATE);

        assertEquals(4, level.items().size());
    }

    @Test
    void toleratesCategoriesBeingTheWrongTypeEntirely() throws Exception {
        Level level = directed(
                "{\"gravityMillis\":500,\"itemCount\":4,\"categories\":\"DAIRY\",\"reason\":\"x\"}",
                COMBINED_TEMPLATE);

        assertEquals(4, level.items().size());
    }

    // ----------------------------------------------------------------- failure

    @Test
    void anUnreachableEndpointYieldsTheAuthoredLevel() throws Exception {
        LlmLevelDirector director = new LlmLevelDirector(
                (system, user) -> CompletableFuture.failedFuture(
                        new IllegalStateException("endpoint unreachable")),
                7L);

        Level level = director.nextLevel(COMBINED_TEMPLATE, STATS).get();

        assertSame(COMBINED_TEMPLATE, level,
                "progression must not depend on a network call succeeding");
    }

    @Test
    void aReplyThatIsNotJsonYieldsTheAuthoredLevel() throws Exception {
        assertSame(COMBINED_TEMPLATE,
                directed("I would make it a bit easier", COMBINED_TEMPLATE));
    }

    @Test
    void missingFieldsFallBackToTheAuthoredValues() throws Exception {
        Level level = directed("{\"reason\":\"no numbers at all\"}", COMBINED_TEMPLATE);

        assertEquals(COMBINED_TEMPLATE.gravityMillis(), level.gravityMillis());
        assertEquals(COMBINED_TEMPLATE.items().size(), level.items().size());
    }

    @Test
    void aReplyWithNoReasonStillApplies() throws Exception {
        Level level = directed(
                "{\"gravityMillis\":700,\"itemCount\":4,\"categories\":[]}", COMBINED_TEMPLATE);

        assertEquals(700, level.gravityMillis());
    }

    // ------------------------------------------------------------------ schema

    @Test
    void theSchemaBoundsMatchTheJavaClamps() {
        var properties = LlmLevelDirector.TUNING_SCHEMA.path("properties");

        assertEquals(LlmLevelDirector.MIN_GRAVITY_MILLIS,
                properties.path("gravityMillis").path("minimum").asInt());
        assertEquals(LlmLevelDirector.MAX_GRAVITY_MILLIS,
                properties.path("gravityMillis").path("maximum").asInt());
        assertEquals(LlmLevelDirector.MIN_ITEMS, properties.path("itemCount").path("minimum").asInt());
        assertEquals(LlmLevelDirector.MAX_ITEMS, properties.path("itemCount").path("maximum").asInt());
    }

    @Test
    void theSchemaOnlyAllowsRealFoodCategories() {
        String allowed = LlmLevelDirector.TUNING_SCHEMA
                .path("properties").path("categories").path("items").path("enum").toString();

        for (FoodCategory category : FoodCategory.values()) {
            assertTrue(allowed.contains(category.name()), "missing " + category);
        }
    }

    // ------------------------------------------------------------------ prompt

    @Test
    void thePromptCarriesTheMeasuredPerformanceAndTheBounds() {
        LevelStats stats = new LevelStats(LevelMode.COMBINED, 40, 6, 5, 2, 1, 12, 180);

        String prompt = LlmLevelDirector.describe(COMBINED_TEMPLATE, stats);

        assertTrue(prompt.contains("40"), "pieces placed");
        assertTrue(prompt.contains("rows cleared: 6"));
        assertTrue(prompt.contains("5 correct, 2 wrong"));
        assertTrue(prompt.contains("topped out: 1"));
        assertTrue(prompt.contains("12s of 180s"));
        assertTrue(prompt.contains(String.valueOf(LlmLevelDirector.MIN_GRAVITY_MILLIS)));
        assertTrue(prompt.contains(String.valueOf(LlmLevelDirector.MAX_ITEMS)));
    }

    @Test
    void thePromptSaysSoWhenThereWasNothingToSort() {
        String prompt = LlmLevelDirector.describe(TETRIS_TEMPLATE, STATS);

        assertTrue(prompt.contains("no sorting"),
                "a 100% accuracy reading from zero drops would be misleading");
    }

    @Test
    void thePromptWarnsThatATetrisOnlyLevelAlwaysEndsWithAnEmptyClock() {
        // Observed live: the model read the inevitable "0s left" as a near miss and eased
        // the gravity every time, which is the opposite of what the numbers meant.
        String prompt = LlmLevelDirector.describe(COMBINED_TEMPLATE, STATS);

        assertTrue(prompt.contains("IGNORE THE CLOCK"),
                "the clock margin is meaningless after a Tetris-only level");
        assertTrue(prompt.contains("rows per piece"),
                "it should be pointed at the numbers that do mean something");
    }

    @Test
    void thatWarningIsAbsentWhenTheClockMarginIsMeaningful() {
        LevelStats sortStats = new LevelStats(LevelMode.SORT_ONLY, 0, 0, 4, 0, 0, 55, 60);

        String prompt = LlmLevelDirector.describe(COMBINED_TEMPLATE, sortStats);

        assertTrue(!prompt.contains("IGNORE THE CLOCK"),
                "55 of 60 seconds spare is a real signal and must not be explained away");
    }

    @Test
    void aReasonIsGivenRoomForAWholeSentence() {
        // At 120 the schema's hard cut landed mid-word: "...I am increasing it by maximizing 'T"
        int cap = LlmLevelDirector.TUNING_SCHEMA
                .path("properties").path("reason").path("maxLength").asInt();

        assertTrue(cap >= 160, "a reason truncated mid-word reads as a crash, not a decision");
    }

    @Test
    void thePromptStatesTheClockIsNotNegotiable() {
        String prompt = LlmLevelDirector.describe(COMBINED_TEMPLATE, STATS);

        assertTrue(prompt.contains("cannot change"));
    }

    // -------------------------------------------------------------------- seed

    @Test
    void theSameReplyAndSeedProduceTheSameQuota() {
        String reply = reply(500, 5, "[\"MEAT\"]", "same");

        List<GroceryItem> first =
                LlmLevelDirector.apply(COMBINED_TEMPLATE, reply, 5L).items();
        List<GroceryItem> second =
                LlmLevelDirector.apply(COMBINED_TEMPLATE, reply, 5L).items();

        assertEquals(first, second);
    }

    @Test
    void differentSeedsCanProduceDifferentQuotas() {
        String reply = reply(500, 5, "[]", "any");

        assertNotEquals(
                LlmLevelDirector.apply(COMBINED_TEMPLATE, reply, 1L).items(),
                LlmLevelDirector.apply(COMBINED_TEMPLATE, reply, 12345L).items());
    }
}
