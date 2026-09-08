package com.fridgegame.controller;

import com.fridgegame.model.GameState;
import com.fridgegame.model.GroceryItem;
import com.fridgegame.model.Level;
import com.fridgegame.model.StorageZone;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/** Correct/wrong drop rules, scoring, level completion and the countdown tick (Phase 4 + 5). */
public class GameController {

    private static final int CORRECT_BASE_POINTS = 10;
    private static final int WRONG_PENALTY = 5;
    private static final int STREAK_DIVISOR = 5;
    private static final int TIME_BONUS_PER_SECOND = 2;

    /** Consolation points for a row cleared after the whole quota is already on the counter. */
    private static final int SURPLUS_ROW_POINTS = 5;

    private final GameState state;

    /** The level's quota, waiting to be earned one cleared row at a time. */
    private final Deque<GroceryItem> locked = new ArrayDeque<>();

    private IntConsumer onLevelComplete = bonus -> { };
    private Runnable onGameOver = () -> { };
    private Consumer<GroceryItem> onItemUnlocked = item -> { };

    public GameController(GameState state) {
        this.state = state;
    }

    /** Loads a new level's quota and time limit; nothing is on the counter until rows are cleared. */
    public void startLevel(Level level) {
        state.startLevel(level);
        locked.clear();
        locked.addAll(level.items());
    }

    public void setOnLevelComplete(IntConsumer listener) {
        this.onLevelComplete = listener;
    }

    public void setOnGameOver(Runnable listener) {
        this.onGameOver = listener;
    }

    /** Fired once per grocery item released onto the counter by a cleared row. */
    public void setOnItemUnlocked(Consumer<GroceryItem> listener) {
        this.onItemUnlocked = listener;
    }

    /**
     * Pays out {@code rows} cleared rows: one grocery item each, in level order.
     *
     * <p>Rows cleared after the whole quota is already on the counter are not wasted —
     * they pay {@link #SURPLUS_ROW_POINTS} instead, so a late four-row clear still counts
     * for something.
     *
     * @return how many items were actually released
     */
    public int awardClearedRows(int rows) {
        state.setRowsCleared(state.getRowsCleared() + rows);
        int released = 0;
        for (int i = 0; i < rows; i++) {
            GroceryItem item = locked.poll();
            if (item == null) {
                state.setScore(state.getScore() + SURPLUS_ROW_POINTS);
            } else {
                onItemUnlocked.accept(item);
                released++;
            }
        }
        return released;
    }

    /**
     * How many garbage rows an {@code rowsCleared}-row clear sends to the other board.
     *
     * <p>Standard versus-Tetris convention: N−1. A single line is harmless, so the
     * opponent has to actually stack for a multi-line clear to hurt you.
     */
    public static int garbageFor(int rowsCleared) {
        return Math.max(0, rowsCleared - 1);
    }

    /** Applies scoring rules for dropping {@code item} into {@code zone}; returns whether it was correct. */
    public boolean handleDrop(GroceryItem item, StorageZone zone) {
        boolean correct = zone.accepts(item);
        if (correct) {
            int bonus = CORRECT_BASE_POINTS * (1 + state.getStreak() / STREAK_DIVISOR);
            state.setScore(state.getScore() + bonus);
            state.setStreak(state.getStreak() + 1);
            state.setItemsLeft(state.getItemsLeft() - 1);
            if (state.getItemsLeft() == 0) {
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

    /**
     * Charges a life for letting the Tetris stack reach the top.
     *
     * <p>Deliberately the same penalty shape as a wrong drop — lose a life, lose the
     * streak — so topping out is a setback rather than an instant loss. The caller
     * clears the board afterwards if any lives remain.
     */
    public void penalizeTopOut() {
        state.setStreak(0);
        state.setLives(state.getLives() - 1);
        if (state.getLives() <= 0) {
            onGameOver.run();
        }
    }

    /** Advances the countdown by one second; triggers game over once it hits 0. */
    public void tick() {
        state.setSecondsLeft(state.getSecondsLeft() - 1);
        if (state.getSecondsLeft() <= 0) {
            onGameOver.run();
        }
    }
}
