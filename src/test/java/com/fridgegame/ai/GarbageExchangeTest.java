package com.fridgegame.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fridgegame.controller.GameController;
import com.fridgegame.model.Cell;
import com.fridgegame.model.StepResult;
import com.fridgegame.model.TetrisBoard;
import org.junit.jupiter.api.Test;

/**
 * The opponent-to-player garbage loop (phase T4), exercised without JavaFX by running
 * the agent against a board directly and feeding its clears into a second board.
 */
class GarbageExchangeTest {

    private final HeuristicTetrisAgent agent = new HeuristicTetrisAgent();

    @Test
    void aSingleLineClearSendsNothing() {
        assertEquals(0, GameController.garbageFor(1));
    }

    @Test
    void multiLineClearsSendOneRowLess() {
        assertEquals(1, GameController.garbageFor(2));
        assertEquals(2, GameController.garbageFor(3));
        assertEquals(3, GameController.garbageFor(4));
    }

    @Test
    void noClearSendsNothingRatherThanANegativeCount() {
        assertEquals(0, GameController.garbageFor(0));
    }

    @Test
    void theAgentActuallyClearsRowsSoGarbageCanEverFire() {
        TetrisBoard agentBoard = new TetrisBoard(42);

        int totalCleared = 0;
        for (int i = 0; i < 200 && !agentBoard.isToppedOut(); i++) {
            totalCleared += agentBoard.applyMove(agent.bestMove(agentBoard)).rowsCleared();
        }

        assertTrue(totalCleared > 10,
                "the opponent must be able to clear rows; managed " + totalCleared);
    }

    @Test
    void agentClearsLandAsGarbageOnThePlayersBoard() {
        TetrisBoard agentBoard = new TetrisBoard(42);
        TetrisBoard playerBoard = new TetrisBoard(99);

        int garbageSent = 0;
        for (int i = 0; i < 200 && !agentBoard.isToppedOut(); i++) {
            StepResult result = agentBoard.applyMove(agent.bestMove(agentBoard));
            int garbage = GameController.garbageFor(result.rowsCleared());
            if (garbage > 0) {
                garbageSent += garbage;
                playerBoard.pushGarbage(garbage);
                if (playerBoard.isToppedOut()) {
                    playerBoard.clear();
                }
            }
        }

        assertTrue(garbageSent > 0,
                "an agent good enough to multi-line should have sent garbage");
    }

    @Test
    void garbageRowsAreAlmostFullSoTheyAreHardToClear() {
        TetrisBoard board = new TetrisBoard(5);

        board.pushGarbage(3);

        for (int r = TetrisBoard.HEIGHT - 3; r < TetrisBoard.HEIGHT; r++) {
            int filled = 0;
            for (int c = 0; c < TetrisBoard.WIDTH; c++) {
                if (board.cellAt(r, c) != Cell.EMPTY) {
                    filled++;
                }
            }
            assertEquals(TetrisBoard.WIDTH - 1, filled, "row " + r + " should have exactly one gap");
        }
    }

    @Test
    void garbageThatBuriesThePlayerIsReportedAsATopOut() {
        TetrisBoard board = new TetrisBoard(5);

        // Twenty garbage rows cannot fit in a twenty-row board alongside a falling piece.
        board.pushGarbage(TetrisBoard.HEIGHT);

        assertTrue(board.isToppedOut());
    }
}
