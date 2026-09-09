package com.fridgegame.director;

import com.fridgegame.model.Level;
import java.util.concurrent.CompletableFuture;

/**
 * Chooses the next level's difficulty from how the last one actually went.
 *
 * <p>Replaces reading fixed numbers out of {@code ItemCatalog.LEVELS}: the table now
 * supplies a template — mode, clock, required rows — and the director fills in gravity and
 * the grocery quota, aiming to keep the player in flow rather than following a curve
 * authored for a player who does not exist.
 *
 * <p>Implementations must never fail the caller. Returning the template unchanged is
 * always a valid answer, and progression cannot be allowed to depend on a network call.
 */
public interface LevelDirector {

    /**
     * @param template the level as authored: mode, clock and required rows are fixed
     * @param last     measured performance on the level just finished
     * @return the level to actually play; the template itself if there is nothing to change
     */
    CompletableFuture<Level> nextLevel(Level template, LevelStats last);
}
