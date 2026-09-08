package com.fridgegame.model;

import java.util.List;

/**
 * The seven tetrominoes and their four rotation states.
 *
 * <p>Each rotation is a list of {@code {row, col}} offsets, <b>normalised</b> so the
 * smallest occupied row and the smallest occupied column are both 0. Rows increase
 * downward, matching {@link TetrisBoard}'s grid.
 *
 * <p>Normalisation is what makes {@link TetrisMove#column()} mean "leftmost occupied
 * column" regardless of rotation — an agent can say "column 7" without knowing which
 * cells of a 3×3 box the piece actually fills. It also means rotation never needs
 * SRS wall kicks: {@link TetrisBoard} simply refuses a rotation that wouldn't fit,
 * which is forgiving enough for a 60-second round.
 */
public enum Tetromino {

    I(Cell.I,
            offsets(0, 0, 0, 1, 0, 2, 0, 3),
            offsets(0, 0, 1, 0, 2, 0, 3, 0),
            offsets(0, 0, 0, 1, 0, 2, 0, 3),
            offsets(0, 0, 1, 0, 2, 0, 3, 0)),

    J(Cell.J,
            offsets(0, 0, 1, 0, 1, 1, 1, 2),
            offsets(0, 0, 0, 1, 1, 0, 2, 0),
            offsets(0, 0, 0, 1, 0, 2, 1, 2),
            offsets(0, 1, 1, 1, 2, 0, 2, 1)),

    L(Cell.L,
            offsets(0, 2, 1, 0, 1, 1, 1, 2),
            offsets(0, 0, 1, 0, 2, 0, 2, 1),
            offsets(0, 0, 0, 1, 0, 2, 1, 0),
            offsets(0, 0, 0, 1, 1, 1, 2, 1)),

    O(Cell.O,
            offsets(0, 0, 0, 1, 1, 0, 1, 1),
            offsets(0, 0, 0, 1, 1, 0, 1, 1),
            offsets(0, 0, 0, 1, 1, 0, 1, 1),
            offsets(0, 0, 0, 1, 1, 0, 1, 1)),

    S(Cell.S,
            offsets(0, 1, 0, 2, 1, 0, 1, 1),
            offsets(0, 0, 1, 0, 1, 1, 2, 1),
            offsets(0, 1, 0, 2, 1, 0, 1, 1),
            offsets(0, 0, 1, 0, 1, 1, 2, 1)),

    T(Cell.T,
            offsets(0, 1, 1, 0, 1, 1, 1, 2),
            offsets(0, 0, 1, 0, 1, 1, 2, 0),
            offsets(0, 0, 0, 1, 0, 2, 1, 1),
            offsets(0, 1, 1, 0, 1, 1, 2, 1)),

    Z(Cell.Z,
            offsets(0, 0, 0, 1, 1, 1, 1, 2),
            offsets(0, 1, 1, 0, 1, 1, 2, 0),
            offsets(0, 0, 0, 1, 1, 1, 1, 2),
            offsets(0, 1, 1, 0, 1, 1, 2, 0));

    /** Number of distinct rotation slots every piece exposes (some repeat). */
    public static final int ROTATIONS = 4;

    private final Cell cell;
    private final int[][][] rotations;

    Tetromino(Cell cell, int[][] rot0, int[][] rot1, int[][] rot2, int[][] rot3) {
        this.cell = cell;
        this.rotations = new int[][][] {rot0, rot1, rot2, rot3};
    }

    /** The square this piece leaves behind when it locks. */
    public Cell cell() {
        return cell;
    }

    /** The four {@code {row, col}} offsets for {@code rotation}, normalised to origin. */
    public int[][] cells(int rotation) {
        return rotations[Math.floorMod(rotation, ROTATIONS)];
    }

    /** Columns spanned by {@code rotation} — a piece at column {@code c} occupies {@code c .. c+width-1}. */
    public int width(int rotation) {
        int max = 0;
        for (int[] cell : cells(rotation)) {
            max = Math.max(max, cell[1]);
        }
        return max + 1;
    }

    /** Rows spanned by {@code rotation}. */
    public int height(int rotation) {
        int max = 0;
        for (int[] cell : cells(rotation)) {
            max = Math.max(max, cell[0]);
        }
        return max + 1;
    }

    /** Every piece, for building a 7-bag. */
    public static List<Tetromino> all() {
        return List.of(values());
    }

    private static int[][] offsets(int... rowColPairs) {
        int[][] cells = new int[rowColPairs.length / 2][2];
        for (int i = 0; i < cells.length; i++) {
            cells[i][0] = rowColPairs[i * 2];
            cells[i][1] = rowColPairs[i * 2 + 1];
        }
        return cells;
    }
}
