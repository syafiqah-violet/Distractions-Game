package com.fridgegame.controller;

import com.fridgegame.model.StepResult;
import com.fridgegame.model.TetrisBoard;
import java.util.function.IntConsumer;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.util.Duration;

/**
 * Drives the player's Tetris board: gravity, keyboard input, and the callbacks that
 * turn cleared rows into grocery items.
 *
 * <p>Owns a {@link Timeline} per level rather than one global clock, because gravity
 * speed is the main difficulty knob and changes with every level.
 */
public class TetrisController {

    private final TetrisBoard board;
    private final Timeline gravity = new Timeline();

    private IntConsumer onRowsCleared = rows -> { };
    private Runnable onTopOut = () -> { };
    private Runnable onChanged = () -> { };

    private boolean active;
    private boolean draggingGrocery;

    public TetrisController(long seed) {
        this.board = new TetrisBoard(seed);
        gravity.setCycleCount(Animation.INDEFINITE);
    }

    public TetrisBoard getBoard() {
        return board;
    }

    /** Fired with the number of rows a single lock cleared — one grocery item each. */
    public void setOnRowsCleared(IntConsumer listener) {
        this.onRowsCleared = listener;
    }

    /** Fired when the stack reaches the top; the caller decides the penalty. */
    public void setOnTopOut(Runnable listener) {
        this.onTopOut = listener;
    }

    /** Fired after any state change, so the view can repaint. */
    public void setOnChanged(Runnable listener) {
        this.onChanged = listener;
    }

    // ------------------------------------------------------------ level flow

    /** Wipes the board and restarts gravity at {@code gravityMillis} per row. */
    public void startLevel(int gravityMillis) {
        gravity.stop();
        board.clear();
        gravity.getKeyFrames().setAll(
                new KeyFrame(Duration.millis(gravityMillis), e -> step()));
        active = true;
        draggingGrocery = false;
        gravity.play();
        onChanged.run();
    }

    /** Halts gravity and stops accepting input — used while an overlay screen is up. */
    public void stop() {
        active = false;
        gravity.stop();
    }

    /**
     * Pauses gravity while a grocery drag is in flight.
     *
     * <p>JavaFX runs a nested event loop during a drag-and-drop gesture, so key presses
     * are not reliably delivered and the player would be unable to steer a falling piece.
     * The round timer keeps running, so stalling a drag costs time and gains nothing.
     */
    public void setDraggingGrocery(boolean dragging) {
        if (draggingGrocery == dragging) {
            return;
        }
        draggingGrocery = dragging;
        if (!active) {
            return;
        }
        if (dragging) {
            gravity.pause();
        } else {
            gravity.play();
        }
    }

    /**
     * Takes {@code rows} garbage rows from the opponent.
     *
     * <p>Garbage can bury the player outright, so this goes through the same top-out
     * path as gravity does rather than silently leaving a dead board on screen.
     */
    public void receiveGarbage(int rows) {
        if (!active || rows <= 0) {
            return;
        }
        board.pushGarbage(rows);
        if (board.isToppedOut()) {
            gravity.pause();
            onTopOut.run();
        }
        onChanged.run();
    }

    /** Recovers from a top-out: the caller has already charged a life. */
    public void resetAfterTopOut() {
        board.clear();
        onChanged.run();
        if (active && !draggingGrocery) {
            gravity.play();
        }
    }

    // ---------------------------------------------------------------- input

    /**
     * Binds the piece controls to {@code scene}.
     *
     * <p>An event <i>filter</i> on the Scene, not a handler on the board node: the counter's
     * {@code ScrollPane} would otherwise swallow the arrow keys, and the board has no focus
     * of its own to compete with. Keys are consumed only while a level is running, so the
     * overlay screens' buttons stay keyboard-operable.
     */
    public void installKeys(Scene scene) {
        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (!active || board.isToppedOut()) {
                return;
            }
            boolean handled = switch (event.getCode()) {
                case LEFT, A -> board.moveLeft();
                case RIGHT, D -> board.moveRight();
                case UP, W -> board.rotate();
                case DOWN, S -> softDrop();
                case SPACE -> hardDrop();
                default -> false;
            };
            if (handled || isGameKey(event.getCode())) {
                onChanged.run();
                event.consume();
            }
        });
    }

    private static boolean isGameKey(KeyCode code) {
        return switch (code) {
            case LEFT, RIGHT, UP, DOWN, SPACE, A, D, W, S -> true;
            default -> false;
        };
    }

    private boolean softDrop() {
        handle(board.tick());
        return true;
    }

    private boolean hardDrop() {
        handle(board.hardDrop());
        return true;
    }

    // -------------------------------------------------------------- gravity

    private void step() {
        handle(board.tick());
        onChanged.run();
    }

    private void handle(StepResult result) {
        if (result.rowsCleared() > 0) {
            onRowsCleared.accept(result.rowsCleared());
        }
        if (result.toppedOut()) {
            gravity.pause();
            onTopOut.run();
        }
    }
}
