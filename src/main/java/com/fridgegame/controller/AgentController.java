package com.fridgegame.controller;

import com.fridgegame.ai.HeuristicTetrisAgent;
import com.fridgegame.ai.TetrisAgent;
import com.fridgegame.model.StepResult;
import com.fridgegame.model.TetrisBoard;
import com.fridgegame.model.TetrisMove;
import java.util.concurrent.CompletableFuture;
import java.util.function.IntConsumer;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;

/**
 * Drives the opponent's board: one piece placed per cadence tick, decided by a
 * {@link TetrisAgent}.
 *
 * <p>The cadence period doubles as the agent's decision deadline. A move is requested the
 * instant a piece spawns and consumed on the next tick, so a network-backed agent gets the
 * whole period to answer. If it hasn't answered by then — or answers with an illegal
 * placement — {@link HeuristicTetrisAgent} decides that single move instead. The board
 * therefore never stalls waiting on the network, and the player cannot tell the difference
 * beyond the opponent playing a little less interestingly.
 */
public class AgentController {

    /** Decides any move the primary agent fumbles. Always instant, always legal. */
    private static final HeuristicTetrisAgent FALLBACK = new HeuristicTetrisAgent();

    private final TetrisBoard board;
    private final Timeline cadence = new Timeline();

    private TetrisAgent agent = FALLBACK;
    private CompletableFuture<TetrisMove> pending;

    private IntConsumer onRowsCleared = rows -> { };
    private Runnable onTopOut = () -> { };
    private Runnable onChanged = () -> { };

    private boolean active;
    private int fallbackMoves;
    private int agentMoves;

    public AgentController(long seed) {
        this.board = new TetrisBoard(seed);
        cadence.setCycleCount(Animation.INDEFINITE);
    }

    public TetrisBoard getBoard() {
        return board;
    }

    /** Swaps in a different brain; takes effect on the next requested move. */
    public void setAgent(TetrisAgent agent) {
        this.agent = agent == null ? FALLBACK : agent;
    }

    /** Fired with the rows the agent just cleared — the trigger for sending garbage. */
    public void setOnRowsCleared(IntConsumer listener) {
        this.onRowsCleared = listener;
    }

    /** Fired when the agent buries itself; the player gets a bonus and this board resets. */
    public void setOnTopOut(Runnable listener) {
        this.onTopOut = listener;
    }

    public void setOnChanged(Runnable listener) {
        this.onChanged = listener;
    }

    /** How many moves this level came from the fallback rather than the primary agent. */
    public int getFallbackMoves() {
        return fallbackMoves;
    }

    /** How many moves this level the primary agent actually decided. */
    public int getAgentMoves() {
        return agentMoves;
    }

    public void startLevel(int cadenceMillis) {
        cadence.stop();
        board.clear();
        fallbackMoves = 0;
        agentMoves = 0;
        active = true;
        requestMove();
        cadence.getKeyFrames().setAll(
                new KeyFrame(Duration.millis(cadenceMillis), e -> placeOnePiece()));
        cadence.play();
        onChanged.run();
    }

    public void stop() {
        active = false;
        cadence.stop();
        pending = null;
    }

    private void requestMove() {
        // Abandon any answer that missed its deadline, so a slow endpoint cannot
        // accumulate in-flight requests over a 60-second round.
        if (pending != null && !pending.isDone()) {
            pending.cancel(true);
        }
        if (board.isToppedOut()) {
            pending = null;
            return;
        }
        try {
            pending = agent.chooseMove(board);
        } catch (RuntimeException e) {
            // A misbehaving agent must not take the game down with it.
            pending = null;
        }
    }

    private void placeOnePiece() {
        if (!active || board.isToppedOut()) {
            return;
        }

        TetrisMove move = collectPendingMove();
        if (move == null || !board.isLegal(move)) {
            move = FALLBACK.bestMove(board);
            fallbackMoves++;
        } else {
            agentMoves++;
        }

        StepResult result = board.applyMove(move);
        if (result.rowsCleared() > 0) {
            onRowsCleared.accept(result.rowsCleared());
        }
        if (result.toppedOut()) {
            cadence.pause();
            onTopOut.run();
        } else {
            requestMove();
        }
        onChanged.run();
    }

    /** The pending answer if it arrived in time, else {@code null}. Never blocks. */
    private TetrisMove collectPendingMove() {
        if (pending == null || !pending.isDone() || pending.isCompletedExceptionally()) {
            return null;
        }
        return pending.getNow(null);
    }

    /** Recovers after the agent topped out, so it keeps playing for the rest of the round. */
    public void resetAfterTopOut() {
        board.clear();
        requestMove();
        onChanged.run();
        if (active) {
            cadence.play();
        }
    }
}
