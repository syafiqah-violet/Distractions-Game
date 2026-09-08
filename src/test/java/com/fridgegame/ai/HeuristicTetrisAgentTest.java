package com.fridgegame.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fridgegame.model.TetrisBoard;
import com.fridgegame.model.TetrisMove;
import com.fridgegame.model.Tetromino;
import org.junit.jupiter.api.Test;

class HeuristicTetrisAgentTest {

    private final HeuristicTetrisAgent agent = new HeuristicTetrisAgent();

    private static TetrisBoard boardStartingWith(Tetromino wanted) {
        for (long seed = 0; seed < 500; seed++) {
            TetrisBoard board = new TetrisBoard(seed);
            if (board.current() == wanted) {
                return board;
            }
        }
        throw new AssertionError("No seed below 500 deals " + wanted + " first");
    }

    @Test
    void answersImmediatelyWithoutBlocking() {
        TetrisBoard board = new TetrisBoard(1);

        var future = agent.chooseMove(board);

        assertTrue(future.isDone(), "the heuristic must never leave the caller waiting");
        assertNotNull(future.getNow(null));
    }

    @Test
    void everyPieceOnAnEmptyBoardGetsALegalMove() {
        for (Tetromino piece : Tetromino.values()) {
            TetrisBoard board = boardStartingWith(piece);

            TetrisMove move = agent.bestMove(board);

            assertTrue(board.isLegal(move), piece + " got illegal move " + move);
        }
    }

    @Test
    void everyPieceOnACrowdedBoardStillGetsALegalMove() {
        for (Tetromino piece : Tetromino.values()) {
            TetrisBoard board = boardStartingWith(piece);
            board.setStack(
                    "XXXXXXXXX.", "XXXXXXX...", "XXXXX.....", "X.........",
                    "XX........", "XXX.......", "XXXX......");

            TetrisMove move = agent.bestMove(board);

            assertTrue(board.isLegal(move), piece + " got illegal move " + move);
        }
    }

    @Test
    void takesTheAvailableLineClear() {
        TetrisBoard board = boardStartingWith(Tetromino.I);
        // Only a horizontal I at column 3 completes the bottom row.
        board.setStack("XXX....XXX");

        TetrisMove move = agent.bestMove(board);

        assertEquals(new TetrisMove(0, 3), move);
    }

    @Test
    void standsTheIPieceUpToFillADeepWell() {
        TetrisBoard board = boardStartingWith(Tetromino.I);
        // A 1-wide well at column 9, four deep: the vertical I belongs there.
        board.setStack(
                "XXXXXXXXX.", "XXXXXXXXX.", "XXXXXXXXX.", "XXXXXXXXX.");

        TetrisMove move = agent.bestMove(board);

        assertEquals(new TetrisMove(1, 9), move);
    }

    @Test
    void prefersNotToBuryHoles() {
        TetrisBoard board = boardStartingWith(Tetromino.O);
        // Column 4 is a 1-wide notch; dropping the 2x2 O across it would bury a hole.
        board.setStack("XXXX.XXXXX");

        TetrisMove move = agent.bestMove(board);

        assertTrue(board.isLegal(move));
        assertTrue(move.column() != 3 && move.column() != 4,
                "placing the O over the notch at column " + move.column() + " buries a hole");
    }

    @Test
    void keepsTheStackAliveOverAWholeGame() {
        TetrisBoard board = new TetrisBoard(42);

        int placements = 0;
        while (!board.isToppedOut() && placements < 300) {
            board.applyMove(agent.bestMove(board));
            placements++;
        }

        assertEquals(300, placements,
                "a competent agent should survive 300 placements, managed " + placements);
    }

    @Test
    void survivesEvenWhenFedGarbage() {
        TetrisBoard board = new TetrisBoard(7);

        int placements = 0;
        while (!board.isToppedOut() && placements < 120) {
            board.applyMove(agent.bestMove(board));
            placements++;
            if (placements % 10 == 0) {
                board.pushGarbage(1);
            }
        }

        assertTrue(placements > 60,
                "expected the agent to weather periodic garbage, lasted " + placements);
    }

    @Test
    void reportsAShortLabelForTheHud() {
        assertEquals("Bot", agent.label());
    }
}
