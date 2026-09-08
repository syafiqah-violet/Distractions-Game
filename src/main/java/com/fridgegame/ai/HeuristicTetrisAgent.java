package com.fridgegame.ai;

import com.fridgegame.model.Cell;
import com.fridgegame.model.TetrisBoard;
import com.fridgegame.model.TetrisMove;
import com.fridgegame.model.Tetromino;
import java.util.concurrent.CompletableFuture;

/**
 * A local, instant, always-legal Tetris agent.
 *
 * <p>Tries every rotation × column, simulates the drop on a copy of the stack, and scores
 * the outcome on four classic features: lines cleared, total height, buried holes, and
 * surface bumpiness. Roughly 40 candidates per piece — microseconds, so it runs inline on
 * the JavaFX thread.
 *
 * <p>Two jobs: it is the opponent when the LLM is unreachable, and it is the per-move
 * fallback whenever the LLM is late or answers with something illegal. That makes the
 * game fully playable offline, and keeps the LLM's failure modes invisible to the player.
 */
public final class HeuristicTetrisAgent implements TetrisAgent {

    // Weights in the spirit of Dellacherie/Lee: clear lines, stay low, avoid burying holes,
    // keep the surface flat. Tuned to be competent but not superhuman — it should be beatable.
    private static final double W_LINES = 0.76;
    private static final double W_HEIGHT = -0.51;
    private static final double W_HOLES = -0.36;
    private static final double W_BUMPINESS = -0.18;

    @Override
    public CompletableFuture<TetrisMove> chooseMove(TetrisBoard board) {
        return CompletableFuture.completedFuture(bestMove(board));
    }

    @Override
    public String label() {
        return "Bot";
    }

    /**
     * The best placement for {@code board}'s current piece.
     *
     * <p>Always returns a move. If the stack is so high that no drop is legal, it returns
     * the piece's present position, which {@link TetrisBoard#applyMove} will resolve into
     * a top-out rather than an exception.
     */
    public TetrisMove bestMove(TetrisBoard board) {
        Tetromino piece = board.current();
        if (piece == null) {
            return new TetrisMove(0, 0);
        }
        boolean[][] stack = occupancy(board);

        TetrisMove best = null;
        double bestScore = -Double.MAX_VALUE;
        for (int rotation = 0; rotation < Tetromino.ROTATIONS; rotation++) {
            int span = piece.width(rotation);
            for (int column = 0; column + span <= TetrisBoard.WIDTH; column++) {
                int resting = board.restingRow(piece, rotation, column);
                if (resting < 0) {
                    continue;
                }
                double score = evaluate(stack, piece, rotation, resting, column);
                if (score > bestScore) {
                    bestScore = score;
                    best = new TetrisMove(rotation, column);
                }
            }
        }
        return best != null
                ? best
                : new TetrisMove(board.currentRotation(), board.currentColumn());
    }

    private static boolean[][] occupancy(TetrisBoard board) {
        Cell[][] snapshot = board.snapshot();
        boolean[][] filled = new boolean[TetrisBoard.HEIGHT][TetrisBoard.WIDTH];
        for (int r = 0; r < TetrisBoard.HEIGHT; r++) {
            for (int c = 0; c < TetrisBoard.WIDTH; c++) {
                filled[r][c] = snapshot[r][c] != Cell.EMPTY;
            }
        }
        return filled;
    }

    private static double evaluate(
            boolean[][] stack, Tetromino piece, int rotation, int row, int column) {
        boolean[][] after = new boolean[TetrisBoard.HEIGHT][];
        for (int r = 0; r < TetrisBoard.HEIGHT; r++) {
            after[r] = stack[r].clone();
        }
        for (int[] offset : piece.cells(rotation)) {
            after[row + offset[0]][column + offset[1]] = true;
        }

        int lines = collapseFullRows(after);
        int[] heights = new int[TetrisBoard.WIDTH];
        int aggregate = 0;
        int holes = 0;
        for (int c = 0; c < TetrisBoard.WIDTH; c++) {
            boolean roofed = false;
            for (int r = 0; r < TetrisBoard.HEIGHT; r++) {
                if (after[r][c]) {
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

        return W_LINES * lines
                + W_HEIGHT * aggregate
                + W_HOLES * holes
                + W_BUMPINESS * bumpiness;
    }

    /** Removes full rows in place, sliding the rest down; returns how many went. */
    private static int collapseFullRows(boolean[][] grid) {
        int write = TetrisBoard.HEIGHT - 1;
        int cleared = 0;
        for (int read = TetrisBoard.HEIGHT - 1; read >= 0; read--) {
            boolean full = true;
            for (int c = 0; c < TetrisBoard.WIDTH; c++) {
                if (!grid[read][c]) {
                    full = false;
                    break;
                }
            }
            if (full) {
                cleared++;
            } else {
                grid[write--] = grid[read];
            }
        }
        while (write >= 0) {
            grid[write--] = new boolean[TetrisBoard.WIDTH];
        }
        return cleared;
    }
}
