package com.fridgegame.controller;

import com.fridgegame.model.GameState;
import com.fridgegame.model.GroceryItem;
import com.fridgegame.model.Level;
import com.fridgegame.model.StorageZone;
import java.util.function.IntConsumer;

/** Correct/wrong drop rules, scoring, level completion and the countdown tick (Phase 4 + 5). */
public class GameController {

    private static final int CORRECT_BASE_POINTS = 10;
    private static final int WRONG_PENALTY = 5;
    private static final int STREAK_DIVISOR = 5;
    private static final int TIME_BONUS_PER_SECOND = 2;

    private final GameState state;
    private int itemsRemaining;
    private IntConsumer onLevelComplete = bonus -> { };
    private Runnable onGameOver = () -> { };

    public GameController(GameState state) {
        this.state = state;
    }

    /** Loads a new level's items/time limit and resets the counter-empties tracker. */
    public void startLevel(Level level) {
        state.startLevel(level);
        itemsRemaining = level.items().size();
    }

    public void setOnLevelComplete(IntConsumer listener) {
        this.onLevelComplete = listener;
    }

    public void setOnGameOver(Runnable listener) {
        this.onGameOver = listener;
    }

    /** Applies scoring rules for dropping {@code item} into {@code zone}; returns whether it was correct. */
    public boolean handleDrop(GroceryItem item, StorageZone zone) {
        boolean correct = zone.accepts(item);
        if (correct) {
            int bonus = CORRECT_BASE_POINTS * (1 + state.getStreak() / STREAK_DIVISOR);
            state.setScore(state.getScore() + bonus);
            state.setStreak(state.getStreak() + 1);
            itemsRemaining--;
            if (itemsRemaining == 0) {
                int timeBonus = state.getSecondsLeft() * TIME_BONUS_PER_SECOND;
                state.setScore(state.getScore() + timeBonus);
                onLevelComplete.accept(timeBonus);
            }
        } else {
            state.setScore(state.getScore() - WRONG_PENALTY);
            state.setStreak(0);
            state.setLives(state.getLives() - 1);
            if (state.getLives() <= 0) {
                onGameOver.run();
            }
        }
        return correct;
    }

    /** Advances the countdown by one second; triggers game over once it hits 0. */
    public void tick() {
        state.setSecondsLeft(state.getSecondsLeft() - 1);
        if (state.getSecondsLeft() <= 0) {
            onGameOver.run();
        }
    }
}
