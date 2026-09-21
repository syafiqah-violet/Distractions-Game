package com.fridgegame.controller;

import com.fridgegame.model.GroceryItem;
import com.fridgegame.model.MemoryBoard;
import java.util.List;
import java.util.Random;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;

/**
 * Drives the mix-and-match card grid: which cards are face up, and when a mismatched pair
 * goes back down.
 *
 * <p>Scores nothing. Like {@link TetrisController} it reports what happened and leaves the
 * points, the streak and the lives to {@link GameController} — the alternative is two classes
 * that both think they own the score.
 *
 * <p>Owns one {@link Timeline}: the reveal window after a mismatch. That single timer is the
 * only piece of state that has to survive a pause correctly, which is why it gets the same
 * single-predicate treatment as Tetris gravity — see {@link #updateReveal()}.
 */
public class MemoryController {

    /**
     * How long a mismatched pair stays visible before turning back down.
     *
     * <p>Long enough to read two icons and place them on the grid, short enough that a wrong
     * guess still costs real time on a 60-second clock. The whole level is a negotiation
     * between those two, so it is a named constant rather than a number in a keyframe.
     */
    private static final Duration REVEAL_WINDOW = Duration.millis(900);

    private final Random random;
    private final Timeline reveal = new Timeline();

    private MemoryBoard board;

    private Runnable onChanged = () -> { };
    private Runnable onMatch = () -> { };
    private Runnable onMismatch = () -> { };

    private boolean active;
    private boolean paused;

    public MemoryController(long seed) {
        this.random = new Random(seed);
        reveal.setCycleCount(1);
        reveal.getKeyFrames().setAll(new KeyFrame(REVEAL_WINDOW, e -> endReveal()));
    }

    /** The current board, or {@code null} before the first memory level starts. */
    public MemoryBoard getBoard() {
        return board;
    }

    /** Fired after any change to what is face up, so the view can repaint. */
    public void setOnChanged(Runnable listener) {
        this.onChanged = listener;
    }

    /** Fired once per matched pair; the caller decides what it is worth. */
    public void setOnMatch(Runnable listener) {
        this.onMatch = listener;
    }

    /** Fired the moment a pair fails to match, not when it flips back. */
    public void setOnMismatch(Runnable listener) {
        this.onMismatch = listener;
    }

    // ------------------------------------------------------------ level flow

    /**
     * Deals a fresh shuffled board holding two of every item in {@code pairs}.
     *
     * <p>Reshuffled per level rather than per game, so replaying after a game over is a new
     * board — memorising one layout across attempts would defeat the entire level.
     */
    public void startLevel(List<GroceryItem> pairs) {
        reveal.stop();
        board = new MemoryBoard(pairs, random.nextLong());
        active = true;
        paused = false;
        onChanged.run();
    }

    /** Stops accepting flips and cancels any pending reveal — used when an overlay comes up. */
    public void stop() {
        active = false;
        // Levels without a card grid never call startLevel, so this is the only place the
        // pause mirror is cleared before the next level that does have one.
        paused = false;
        reveal.stop();
    }

    /** Mirrors the app-wide pause; see {@link #updateReveal()} for why it is not local. */
    public void setPaused(boolean value) {
        if (paused == value) {
            return;
        }
        paused = value;
        updateReveal();
    }

    public boolean isPaused() {
        return paused;
    }

    /**
     * The single decision about whether the reveal countdown should be running right now.
     *
     * <p>Same reasoning as {@link TetrisController#updateGravity()}: routing pause and resume
     * through one predicate stops either condition from resuming a timer the other still
     * wants frozen. {@code pause()} rather than {@code stop()}, because {@code stop()} rewinds
     * the playhead — pausing during a reveal and resuming would hand the player a fresh full
     * window every time, which is a free look at two cards for the price of tapping Esc.
     *
     * <p>Resume only. Starting a reveal is {@link #flip}'s job and uses
     * {@code playFromStart()}: {@code play()} on a {@link Timeline} that already ran to the
     * end resumes from the end, firing the hide immediately and flashing the mismatched pair
     * for a single frame.
     */
    private void updateReveal() {
        if (active && !paused && board != null && board.isRevealPending()) {
            reveal.play();
        } else {
            reveal.pause();
        }
    }

    // ---------------------------------------------------------------- input

    /**
     * Turns card {@code index} over, if that is a legal thing to do right now.
     *
     * <p>Silently ignores everything else — a click on a matched card, a click during the
     * reveal window, a click while paused. The pause overlay already swallows the mouse, but
     * that block is a property of a stylesheet; this is the layer that makes a missing
     * background colour a cosmetic bug rather than a free look at the board.
     */
    public void flip(int index) {
        if (!active || paused || board == null) {
            return;
        }
        MemoryBoard.FlipResult result = board.flip(index);
        switch (result.outcome()) {
            case IGNORED -> {
                // Nothing turned over, so nothing to repaint and nothing to score.
            }
            case FIRST_UP -> onChanged.run();
            case MATCH -> {
                // Repaint first: the pair should be on screen before the score reacts to it,
                // and the last match ends the level from inside onMatch.
                onChanged.run();
                onMatch.run();
            }
            case MISMATCH -> {
                onChanged.run();
                // Charged now rather than when the cards flip back, so the penalty lands with
                // the mistake the player just made instead of a second later, seemingly at random.
                onMismatch.run();
                reveal.playFromStart();
            }
        }
    }

    /** End of the reveal window: the mismatched pair goes back down. */
    private void endReveal() {
        if (board == null) {
            return;
        }
        board.hideMismatched();
        onChanged.run();
    }
}
