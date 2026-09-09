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
 * What the clock hitting zero means, which is now mode-dependent.
 *
 * <p>The asymmetry is the whole point of the level structure: on a sorting level the clock
 * is a deadline you can miss, while on the Tetris-only level surviving it <i>is</i> the
 * objective. Getting that backwards would make level 2 unwinnable or level 1 impossible
 * to fail, so it is worth pinning down.
 */
class LevelModeFlowTest {

    private static final GroceryItem MILK =
            new GroceryItem("milk", "Milk", "/icons/milk.png", FoodCategory.DAIRY);

    private final GameState state = new GameState();
    private final GameController controller = new GameController(state);

    private boolean over;
    private int completedWithBonus = -1;

    LevelModeFlowTest() {
        controller.setOnGameOver(() -> over = true);
        controller.setOnLevelComplete(bonus -> completedWithBonus = bonus);
    }

    /** Runs the clock down to zero from a one-second level. */
    private void expireClock() {
        state.setSecondsLeft(1);
        controller.tick();
    }

    // ------------------------------------------------------------ TETRIS_ONLY

    @Test
    void tetrisOnlyPassesWhenTheClockExpiresWithTheRequiredRowsCleared() {
        controller.startLevel(new Level(2, LevelMode.TETRIS_ONLY, List.of(), 60, 550, 1));
        controller.awardClearedRows(1);

        expireClock();

        assertTrue(completedWithBonus >= 0, "surviving the minute is the objective");
        assertEquals(0, completedWithBonus, "the whole clock was used, so no time bonus");
        assertFalse(over);
    }

    @Test
    void tetrisOnlyFailsWhenTheClockExpiresWithNoRowsCleared() {
        controller.startLevel(new Level(2, LevelMode.TETRIS_ONLY, List.of(), 60, 550, 1));

        expireClock();

        assertTrue(over, "parking pieces in a corner for a minute must not pass");
        assertEquals(-1, completedWithBonus);
    }

    @Test
    void tetrisOnlyDoesNotEndEarlyOnceTheRowQuotaIsMet() {
        controller.startLevel(new Level(2, LevelMode.TETRIS_ONLY, List.of(), 60, 550, 1));
        state.setSecondsLeft(30);

        controller.awardClearedRows(4);
        controller.tick();

        assertEquals(-1, completedWithBonus, "the level runs the full clock to bank more score");
        assertFalse(over);
        assertEquals(29, state.getSecondsLeft());
    }

    @Test
    void tetrisOnlyWithNoRowRequirementPassesOnSurvivalAlone() {
        controller.startLevel(new Level(2, LevelMode.TETRIS_ONLY, List.of(), 60, 550, 0));

        expireClock();

        assertTrue(completedWithBonus >= 0);
        assertFalse(over);
    }

    // ------------------------------------------------- SORT_ONLY and COMBINED

    @Test
    void sortOnlyFailsWhenTheClockExpiresWithItemsStillUnsorted() {
        controller.startLevel(new Level(1, LevelMode.SORT_ONLY, List.of(MILK), 60));
        assertEquals(1, state.getItemsLeft());

        expireClock();

        assertTrue(over, "unsorted groceries at zero is a loss");
        assertEquals(-1, completedWithBonus);
    }

    @Test
    void combinedFailsWhenTheClockExpiresWithItemsStillUnsorted() {
        controller.startLevel(new Level(3, LevelMode.COMBINED, List.of(MILK), 180));

        expireClock();

        assertTrue(over);
        assertEquals(-1, completedWithBonus);
    }

    @Test
    void aLevelWithNoModeContextStillFailsSafeOnTimeout() {
        // No startLevel: the controller must not assume a mode it was never told about.
        expireClock();

        assertTrue(over);
    }

    @Test
    void tickAboveZeroDecidesNothing() {
        controller.startLevel(new Level(2, LevelMode.TETRIS_ONLY, List.of(), 60, 550, 1));
        state.setSecondsLeft(5);

        controller.tick();

        assertEquals(4, state.getSecondsLeft());
        assertFalse(over);
        assertEquals(-1, completedWithBonus);
    }
}
