package com.fridgegame.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TetrisBoardTest {

    /**
     * The bag is seeded, so rather than hardcoding a magic seed we search for one that
     * deals the piece a test needs. This exercises the real randomiser instead of a stub.
     */
    private static TetrisBoard boardStartingWith(Tetromino wanted) {
        for (long seed = 0; seed < 500; seed++) {
            TetrisBoard board = new TetrisBoard(seed);
            if (board.current() == wanted) {
                return board;
            }
        }
        throw new AssertionError("No seed below 500 deals " + wanted + " first");
    }

    private static int filledCells(TetrisBoard board) {
        int count = 0;
        for (int r = 0; r < TetrisBoard.HEIGHT; r++) {
            for (int c = 0; c < TetrisBoard.WIDTH; c++) {
                if (board.cellAt(r, c) != Cell.EMPTY) {
                    count++;
                }
            }
        }
        return count;
    }

    // ------------------------------------------------------------------ setup

    @Test
    void freshBoardIsEmptyWithAPieceReadyAndNoTopOut() {
        TetrisBoard board = new TetrisBoard(1);

        assertEquals(0, filledCells(board));
        assertNotNull(board.current());
        assertNotNull(board.next());
        assertFalse(board.isToppedOut());
        assertEquals(0, board.currentRow());
    }

    @Test
    void pieceSpawnsHorizontallyCentred() {
        TetrisBoard board = boardStartingWith(Tetromino.O);

        assertEquals((TetrisBoard.WIDTH - 2) / 2, board.currentColumn());
    }

    // ---------------------------------------------------------------- gravity

    @Test
    void tickFallsOneRowAtATimeThenLocksOnTheFloor() {
        TetrisBoard board = boardStartingWith(Tetromino.O);

        StepResult result = board.tick();
        assertFalse(result.locked());
        assertEquals(1, board.currentRow());

        int guard = 0;
        while (!result.locked() && guard++ < TetrisBoard.HEIGHT + 2) {
            result = board.tick();
        }

        assertTrue(result.locked());
        assertEquals(0, result.rowsCleared());
        assertEquals(4, filledCells(board), "the locked O piece leaves 4 squares");
        assertEquals(Cell.O, board.cellAt(TetrisBoard.HEIGHT - 1, 4));
    }

    @Test
    void hardDropLandsThePieceOnTheFloorInOneCall() {
        TetrisBoard board = boardStartingWith(Tetromino.O);

        StepResult result = board.hardDrop();

        assertTrue(result.locked());
        assertEquals(Cell.O, board.cellAt(TetrisBoard.HEIGHT - 1, 4));
        assertEquals(Cell.O, board.cellAt(TetrisBoard.HEIGHT - 2, 5));
    }

    // ------------------------------------------------------------ line clears

    @Test
    void fillingTheLastGapClearsThatRow() {
        TetrisBoard board = boardStartingWith(Tetromino.I);
        board.setStack("XXX....XXX");

        StepResult result = board.applyMove(new TetrisMove(0, 3));

        assertEquals(1, result.rowsCleared());
        assertEquals(0, filledCells(board), "clearing the only row empties the board");
    }

    @Test
    void verticalIPieceClearsFourRowsAtOnce() {
        TetrisBoard board = boardStartingWith(Tetromino.I);
        board.setStack(".XXXXXXXXX", ".XXXXXXXXX", ".XXXXXXXXX", ".XXXXXXXXX");

        StepResult result = board.applyMove(new TetrisMove(1, 0));

        assertEquals(4, result.rowsCleared());
        assertEquals(0, filledCells(board));
    }

    @Test
    void rowsAboveAClearedRowSlideDown() {
        TetrisBoard board = boardStartingWith(Tetromino.I);
        // Bottom row needs 4 cells; the row above it has a single square at column 0.
        board.setStack("XXX....XXX", "X.........");

        StepResult result = board.applyMove(new TetrisMove(0, 3));

        assertEquals(1, result.rowsCleared());
        assertEquals(1, filledCells(board));
        assertEquals(Cell.GARBAGE, board.cellAt(TetrisBoard.HEIGHT - 1, 0),
                "the survivor dropped from row 18 to row 19");
    }

    @Test
    void aPartialRowIsNotCleared() {
        TetrisBoard board = boardStartingWith(Tetromino.O);
        board.setStack("XXXXXXXX..");

        StepResult result = board.applyMove(new TetrisMove(0, 0));

        assertEquals(0, result.rowsCleared());
        assertTrue(filledCells(board) > 8);
    }

    // ----------------------------------------------------------- player input

    @Test
    void movesStopAtBothWalls() {
        TetrisBoard board = boardStartingWith(Tetromino.O);

        int guard = 0;
        while (board.moveLeft() && guard++ < TetrisBoard.WIDTH + 2) {
            // slide to the wall
        }
        assertEquals(0, board.currentColumn());
        assertFalse(board.moveLeft());

        guard = 0;
        while (board.moveRight() && guard++ < TetrisBoard.WIDTH + 2) {
            // slide to the other wall
        }
        assertEquals(TetrisBoard.WIDTH - 2, board.currentColumn());
        assertFalse(board.moveRight());
    }

    @Test
    void standingAPieceUpKeepsItsLeftmostColumn() {
        TetrisBoard board = boardStartingWith(Tetromino.I);
        while (board.moveRight()) {
            // park the horizontal I against the right wall
        }
        assertEquals(TetrisBoard.WIDTH - 4, board.currentColumn());

        assertTrue(board.rotate(), "standing the I up at the wall should succeed");
        assertEquals(1, board.currentRotation());
        assertEquals(TetrisBoard.WIDTH - 4, board.currentColumn(),
                "a rotation that narrows the piece leaves its left edge alone");
    }

    @Test
    void rotatingIntoTheRightWallPullsThePieceBackOnBoard() {
        TetrisBoard board = boardStartingWith(Tetromino.I);
        board.rotate(); // stand it up: width 1
        while (board.moveRight()) {
            // park the vertical I in the last column
        }
        assertEquals(TetrisBoard.WIDTH - 1, board.currentColumn());

        assertTrue(board.rotate(), "laying the I back down should nudge it inboard");
        assertEquals(TetrisBoard.WIDTH - 4, board.currentColumn());
    }

    @Test
    void rotationIsRefusedWhenTheStackIsInTheWay() {
        TetrisBoard board = boardStartingWith(Tetromino.I);
        // Fill every row but the top one: the horizontal I has room to sit, but no
        // free channel beneath it to stand up into.
        String[] rows = new String[TetrisBoard.HEIGHT - 1];
        java.util.Arrays.fill(rows, "XXXXXXXXXX");
        board.setStack(rows);

        assertFalse(board.rotate());
        assertEquals(0, board.currentRotation());
    }

    // --------------------------------------------------------------- garbage

    @Test
    void garbageArrivesAtTheBottomWithExactlyOneHole() {
        TetrisBoard board = new TetrisBoard(3);

        board.pushGarbage(1);

        int holes = 0;
        for (int c = 0; c < TetrisBoard.WIDTH; c++) {
            if (board.cellAt(TetrisBoard.HEIGHT - 1, c) == Cell.EMPTY) {
                holes++;
            } else {
                assertEquals(Cell.GARBAGE, board.cellAt(TetrisBoard.HEIGHT - 1, c));
            }
        }
        assertEquals(1, holes);
    }

    @Test
    void garbagePushesTheExistingStackUpwards() {
        TetrisBoard board = new TetrisBoard(3);
        board.setStack("X.........");

        board.pushGarbage(2);

        assertEquals(Cell.GARBAGE, board.cellAt(TetrisBoard.HEIGHT - 3, 0),
                "the original square rose two rows");
        assertEquals(Cell.EMPTY, board.cellAt(TetrisBoard.HEIGHT - 3, 1));
    }

    @Test
    void garbageThatWouldOverflowTheTopTopsTheBoardOut() {
        TetrisBoard board = new TetrisBoard(3);
        String[] rows = new String[TetrisBoard.HEIGHT];
        java.util.Arrays.fill(rows, "X.........");
        board.setStack(rows);

        board.pushGarbage(1);

        assertTrue(board.isToppedOut());
    }

    @Test
    void garbageIsIgnoredOnceTheBoardIsDead() {
        TetrisBoard board = new TetrisBoard(3);
        String[] rows = new String[TetrisBoard.HEIGHT];
        java.util.Arrays.fill(rows, "X.........");
        board.setStack(rows);
        board.pushGarbage(1);
        int before = filledCells(board);

        board.pushGarbage(3);

        assertEquals(before, filledCells(board));
    }

    // --------------------------------------------------------------- top out

    @Test
    void aBoardThatIsNeverClearedTopsOut() {
        TetrisBoard board = new TetrisBoard(11);

        int drops = 0;
        while (!board.isToppedOut() && drops < 100) {
            board.hardDrop();
            drops++;
        }

        assertTrue(board.isToppedOut(), "piling pieces in the spawn columns must top out");
        assertTrue(drops < 60, "took an implausible " + drops + " drops to top out");
    }

    @Test
    void clearBringsADeadBoardBackToLife() {
        TetrisBoard board = new TetrisBoard(11);
        while (!board.isToppedOut()) {
            board.hardDrop();
        }

        board.clear();

        assertFalse(board.isToppedOut());
        assertEquals(0, filledCells(board));
        assertNotNull(board.current());
    }

    @Test
    void aDeadBoardIgnoresInputAndGravity() {
        TetrisBoard board = new TetrisBoard(11);
        while (!board.isToppedOut()) {
            board.hardDrop();
        }
        int before = filledCells(board);

        assertFalse(board.moveLeft());
        assertFalse(board.rotate());
        assertTrue(board.tick().toppedOut());

        assertEquals(before, filledCells(board));
    }

    // --------------------------------------------------------- agent queries

    @Test
    void restingRowRejectsPlacementsThatHangOffTheBoard() {
        TetrisBoard board = new TetrisBoard(1);

        assertEquals(-1, board.restingRow(Tetromino.I, 0, -1));
        assertEquals(-1, board.restingRow(Tetromino.I, 0, 7), "a 4-wide piece cannot start at column 7");
        assertTrue(board.restingRow(Tetromino.I, 0, 6) >= 0);
    }

    @Test
    void restingRowFindsTheFloorAndTheTopOfTheStack() {
        TetrisBoard board = new TetrisBoard(1);
        assertEquals(TetrisBoard.HEIGHT - 1, board.restingRow(Tetromino.I, 0, 0));

        board.setStack("XXXXXXXXXX");
        assertEquals(TetrisBoard.HEIGHT - 2, board.restingRow(Tetromino.I, 0, 0));
    }

    @Test
    void isLegalRejectsOutOfRangeRotationsAndColumns() {
        TetrisBoard board = boardStartingWith(Tetromino.I);

        assertTrue(board.isLegal(new TetrisMove(0, 3)));
        assertFalse(board.isLegal(new TetrisMove(4, 3)));
        assertFalse(board.isLegal(new TetrisMove(-1, 3)));
        assertFalse(board.isLegal(new TetrisMove(0, 9)));
    }

    @Test
    void columnHeightsMeasureUpFromTheFloor() {
        TetrisBoard board = new TetrisBoard(1);
        board.setStack("X........X", "X.........");

        int[] heights = board.columnHeights();

        assertEquals(2, heights[0]);
        assertEquals(0, heights[1]);
        assertEquals(1, heights[9]);
    }

    @Test
    void applyMoveClampsAnOutOfRangeColumnInsteadOfThrowing() {
        TetrisBoard board = boardStartingWith(Tetromino.I);

        StepResult result = board.applyMove(new TetrisMove(0, 99));

        assertTrue(result.locked());
        assertEquals(Cell.I, board.cellAt(TetrisBoard.HEIGHT - 1, TetrisBoard.WIDTH - 1));
    }

    @Test
    void snapshotDoesNotAliasTheLiveGrid() {
        TetrisBoard board = new TetrisBoard(1);
        board.setStack("XXXXXXXXXX");

        Cell[][] snapshot = board.snapshot();
        snapshot[TetrisBoard.HEIGHT - 1][0] = Cell.EMPTY;

        assertEquals(Cell.GARBAGE, board.cellAt(TetrisBoard.HEIGHT - 1, 0));
    }

    @Test
    void theBagEventuallyDealsAllSevenPieces() {
        TetrisBoard board = new TetrisBoard(5);
        Set<Tetromino> seen = EnumSet.noneOf(Tetromino.class);

        for (int i = 0; i < 40; i++) {
            seen.add(board.current());
            board.hardDrop();
            if (board.isToppedOut()) {
                board.clear();
            }
        }

        assertEquals(EnumSet.allOf(Tetromino.class), seen);
    }
}
