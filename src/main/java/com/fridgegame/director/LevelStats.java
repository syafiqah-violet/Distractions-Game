package com.fridgegame.director;

import com.fridgegame.model.LevelMode;

/**
 * What the player actually did in one level — the director's only input.
 *
 * <p>Deliberately raw counts rather than a difficulty verdict: the judgement is the
 * director's job, and keeping this a plain record means it can be logged, replayed and
 * asserted on without a running game.
 *
 * @param secondsRemaining what was left on the clock when the level ended (0 if it expired)
 */
public record LevelStats(
        LevelMode mode,
        int piecesLocked,
        int rowsCleared,
        int correctDrops,
        int wrongDrops,
        int topOuts,
        int secondsRemaining,
        int timeLimitSeconds) {

    /** Rows per piece placed. Around 0.25 is competent; below 0.1 means the player is drowning. */
    public double clearRate() {
        return piecesLocked == 0 ? 0 : (double) rowsCleared / piecesLocked;
    }

    /** Share of drops that went into the right zone, or {@code 1} if nothing was dropped. */
    public double dropAccuracy() {
        int drops = correctDrops + wrongDrops;
        return drops == 0 ? 1 : (double) correctDrops / drops;
    }

    /** Fraction of the clock left over. Near 0 means it was a near miss on time. */
    public double clockMargin() {
        return timeLimitSeconds == 0 ? 0 : (double) secondsRemaining / timeLimitSeconds;
    }

    /** Whether any sorting happened at all — {@link #dropAccuracy()} is meaningless if not. */
    public boolean hadDrops() {
        return correctDrops + wrongDrops > 0;
    }
}
