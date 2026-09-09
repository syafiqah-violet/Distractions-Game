package com.fridgegame.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TetrominoTest {

    @Test
    void everyRotationOfEveryPieceHasExactlyFourCells() {
        for (Tetromino piece : Tetromino.values()) {
            for (int rotation = 0; rotation < Tetromino.ROTATIONS; rotation++) {
                assertEquals(4, piece.cells(rotation).length,
                        piece + " rotation " + rotation + " must have 4 cells");
            }
        }
    }

    @Test
    void everyRotationIsNormalisedToTheOrigin() {
        for (Tetromino piece : Tetromino.values()) {
            for (int rotation = 0; rotation < Tetromino.ROTATIONS; rotation++) {
                int minRow = Integer.MAX_VALUE;
                int minCol = Integer.MAX_VALUE;
                for (int[] cell : piece.cells(rotation)) {
                    minRow = Math.min(minRow, cell[0]);
                    minCol = Math.min(minCol, cell[1]);
                }
                assertEquals(0, minRow, piece + " rotation " + rotation + " min row");
                assertEquals(0, minCol, piece + " rotation " + rotation + " min col");
            }
        }
    }

    @Test
    void everyRotationHasFourDistinctCells() {
        for (Tetromino piece : Tetromino.values()) {
            for (int rotation = 0; rotation < Tetromino.ROTATIONS; rotation++) {
                java.util.Set<String> seen = new java.util.HashSet<>();
                for (int[] cell : piece.cells(rotation)) {
                    assertTrue(seen.add(cell[0] + "," + cell[1]),
                            piece + " rotation " + rotation + " has a duplicated cell");
                }
            }
        }
    }

    @Test
    void widthAndHeightSwapWhenIPieceStandsUp() {
        assertEquals(4, Tetromino.I.width(0));
        assertEquals(1, Tetromino.I.height(0));
        assertEquals(1, Tetromino.I.width(1));
        assertEquals(4, Tetromino.I.height(1));
    }

    @Test
    void squareIsTwoByTwoInEveryRotation() {
        for (int rotation = 0; rotation < Tetromino.ROTATIONS; rotation++) {
            assertEquals(2, Tetromino.O.width(rotation));
            assertEquals(2, Tetromino.O.height(rotation));
        }
    }

    @Test
    void rotationIndexWrapsInsteadOfThrowing() {
        assertEquals(Tetromino.T.width(0), Tetromino.T.width(4));
        assertEquals(Tetromino.T.width(1), Tetromino.T.width(-3));
    }

    @Test
    void cellMatchesThePieceName() {
        for (Tetromino piece : Tetromino.values()) {
            assertEquals(piece.name(), piece.cell().name());
        }
    }
}
