package com.fridgegame.model;

/**
 * One square of a {@link TetrisBoard}.
 *
 * <p>Kept separate from {@link Tetromino} because a locked square can also be
 * {@link #GARBAGE} — a row pushed up from the opponent, which belongs to no piece.
 * The model stays colour-blind; the view maps these constants to paints.
 */
public enum Cell {
    EMPTY,
    GARBAGE,
    I,
    J,
    L,
    O,
    S,
    T,
    Z
}
