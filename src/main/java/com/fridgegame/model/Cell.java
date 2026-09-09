package com.fridgegame.model;

/**
 * One square of a {@link TetrisBoard}.
 *
 * <p>Kept separate from {@link Tetromino} because a locked square can also be
 * {@link #GARBAGE} — a filled square with no piece identity, which is what
 * {@link TetrisBoard#setStack} produces since text fixtures cannot carry piece colours.
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
