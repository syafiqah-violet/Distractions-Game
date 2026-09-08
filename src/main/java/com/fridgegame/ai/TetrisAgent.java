package com.fridgegame.ai;

import com.fridgegame.model.TetrisBoard;
import com.fridgegame.model.TetrisMove;
import java.util.concurrent.CompletableFuture;

/**
 * Something that decides where to put the opponent's falling piece.
 *
 * <p>Two implementations: {@link HeuristicTetrisAgent}, which answers instantly and
 * always legally, and the LLM-backed agent, which answers over the network in roughly
 * 700ms. The controller treats them identically and falls back to the heuristic whenever
 * the other misses its deadline or returns something illegal.
 *
 * <p><b>Threading contract:</b> {@link #chooseMove} is called on the JavaFX application
 * thread and must read everything it needs from {@code board} <i>before</i> returning.
 * The board keeps mutating as gravity runs, so an implementation that defers its read
 * into an async callback would be inspecting a different position than it was asked about.
 */
public interface TetrisAgent {

    /**
     * Picks a placement for {@code board}'s current piece.
     *
     * @return a future that may already be complete; never {@code null}. Completing
     *         exceptionally is a valid way to decline, and the caller will fall back.
     */
    CompletableFuture<TetrisMove> chooseMove(TetrisBoard board);

    /** Short name for the HUD badge, e.g. {@code "Bot"} or {@code "qwen3.6-35b"}. */
    String label();
}
