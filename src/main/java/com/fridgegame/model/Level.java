package com.fridgegame.model;

import java.util.List;

/**
 * One round: which mechanic it asks for, the groceries involved, the clock, and the
 * Tetris difficulty.
 *
 * @param mode             which halves of the game are live — see {@link LevelMode}
 * @param items            the quota. On {@link LevelMode#SORT_ONLY} they start on the
 *                         counter; on {@link LevelMode#COMBINED} each one must first be
 *                         earned by clearing a row. Empty on {@link LevelMode#TETRIS_ONLY}.
 * @param timeLimitSeconds the countdown; the level's whole budget
 * @param gravityMillis    how long the player's piece takes to fall one row (0 when there
 *                         is no board)
 * @param requiredRows     rows that must be cleared for the level to be survivable. Only
 *                         meaningful on {@link LevelMode#TETRIS_ONLY}, where the clock
 *                         running out is a pass rather than a fail — provided you cleared
 *                         at least this many.
 */
public record Level(
        int number,
        LevelMode mode,
        List<GroceryItem> items,
        int timeLimitSeconds,
        int gravityMillis,
        int requiredRows) {

    private static final int DEFAULT_GRAVITY_MILLIS = 700;

    /** Gentle defaults for the Tetris knobs — used by tests that only care about scoring. */
    public Level(int number, LevelMode mode, List<GroceryItem> items, int timeLimitSeconds) {
        this(number, mode, items, timeLimitSeconds, DEFAULT_GRAVITY_MILLIS, 0);
    }

    /**
     * A copy with the difficulty retuned — the only surface the level director may change.
     *
     * <p>Number, mode, clock and {@link #requiredRows} are deliberately not retunable:
     * those are the level design, and letting a model rewrite them would let it turn
     * level 2 into something other than a Tetris tutorial.
     */
    public Level withTuning(int newGravityMillis, List<GroceryItem> newItems) {
        return new Level(number, mode, List.copyOf(newItems),
                timeLimitSeconds, newGravityMillis, requiredRows);
    }
}
