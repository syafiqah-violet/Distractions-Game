package com.fridgegame.model;

/**
 * Which of the game's mechanics a level actually asks for.
 *
 * <p>The first two levels each teach one mechanic on its own — meeting Tetris,
 * drag-and-drop and a shared clock all at once is what made the original single mode
 * hard to learn. Only {@link #COMBINED} joins them up, and {@link #MEMORY} teaches a
 * third mechanic of its own after they have been combined.
 */
public enum LevelMode {

    /** Drag-and-drop only. The whole quota is on the counter from the start; no board. */
    SORT_ONLY,

    /** Tetris only. No counter and no fridge; rows score points instead of buying groceries. */
    TETRIS_ONLY,

    /** Both: clear a row to earn a grocery, then sort it before the clock runs out. */
    COMBINED,

    /** Mix and match. A grid of face-down grocery cards, flipped two at a time. */
    MEMORY;

    /**
     * Whether this mode puts a Tetris board on screen and runs gravity.
     *
     * <p>Spelled as a positive list rather than {@code != SORT_ONLY}. A negation would hand
     * every mode added later both mechanics by default, silently — a memory level would boot
     * with a Tetris board beside its cards and nobody would have written a line of code
     * saying so.
     */
    public boolean hasTetris() {
        return this == TETRIS_ONLY || this == COMBINED;
    }

    /** Whether this mode shows the counter and the fridge. */
    public boolean hasSorting() {
        return this == SORT_ONLY || this == COMBINED;
    }

    /** Whether this mode shows the mix-and-match card grid. */
    public boolean hasMemory() {
        return this == MEMORY;
    }
}
