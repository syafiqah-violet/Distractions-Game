package com.fridgegame.model;

/**
 * Which of the game's two mechanics a level actually asks for.
 *
 * <p>The first two levels each teach one mechanic on its own — meeting Tetris,
 * drag-and-drop and a shared clock all at once is what made the original single mode
 * hard to learn. Only {@link #COMBINED} joins them up.
 */
public enum LevelMode {

    /** Drag-and-drop only. The whole quota is on the counter from the start; no board. */
    SORT_ONLY,

    /** Tetris only. No counter and no fridge; rows score points instead of buying groceries. */
    TETRIS_ONLY,

    /** Both: clear a row to earn a grocery, then sort it before the clock runs out. */
    COMBINED;

    /** Whether this mode puts a Tetris board on screen and runs gravity. */
    public boolean hasTetris() {
        return this != SORT_ONLY;
    }

    /** Whether this mode shows the counter and the fridge. */
    public boolean hasSorting() {
        return this != TETRIS_ONLY;
    }
}
