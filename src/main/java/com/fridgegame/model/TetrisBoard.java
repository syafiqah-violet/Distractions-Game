package com.fridgegame.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Random;

/**
 * A 10×20 Tetris playfield: the locked stack plus the one falling piece.
 *
 * <p>Deliberately free of JavaFX imports, like the rest of {@code model/} — the whole
 * engine is unit-testable with no display. Row 0 is the <b>top</b> of the board, so
 * clearing a row means shifting everything above it downward.
 *
 * <p>Piece order comes from a seeded 7-bag randomiser, which keeps tests deterministic
 * and stops real games from dealing five S-pieces in a row.
 */
public final class TetrisBoard {

    public static final int WIDTH = 10;
    public static final int HEIGHT = 20;

    private final Cell[][] grid = new Cell[HEIGHT][WIDTH];
    private final Deque<Tetromino> bag = new ArrayDeque<>();
    private final Random random;

    private Tetromino current;
    private Tetromino next;
    private int rotation;
    private int row;
    private int col;
    private boolean toppedOut;

    public TetrisBoard(long seed) {
        this.random = new Random(seed);
        clear();
    }

    // ---------------------------------------------------------------- lifecycle

    /** Empties the stack and deals a fresh piece. Used at level start and after a top-out. */
    public void clear() {
        for (Cell[] rowCells : grid) {
            java.util.Arrays.fill(rowCells, Cell.EMPTY);
        }
        toppedOut = false;
        next = deal();
        spawn();
    }

    private Tetromino deal() {
        if (bag.isEmpty()) {
            List<Tetromino> refill = new ArrayList<>(Tetromino.all());
            Collections.shuffle(refill, random);
            bag.addAll(refill);
        }
        return bag.poll();
    }

    private void spawn() {
        current = next;
        next = deal();
        rotation = 0;
        row = 0;
        col = (WIDTH - current.width(rotation)) / 2;
        if (!fits(current, rotation, row, col)) {
            toppedOut = true;
        }
    }

    // ------------------------------------------------------------ player input

    public boolean moveLeft() {
        return shift(-1);
    }

    public boolean moveRight() {
        return shift(1);
    }

    private boolean shift(int delta) {
        if (toppedOut || !fits(current, rotation, row, col + delta)) {
            return false;
        }
        col += delta;
        return true;
    }

    /**
     * Rotates clockwise if the result fits, nudging left off the right wall first.
     *
     * <p>No SRS wall kicks — a rotation that still doesn't fit is simply refused.
     */
    public boolean rotate() {
        if (toppedOut) {
            return false;
        }
        int candidate = Math.floorMod(rotation + 1, Tetromino.ROTATIONS);
        int maxCol = WIDTH - current.width(candidate);
        int candidateCol = Math.min(col, maxCol);
        if (candidateCol < 0 || !fits(current, candidate, row, candidateCol)) {
            return false;
        }
        rotation = candidate;
        col = candidateCol;
        return true;
    }

    // -------------------------------------------------------------- gravity

    /** One gravity step: fall a row, or lock, clear rows and spawn the next piece. */
    public StepResult tick() {
        if (toppedOut) {
            return new StepResult(false, 0, true);
        }
        if (fits(current, rotation, row + 1, col)) {
            row++;
            return StepResult.FELL;
        }
        return lock();
    }

    /** Drops the piece as far as it will go, then locks it. */
    public StepResult hardDrop() {
        if (toppedOut) {
            return new StepResult(false, 0, true);
        }
        while (fits(current, rotation, row + 1, col)) {
            row++;
        }
        return lock();
    }

    /**
     * Steers the current piece to {@code move} and hard-drops it.
     *
     * <p>Rotation and column are clamped into range rather than rejected, so an agent
     * that returns a slightly off placement still produces a legal move. Callers that
     * need to reject bad agent output should check {@link #restingRow} first.
     */
    public StepResult applyMove(TetrisMove move) {
        if (toppedOut) {
            return new StepResult(false, 0, true);
        }
        int targetRotation = Math.floorMod(move.rotation(), Tetromino.ROTATIONS);
        int maxCol = WIDTH - current.width(targetRotation);
        int targetCol = Math.max(0, Math.min(move.column(), maxCol));
        if (fits(current, targetRotation, row, targetCol)) {
            rotation = targetRotation;
            col = targetCol;
        }
        return hardDrop();
    }

    private StepResult lock() {
        for (int[] cell : current.cells(rotation)) {
            grid[row + cell[0]][col + cell[1]] = current.cell();
        }
        int cleared = clearFullRows();
        spawn();
        return new StepResult(true, cleared, toppedOut);
    }

    private int clearFullRows() {
        int cleared = 0;
        for (int r = HEIGHT - 1; r >= 0; r--) {
            if (isRowFull(r)) {
                collapseInto(r);
                cleared++;
                r++; // the row that slid down into r still needs checking
            }
        }
        return cleared;
    }

    private boolean isRowFull(int r) {
        for (int c = 0; c < WIDTH; c++) {
            if (grid[r][c] == Cell.EMPTY) {
                return false;
            }
        }
        return true;
    }

    private void collapseInto(int target) {
        for (int r = target; r > 0; r--) {
            System.arraycopy(grid[r - 1], 0, grid[r], 0, WIDTH);
        }
        java.util.Arrays.fill(grid[0], Cell.EMPTY);
    }

    // -------------------------------------------------------------- garbage

    /**
     * Pushes {@code rows} near-full rows in at the bottom, shifting the stack up.
     *
     * <p>Each garbage row has exactly one hole, so it can be cleared but only with a
     * deliberate placement. If the shift would push locked squares off the top of the
     * board, the board tops out.
     */
    public void pushGarbage(int rows) {
        if (rows <= 0 || toppedOut) {
            return;
        }
        for (int i = 0; i < rows; i++) {
            for (int c = 0; c < WIDTH; c++) {
                if (grid[0][c] != Cell.EMPTY) {
                    toppedOut = true;
                }
            }
            for (int r = 0; r < HEIGHT - 1; r++) {
                System.arraycopy(grid[r + 1], 0, grid[r], 0, WIDTH);
            }
            int hole = random.nextInt(WIDTH);
            for (int c = 0; c < WIDTH; c++) {
                grid[HEIGHT - 1][c] = c == hole ? Cell.EMPTY : Cell.GARBAGE;
            }
        }
        // The falling piece may now be inside the raised stack; lift it clear.
        while (row > 0 && !fits(current, rotation, row, col)) {
            row--;
        }
        if (!fits(current, rotation, row, col)) {
            toppedOut = true;
        }
    }

    // ------------------------------------------------------- agent queries

    /**
     * The row the piece would come to rest on if dropped at {@code rotation}/{@code column},
     * or {@code -1} if that placement is illegal (off-board or blocked all the way up).
     */
    public int restingRow(Tetromino piece, int rotation, int column) {
        if (column < 0 || column + piece.width(rotation) > WIDTH) {
            return -1;
        }
        int resting = -1;
        for (int r = 0; r + piece.height(rotation) <= HEIGHT; r++) {
            if (!fits(piece, rotation, r, column)) {
                break;
            }
            resting = r;
        }
        return resting;
    }

    /** Whether {@code move} is a placement the current piece could actually reach. */
    public boolean isLegal(TetrisMove move) {
        if (move.rotation() < 0 || move.rotation() >= Tetromino.ROTATIONS) {
            return false;
        }
        return restingRow(current, move.rotation(), move.column()) >= 0;
    }

    /** Height of each column, measured from the floor; 0 means the column is empty. */
    public int[] columnHeights() {
        int[] heights = new int[WIDTH];
        for (int c = 0; c < WIDTH; c++) {
            for (int r = 0; r < HEIGHT; r++) {
                if (grid[r][c] != Cell.EMPTY) {
                    heights[c] = HEIGHT - r;
                    break;
                }
            }
        }
        return heights;
    }

    /** A defensive copy of the locked stack, excluding the falling piece. */
    public Cell[][] snapshot() {
        Cell[][] copy = new Cell[HEIGHT][];
        for (int r = 0; r < HEIGHT; r++) {
            copy[r] = grid[r].clone();
        }
        return copy;
    }

    /**
     * Loads an exact position into the locked stack, leaving the falling piece untouched.
     *
     * <p>Rows are given <b>bottom-up</b> — {@code bottomUpRows[0]} is the floor — with
     * {@code '.'} for empty and any other character for filled. Rows not supplied are
     * cleared. Filled squares come back as {@link Cell#GARBAGE} since the original piece
     * colours are not recoverable from the text.
     *
     * <p>Public because reaching a specific position by playing moves is impractical:
     * tests and agent evaluation both need to state the board directly.
     */
    public void setStack(String... bottomUpRows) {
        for (Cell[] rowCells : grid) {
            java.util.Arrays.fill(rowCells, Cell.EMPTY);
        }
        for (int i = 0; i < bottomUpRows.length; i++) {
            String spec = bottomUpRows[i];
            int r = HEIGHT - 1 - i;
            for (int c = 0; c < WIDTH; c++) {
                grid[r][c] = spec.charAt(c) == '.' ? Cell.EMPTY : Cell.GARBAGE;
            }
        }
    }

    private boolean fits(Tetromino piece, int rotation, int row, int col) {
        for (int[] cell : piece.cells(rotation)) {
            int r = row + cell[0];
            int c = col + cell[1];
            if (r < 0 || r >= HEIGHT || c < 0 || c >= WIDTH || grid[r][c] != Cell.EMPTY) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------ accessors

    public Cell cellAt(int row, int col) {
        return grid[row][col];
    }

    public Tetromino current() {
        return current;
    }

    public Tetromino next() {
        return next;
    }

    public int currentRotation() {
        return rotation;
    }

    public int currentRow() {
        return row;
    }

    public int currentColumn() {
        return col;
    }

    public boolean isToppedOut() {
        return toppedOut;
    }
}
