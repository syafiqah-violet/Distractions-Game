package com.fridgegame.view;

import com.fridgegame.model.Cell;
import com.fridgegame.model.TetrisBoard;
import com.fridgegame.model.Tetromino;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.util.Duration;

/**
 * Renders one {@link TetrisBoard} onto a {@link Canvas}.
 *
 * <p>A Canvas rather than 200 {@code Region} nodes: the whole grid is repainted on every
 * gravity step, and a scene graph that big would spend its time on layout passes while the
 * player is also dragging groceries around.
 *
 * <p>Purely a renderer — it holds no game state and never mutates the board. The controller
 * calls {@link #render} after anything changes.
 */
public class TetrisBoardView extends Canvas {

    // Kept in step with the -pixel-* palette in styles.css by hand: a Canvas is painted,
    // not styled, so these are the one set of colours the stylesheet cannot reach.
    private static final Color BACKDROP = Color.web("#0b1a24");
    private static final Color FRAME = Color.web("#5c2846");
    private static final Color GRID_LINE = Color.web("#17303f");
    private static final Color DEAD_WASH = Color.web("#ff6b6b", 0.28);
    private static final Color GHOST = Color.web("#4ec8e8", 0.20);

    private final double cellSize;

    private TetrisBoard lastBoard;
    private Color flashColor = Color.TRANSPARENT;
    private double flashAlpha;

    public TetrisBoardView(double cellSize) {
        super(TetrisBoard.WIDTH * cellSize, TetrisBoard.HEIGHT * cellSize);
        this.cellSize = cellSize;
    }

    /**
     * Briefly washes the board in {@code color}.
     *
     * <p>Used to make two otherwise-easy-to-miss events unmistakable: your own line
     * clears, and garbage rows arriving from the opponent. Without this, garbage just
     * silently appears while the player is looking at the fridge.
     */
    public void flash(Color color) {
        flashColor = color;
        new Timeline(
                new KeyFrame(Duration.ZERO, e -> setFlash(0.55)),
                new KeyFrame(Duration.millis(110), e -> setFlash(0.28)),
                new KeyFrame(Duration.millis(260), e -> setFlash(0))
        ).play();
    }

    private void setFlash(double alpha) {
        flashAlpha = alpha;
        if (lastBoard != null) {
            render(lastBoard);
        }
    }

    /** Repaints the stack, the landing preview and the falling piece. */
    public void render(TetrisBoard board) {
        lastBoard = board;
        GraphicsContext g = getGraphicsContext2D();
        g.setFill(BACKDROP);
        g.fillRect(0, 0, getWidth(), getHeight());

        g.setStroke(GRID_LINE);
        g.setLineWidth(1);
        for (int c = 1; c < TetrisBoard.WIDTH; c++) {
            double x = Math.floor(c * cellSize) + 0.5;
            g.strokeLine(x, 0, x, getHeight());
        }
        for (int r = 1; r < TetrisBoard.HEIGHT; r++) {
            double y = Math.floor(r * cellSize) + 0.5;
            g.strokeLine(0, y, getWidth(), y);
        }

        for (int r = 0; r < TetrisBoard.HEIGHT; r++) {
            for (int c = 0; c < TetrisBoard.WIDTH; c++) {
                Cell cell = board.cellAt(r, c);
                if (cell != Cell.EMPTY) {
                    drawCell(g, r, c, colorOf(cell));
                }
            }
        }

        Tetromino piece = board.current();
        if (piece != null && !board.isToppedOut()) {
            drawGhost(g, board, piece);
            for (int[] offset : piece.cells(board.currentRotation())) {
                drawCell(g, board.currentRow() + offset[0], board.currentColumn() + offset[1],
                        colorOf(piece.cell()));
            }
        }

        if (flashAlpha > 0) {
            g.setFill(Color.color(
                    flashColor.getRed(), flashColor.getGreen(), flashColor.getBlue(), flashAlpha));
            g.fillRect(0, 0, getWidth(), getHeight());
        }

        if (board.isToppedOut()) {
            g.setFill(DEAD_WASH);
            g.fillRect(0, 0, getWidth(), getHeight());
        }

        // Last, so nothing paints over it: the same plum frame the CSS panels wear, drawn
        // here because a Canvas gets no border from the stylesheet.
        g.setStroke(FRAME);
        g.setLineWidth(3);
        g.strokeRect(1.5, 1.5, getWidth() - 3, getHeight() - 3);
    }

    /** Outlines where the piece would land, so a hard drop is never a guess. */
    private void drawGhost(GraphicsContext g, TetrisBoard board, Tetromino piece) {
        int landing = board.restingRow(piece, board.currentRotation(), board.currentColumn());
        if (landing < 0 || landing == board.currentRow()) {
            return;
        }
        g.setFill(GHOST);
        for (int[] offset : piece.cells(board.currentRotation())) {
            g.fillRect(
                    (board.currentColumn() + offset[1]) * cellSize + 1,
                    (landing + offset[0]) * cellSize + 1,
                    cellSize - 2, cellSize - 2);
        }
    }

    private void drawCell(GraphicsContext g, int row, int col, Color color) {
        if (row < 0 || row >= TetrisBoard.HEIGHT || col < 0 || col >= TetrisBoard.WIDTH) {
            return;
        }
        double x = col * cellSize;
        double y = row * cellSize;
        g.setFill(color);
        g.fillRect(x + 1, y + 1, cellSize - 2, cellSize - 2);
        // A lighter top-left edge reads as a bevel without needing a gradient per cell.
        g.setFill(color.brighter());
        g.fillRect(x + 1, y + 1, cellSize - 2, Math.max(1, cellSize * 0.18));
    }

    private static Color colorOf(Cell cell) {
        return switch (cell) {
            case I -> Color.web("#4fc3f7");
            case J -> Color.web("#5c78d8");
            case L -> Color.web("#f0932b");
            case O -> Color.web("#f6d32d");
            case S -> Color.web("#4cd137");
            case T -> Color.web("#9b59b6");
            case Z -> Color.web("#e74c3c");
            case GARBAGE -> Color.web("#7f8c8d");
            case EMPTY -> Color.TRANSPARENT;
        };
    }
}
