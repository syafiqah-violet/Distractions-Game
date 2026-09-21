package com.fridgegame.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fridgegame.model.FoodCategory;
import com.fridgegame.model.GameState;
import com.fridgegame.model.GroceryItem;
import com.fridgegame.model.Level;
import com.fridgegame.model.LevelMode;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * What a matched and a mismatched pair are worth on the mix-and-match level.
 *
 * <p>Pinned down separately from the drag-and-drop rules because the two mechanics are
 * deliberately scored the same way, and "deliberately the same" is exactly the kind of
 * intention that decays silently. A match must be worth what a correct drop is worth,
 * streak multiplier included, or the HUD's streak means two different things depending on
 * which level you are looking at.
 */
class MemoryScoringTest {

    private static final GroceryItem MILK =
            new GroceryItem("milk", "Milk", "/icons/milk.png", FoodCategory.DAIRY);
    private static final GroceryItem LETTUCE =
            new GroceryItem("lettuce", "Lettuce", "/icons/lettuce.png", FoodCategory.PRODUCE);
    private static final GroceryItem STEAK =
            new GroceryItem("steak", "Steak", "/icons/steak.png", FoodCategory.MEAT);

    private final GameState state = new GameState();
    private final GameController controller = new GameController(state);

    private boolean over;
    private int completedWithBonus = -1;

    MemoryScoringTest() {
        controller.setOnGameOver(() -> over = true);
        controller.setOnLevelComplete(bonus -> completedWithBonus = bonus);
    }

    /** A three-pair memory level, as level 4 is but smaller. */
    private void startMemoryLevel() {
        controller.startLevel(new Level(4, LevelMode.MEMORY,
                List.of(MILK, LETTUCE, STEAK), 60, 0, 0));
    }

    // ----------------------------------------------------------------- pairs

    @Test
    void theItemsOfAMemoryLevelAreThePairsToFind() {
        startMemoryLevel();

        assertEquals(3, state.getItemsLeft(), "three items means three pairs, not three cards");
        assertEquals(60, state.getSecondsLeft());
    }

    @Test
    void aMatchedPairPaysTheSameAsACorrectDrop() {
        startMemoryLevel();

        controller.awardMatchedPair();

        assertEquals(10, state.getScore());
        assertEquals(1, state.getStreak());
        assertEquals(2, state.getItemsLeft());
    }

    @Test
    void matchesRaiseTheStreakMultiplierTheSameWayDropsDo() {
        controller.startLevel(new Level(4, LevelMode.MEMORY,
                List.of(MILK, LETTUCE, STEAK), 60, 0, 0));
        // Arrive at the level with a streak already running, as a player would after sorting.
        state.setStreak(5);

        controller.awardMatchedPair();

        assertEquals(20, state.getScore(), "a streak of 5 doubles the base 10");
    }

    @Test
    void theLastPairCompletesTheLevelWithATimeBonus() {
        startMemoryLevel();
        state.setSecondsLeft(20);

        controller.awardMatchedPair();
        controller.awardMatchedPair();
        assertEquals(-1, completedWithBonus, "two of three pairs does not finish the level");

        controller.awardMatchedPair();

        assertEquals(0, state.getItemsLeft());
        assertEquals(40, completedWithBonus, "2 points per second left");
        assertFalse(over);
    }

    // ------------------------------------------------------------ mismatches

    @Test
    void aMismatchCostsPointsTheStreakAndALife() {
        startMemoryLevel();
        controller.awardMatchedPair();
        int before = state.getScore();

        controller.penalizeMismatch();

        assertEquals(before - 5, state.getScore());
        assertEquals(0, state.getStreak());
        assertEquals(2, state.getLives(), "the same cost as putting a grocery in the wrong zone");
        assertFalse(over);
    }

    @Test
    void aMismatchDoesNotUnmatchAnything() {
        startMemoryLevel();
        controller.awardMatchedPair();

        controller.penalizeMismatch();

        assertEquals(2, state.getItemsLeft(), "pairs already found stay found");
    }

    @Test
    void theThirdMismatchEndsTheRun() {
        startMemoryLevel();

        controller.penalizeMismatch();
        controller.penalizeMismatch();
        assertFalse(over);

        controller.penalizeMismatch();

        assertEquals(0, state.getLives());
        assertTrue(over);
    }

    // ---------------------------------------------------------------- clocks

    @Test
    void pairsStillOnTheBoardWhenTheClockExpiresIsALoss() {
        startMemoryLevel();
        controller.awardMatchedPair();
        controller.awardMatchedPair();
        state.setSecondsLeft(1);

        controller.tick();

        assertTrue(over, "finding some of the pairs is not finishing the level");
        assertEquals(-1, completedWithBonus);
    }

    // ---------------------------------------------------------------- paused

    @Test
    void aPausedBoardScoresNothingEitherWay() {
        startMemoryLevel();
        controller.setPaused(true);

        controller.awardMatchedPair();
        controller.penalizeMismatch();

        assertEquals(0, state.getScore());
        assertEquals(3, state.getItemsLeft());
        assertEquals(3, state.getLives());
    }

    /** Mismatches count as wrong drops, so the director sees one measure of "got it wrong". */
    @Test
    void mismatchesReachTheDirectorAsMistakes() {
        startMemoryLevel();

        controller.penalizeMismatch();
        controller.awardMatchedPair();

        assertEquals(1, controller.snapshot(0).wrongDrops());
        assertEquals(1, controller.snapshot(0).correctDrops());
    }

    @Test
    void mistakesDoNotCarryBetweenLevels() {
        startMemoryLevel();
        controller.penalizeMismatch();

        startMemoryLevel();

        assertEquals(0, controller.snapshot(0).wrongDrops());
    }
}
