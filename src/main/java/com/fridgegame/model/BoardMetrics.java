package com.fridgegame.model;

/**
 * The three classic shape numbers for a Tetris stack.
 *
 * <p>Used to notice things worth saying out loud: a placement that raises {@link #holes()}
 * has buried a cell the player can no longer reach, which is exactly the kind of
 * self-inflicted mistake a commentator should needle you about.
 *
 * @param holes           empty cells with at least one filled cell somewhere above them
 * @param aggregateHeight the sum of all ten column heights
 * @param bumpiness       total height difference between neighbouring columns
 */
public record BoardMetrics(int holes, int aggregateHeight, int bumpiness) {

    /** Measures {@code board}'s locked stack; the falling piece is not counted. */
    public static BoardMetrics of(TetrisBoard board) {
        Cell[][] stack = board.snapshot();
        int[] heights = new int[TetrisBoard.WIDTH];
        int aggregate = 0;
        int holes = 0;
        for (int c = 0; c < TetrisBoard.WIDTH; c++) {
            boolean roofed = false;
            for (int r = 0; r < TetrisBoard.HEIGHT; r++) {
                if (stack[r][c] != Cell.EMPTY) {
                    if (!roofed) {
                        roofed = true;
                        heights[c] = TetrisBoard.HEIGHT - r;
                    }
                } else if (roofed) {
                    holes++;
                }
            }
            aggregate += heights[c];
        }
        int bumpiness = 0;
        for (int c = 0; c < TetrisBoard.WIDTH - 1; c++) {
            bumpiness += Math.abs(heights[c] - heights[c + 1]);
        }
        return new BoardMetrics(holes, aggregate, bumpiness);
    }
}
