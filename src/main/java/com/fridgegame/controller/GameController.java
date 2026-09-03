package com.fridgegame.controller;

import com.fridgegame.model.GameState;
import com.fridgegame.model.GroceryItem;
import com.fridgegame.model.StorageZone;

/** Correct/wrong drop rules and scoring (Phase 4). Level flow and game-over are Phase 5. */
public class GameController {

    private static final int CORRECT_BASE_POINTS = 10;
    private static final int WRONG_PENALTY = 5;
    private static final int STREAK_DIVISOR = 5;

    private final GameState state;

    public GameController(GameState state) {
        this.state = state;
    }

    /** Applies scoring rules for dropping {@code item} into {@code zone}; returns whether it was correct. */
    public boolean handleDrop(GroceryItem item, StorageZone zone) {
        boolean correct = zone.accepts(item);
        if (correct) {
            int bonus = CORRECT_BASE_POINTS * (1 + state.getStreak() / STREAK_DIVISOR);
            state.setScore(state.getScore() + bonus);
            state.setStreak(state.getStreak() + 1);
        } else {
            state.setScore(state.getScore() - WRONG_PENALTY);
            state.setStreak(0);
            state.setLives(state.getLives() - 1);
        }
        return correct;
    }
}
