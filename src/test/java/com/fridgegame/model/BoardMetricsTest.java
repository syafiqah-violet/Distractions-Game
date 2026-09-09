package com.fridgegame.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The stack shape numbers, salvaged from the deleted heuristic bot.
 *
 * <p>{@link BoardMetrics#holes()} is now load-bearing for a different reason: it is how
 * the commentator notices that a placement buried cells, which is the "you missed the
 * right spot" moment worth remarking on.
 */
class BoardMetricsTest {

    @Test
    void anEmptyBoardHasNothingToReport() {
        BoardMetrics metrics = BoardMetrics.of(new TetrisBoard(1));

        assertEquals(0, metrics.holes());
        assertEquals(0, metrics.aggregateHeight());
        assertEquals(0, metrics.bumpiness());
    }

    @Test
    void aCellRoofedByAFilledOneCountsAsAHole() {
        TetrisBoard board = new TetrisBoard(1);
        // Bottom-up: the floor row has a gap in column 0, covered by the row above it.
        board.setStack(".XXXXXXXXX", "XXXXXXXXXX");

        assertEquals(1, BoardMetrics.of(board).holes());
    }

    @Test
    void anOpenGapAtTheSurfaceIsNotAHole() {
        TetrisBoard board = new TetrisBoard(1);
        board.setStack(".XXXXXXXXX");

        assertEquals(0, BoardMetrics.of(board).holes(),
                "nothing is covering it, so the player can still fill it");
    }

    @Test
    void severalStackedRoofsOverOneColumnCountSeparately() {
        TetrisBoard board = new TetrisBoard(1);
        board.setStack(".XXXXXXXXX", ".XXXXXXXXX", "XXXXXXXXXX");

        assertEquals(2, BoardMetrics.of(board).holes());
    }

    @Test
    void aggregateHeightSumsEveryColumn() {
        TetrisBoard board = new TetrisBoard(1);
        board.setStack("XXXXXXXXXX", "XXXXXXXXXX");

        assertEquals(2 * TetrisBoard.WIDTH, BoardMetrics.of(board).aggregateHeight());
    }

    @Test
    void columnHeightIsMeasuredToTheHighestFilledCellNotTheFilledCount() {
        TetrisBoard board = new TetrisBoard(1);
        board.setStack("..........", "X.........");

        assertEquals(2, BoardMetrics.of(board).aggregateHeight(),
                "a floating square still makes the column two tall");
    }

    @Test
    void bumpinessMeasuresTheSurfaceRatherThanItsHeight() {
        TetrisBoard flat = new TetrisBoard(1);
        flat.setStack("XXXXXXXXXX", "XXXXXXXXXX", "XXXXXXXXXX");

        TetrisBoard jagged = new TetrisBoard(1);
        jagged.setStack("X.X.X.X.X.", "X.X.X.X.X.", "X.X.X.X.X.");

        assertEquals(0, BoardMetrics.of(flat).bumpiness(), "a flat surface has no steps");
        assertTrue(BoardMetrics.of(jagged).bumpiness() > 0);
        assertEquals(BoardMetrics.of(flat).aggregateHeight() / 2,
                BoardMetrics.of(jagged).aggregateHeight(),
                "half the columns are filled, so half the height");
    }

    @Test
    void theFallingPieceIsNotCounted() {
        TetrisBoard board = new TetrisBoard(1);

        // A fresh board has a piece in flight and an empty stack.
        assertEquals(0, BoardMetrics.of(board).aggregateHeight(),
                "only locked squares shape the stack");
    }
}
