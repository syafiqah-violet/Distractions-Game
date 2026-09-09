package com.fridgegame.director;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fridgegame.data.ItemCatalog;
import com.fridgegame.model.Level;
import com.fridgegame.model.LevelMode;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The offline fallback and the derived readings the director judges on. */
class LevelDirectorTest {

    private static final Level TEMPLATE = new Level(
            3, LevelMode.COMBINED, ItemCatalog.pick(6, List.of(), 1L), 180, 480, 0);

    @Test
    void theFixedDirectorHandsBackTheAuthoredLevelUntouched() throws Exception {
        LevelStats stats = new LevelStats(LevelMode.COMBINED, 20, 3, 4, 1, 0, 30, 180);

        Level level = new FixedLevelDirector().nextLevel(TEMPLATE, stats).get();

        assertSame(TEMPLATE, level);
    }

    @Test
    void theFixedDirectorAnswersImmediately() {
        assertTrue(new FixedLevelDirector()
                        .nextLevel(TEMPLATE, new LevelStats(LevelMode.COMBINED, 0, 0, 0, 0, 0, 0, 60))
                        .isDone(),
                "nothing should ever wait on the offline path");
    }

    @Test
    void clearRateIsRowsPerPiece() {
        LevelStats stats = new LevelStats(LevelMode.TETRIS_ONLY, 40, 10, 0, 0, 0, 0, 60);

        assertEquals(0.25, stats.clearRate(), 1e-9);
    }

    @Test
    void clearRateIsZeroRatherThanUndefinedWhenNoPieceWasPlaced() {
        LevelStats stats = new LevelStats(LevelMode.SORT_ONLY, 0, 0, 4, 0, 0, 10, 60);

        assertEquals(0, stats.clearRate());
    }

    @Test
    void dropAccuracyIsTheShareOfCorrectDrops() {
        LevelStats stats = new LevelStats(LevelMode.COMBINED, 20, 5, 3, 1, 0, 0, 180);

        assertEquals(0.75, stats.dropAccuracy(), 1e-9);
        assertTrue(stats.hadDrops());
    }

    @Test
    void dropAccuracyReadsAsPerfectButFlagsThatNothingWasDropped() {
        LevelStats stats = new LevelStats(LevelMode.TETRIS_ONLY, 30, 4, 0, 0, 0, 0, 60);

        assertEquals(1, stats.dropAccuracy(),
                "a divide-by-zero would be worse; hadDrops is how callers tell");
        assertTrue(!stats.hadDrops());
    }

    @Test
    void clockMarginIsTheFractionOfTheClockLeftOver() {
        LevelStats comfortable = new LevelStats(LevelMode.COMBINED, 20, 6, 6, 0, 0, 90, 180);
        LevelStats nearMiss = new LevelStats(LevelMode.COMBINED, 40, 6, 6, 0, 0, 3, 180);

        assertEquals(0.5, comfortable.clockMargin(), 1e-9);
        assertTrue(nearMiss.clockMargin() < 0.02, "a near miss must read as one");
    }

    @Test
    void clockMarginIsZeroRatherThanUndefinedForATimelessLevel() {
        assertEquals(0, new LevelStats(LevelMode.COMBINED, 0, 0, 0, 0, 0, 0, 0).clockMargin());
    }
}
