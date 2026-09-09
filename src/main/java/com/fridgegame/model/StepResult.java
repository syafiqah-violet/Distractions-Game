package com.fridgegame.model;

/**
 * What one gravity step or hard drop did.
 *
 * @param locked      the falling piece came to rest and became part of the stack
 * @param rowsCleared full rows removed by that lock — the number of grocery items earned
 * @param toppedOut   the next piece had nowhere to spawn; this board is dead until cleared
 */
public record StepResult(boolean locked, int rowsCleared, boolean toppedOut) {

    /** The piece simply fell one row; nothing else happened. */
    public static final StepResult FELL = new StepResult(false, 0, false);
}
