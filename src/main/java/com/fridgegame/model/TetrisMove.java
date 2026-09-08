package com.fridgegame.model;

/**
 * A placement decision from a Tetris agent: rotate the current piece to
 * {@code rotation}, slide it so its leftmost occupied square sits in
 * {@code column}, then hard-drop.
 *
 * <p>This is exactly the shape the LLM is constrained to emit via guided
 * decoding, so it doubles as the wire format.
 */
public record TetrisMove(int rotation, int column) {
}
