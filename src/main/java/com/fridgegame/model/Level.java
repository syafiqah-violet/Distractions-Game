package com.fridgegame.model;

import java.util.List;

/**
 * One round: the groceries you must earn and sort, the clock, and the Tetris difficulty.
 *
 * @param items            the quota — every one must be earned by clearing a row and then
 *                         sorted into the right zone before the level completes
 * @param timeLimitSeconds shared countdown for both halves of the screen
 * @param gravityMillis    how long the player's piece takes to fall one row
 * @param aiGravityMillis  the agent's piece cadence, and its per-move decision deadline
 * @param sendsGarbage     whether the agent's line clears push garbage rows at the player
 */
public record Level(
        int number,
        List<GroceryItem> items,
        int timeLimitSeconds,
        int gravityMillis,
        int aiGravityMillis,
        boolean sendsGarbage) {

    private static final int DEFAULT_GRAVITY_MILLIS = 700;
    private static final int DEFAULT_AI_GRAVITY_MILLIS = 2000;

    /** Gentle defaults for the Tetris knobs — used by tests that only care about scoring. */
    public Level(int number, List<GroceryItem> items, int timeLimitSeconds) {
        this(number, items, timeLimitSeconds,
                DEFAULT_GRAVITY_MILLIS, DEFAULT_AI_GRAVITY_MILLIS, false);
    }
}
