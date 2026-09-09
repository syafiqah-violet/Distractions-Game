package com.fridgegame.director;

import com.fridgegame.model.Level;
import java.util.concurrent.CompletableFuture;

/**
 * The authored curve, unchanged — used whenever the LLM is unreachable.
 *
 * <p>Its existence is what makes the adaptive director optional rather than load-bearing:
 * the game plays identically end to end with no endpoint at all, just without the tuning.
 */
public final class FixedLevelDirector implements LevelDirector {

    @Override
    public CompletableFuture<Level> nextLevel(Level template, LevelStats last) {
        return CompletableFuture.completedFuture(template);
    }
}
