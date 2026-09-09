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
    private Runnable onPieceLocked = () -> { };

    private boolean active;
    private boolean draggingGrocery;
    private boolean paused;
    private int piecesLocked;

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

    /**
     * Fired once per piece that comes to rest, after any resulting line clears.
     *
     * <p>The one moment where the stack's shape is worth measuring: the player has just
     * committed to a placement, so this is when a newly buried hole can be attributed to
     * a decision rather than to gravity mid-fall.
     */
    public void setOnPieceLocked(Runnable listener) {
        this.onPieceLocked = listener;
    }

    /** Pieces locked during the current level — the denominator for a clear rate. */
    public int getPiecesLocked() {
        return piecesLocked;
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
        paused = false;
        piecesLocked = 0;
        updateGravity();
        onChanged.run();
    }

    /** Halts gravity and stops accepting input — used while an overlay screen is up. */
    public void stop() {
        active = false;
        // A SORT_ONLY level never calls startLevel, so this is the only place the pause
        // mirror gets cleared before the next level that does have a board.
        paused = false;
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
        updateGravity();
    }

    /** Mirrors the app-wide pause; see {@link #updateGravity()} for why it is not local. */
    public void setPaused(boolean value) {
        if (paused == value) {
            return;
        }
        paused = value;
        updateGravity();
    }

    public boolean isPaused() {
        return paused;
    }

    /** Recovers from a top-out: the caller has already charged a life. */
    public void resetAfterTopOut() {
        board.clear();
        onChanged.run();
        updateGravity();
    }

    /**
     * The single decision about whether the piece should be falling right now.
     *
     * <p>Four separate conditions can stop gravity — the level not being live, an app-wide
     * pause, a drag in flight, and a topped-out board — and each used to call
     * {@code gravity.pause()} or {@code play()} for itself. That is fine until two of them
     * overlap, at which point whichever ends first resumes a game the other still wants
     * frozen. Routing every transition through one predicate means no condition can resume
     * unilaterally: pausing mid-drag and then resuming leaves the piece still, correctly,
     * because the drag has not finished.
     *
     * <p>{@code pause()} rather than {@code stop()} on the way down, because {@code stop()}
     * rewinds the playhead — a player who tapped pause twice a second would have a piece
     * that never fell. {@code pause()} on an already-stopped {@link Timeline} is a no-op,
     * which is what makes the else branch safe when {@link #stop()} has already run.
     */
    private void updateGravity() {
        if (active && !paused && !draggingGrocery && !board.isToppedOut()) {
            gravity.play();
        } else {
            gravity.pause();
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
            if (paused) {
                // Consumed, not ignored: a bare return lets SPACE reach the pause
                // overlay's Resume button, so mashing hard-drop would unpause the game.
                if (isGameKey(event.getCode())) {
                    event.consume();
                }
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
            // board.isToppedOut() is true here, so this pauses — same as the old explicit
            // gravity.pause(), but the top-out is no longer a fifth owner of the timeline.
            updateGravity();
            onTopOut.run();
            return;
        }
        // After the clear callbacks, so a listener measuring the stack sees the settled board.
        if (result.locked()) {
            piecesLocked++;
            onPieceLocked.run();
        }
    }
}
