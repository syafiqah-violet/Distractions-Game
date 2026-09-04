package com.fridgegame.data;

import java.util.prefs.Preferences;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;

/** Persists the best score seen across app launches via {@link Preferences}. */
public final class HighScoreStore {

    private static final String KEY = "highScore";

    private final Preferences prefs;
    private final IntegerProperty highScore;

    public HighScoreStore() {
        this(Preferences.userNodeForPackage(HighScoreStore.class));
    }

    HighScoreStore(Preferences prefs) {
        this.prefs = prefs;
        this.highScore = new SimpleIntegerProperty(prefs.getInt(KEY, 0));
    }

    public IntegerProperty highScoreProperty() {
        return highScore;
    }

    public int get() {
        return highScore.get();
    }

    /** Persists {@code score} as the new high score if it beats the current one; returns whether it did. */
    public boolean submit(int score) {
        if (score > get()) {
            highScore.set(score);
            prefs.putInt(KEY, score);
            return true;
        }
        return false;
    }
}
