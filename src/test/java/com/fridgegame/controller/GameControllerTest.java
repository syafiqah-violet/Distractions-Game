package com.fridgegame.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fridgegame.model.FoodCategory;
import com.fridgegame.model.GameState;
import com.fridgegame.model.GroceryItem;
import com.fridgegame.model.StorageZone;
import org.junit.jupiter.api.Test;

class GameControllerTest {

    private static final GroceryItem LETTUCE =
            new GroceryItem("lettuce", "Lettuce", "🥬", FoodCategory.PRODUCE);

    @Test
    void correctDropFromZeroStreakAwardsBasePoints() {
        GameState state = new GameState();
        GameController controller = new GameController(state);

        boolean correct = controller.handleDrop(LETTUCE, StorageZone.CRISPER);

        assertTrue(correct);
        assertEquals(10, state.getScore());
        assertEquals(1, state.getStreak());
        assertEquals(GameState.STARTING_LIVES, state.getLives());
    }

    @Test
    void correctDropAtStreakFiveDoublesTheBonus() {
        GameState state = new GameState();
        state.setStreak(5);
        GameController controller = new GameController(state);

        controller.handleDrop(LETTUCE, StorageZone.CRISPER);

        assertEquals(20, state.getScore());
        assertEquals(6, state.getStreak());
    }

    @Test
    void wrongDropPenalizesScoreResetsStreakAndCostsALife() {
        GameState state = new GameState();
        state.setScore(50);
        state.setStreak(3);
        GameController controller = new GameController(state);

        boolean correct = controller.handleDrop(LETTUCE, StorageZone.TOP_SHELF);

        assertFalse(correct);
        assertEquals(45, state.getScore());
        assertEquals(0, state.getStreak());
        assertEquals(GameState.STARTING_LIVES - 1, state.getLives());
    }
}
