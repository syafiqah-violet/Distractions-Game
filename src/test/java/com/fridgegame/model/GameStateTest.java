package com.fridgegame.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class GameStateTest {

    @Test
    void defaultsAreZeroScoreFullLivesLevelOne() {
        GameState state = new GameState();

        assertEquals(0, state.getScore());
        assertEquals(GameState.STARTING_LIVES, state.getLives());
        assertEquals(0, state.getStreak());
        assertEquals(1, state.getLevel());
    }

    @Test
    void settingScorePropertyIsReflectedByGetter() {
        GameState state = new GameState();

        state.scoreProperty().set(30);

        assertEquals(30, state.getScore());
    }

    @Test
    void startLevelLoadsNumberAndTimeLimit() {
        GameState state = new GameState();
        Level level = new Level(2, LevelMode.COMBINED, List.of(), 50);

        state.startLevel(level);

        assertEquals(2, state.getLevel());
        assertEquals(50, state.getSecondsLeft());
    }

    @Test
    void startLevelWithNoQuotaLeavesNothingToSort() {
        GameState state = new GameState();
        state.setItemsLeft(6);

        state.startLevel(new Level(2, LevelMode.TETRIS_ONLY, List.of(), 60));

        assertEquals(0, state.getItemsLeft(), "the Tetris-only level has no groceries at all");
        assertEquals(0, state.getRowsCleared());
    }

    @Test
    void resetRestoresScoreLivesAndStreakButKeepsLevel() {
        GameState state = new GameState();
        state.setScore(100);
        state.setLives(0);
        state.setStreak(5);
        state.setLevel(3);

        state.reset();

        assertEquals(0, state.getScore());
        assertEquals(GameState.STARTING_LIVES, state.getLives());
        assertEquals(0, state.getStreak());
        assertEquals(3, state.getLevel());
    }
}
