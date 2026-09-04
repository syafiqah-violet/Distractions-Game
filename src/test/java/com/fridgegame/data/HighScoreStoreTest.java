package com.fridgegame.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class HighScoreStoreTest {

    private final Preferences testNode = Preferences.userRoot().node("fridgegame-test/high-score");

    @AfterEach
    void cleanUp() throws BackingStoreException {
        testNode.removeNode();
    }

    @Test
    void defaultsToZeroWithNothingStored() {
        HighScoreStore store = new HighScoreStore(testNode);

        assertEquals(0, store.get());
    }

    @Test
    void submitUpdatesWhenHigherThanCurrent() {
        HighScoreStore store = new HighScoreStore(testNode);

        boolean updated = store.submit(50);

        assertTrue(updated);
        assertEquals(50, store.get());
    }

    @Test
    void submitIgnoresScoreThatDoesNotBeatCurrent() {
        HighScoreStore store = new HighScoreStore(testNode);
        store.submit(50);

        boolean updated = store.submit(20);

        assertFalse(updated);
        assertEquals(50, store.get());
    }

    @Test
    void newInstanceLoadsThePreviouslyPersistedValue() {
        new HighScoreStore(testNode).submit(75);

        HighScoreStore reloaded = new HighScoreStore(testNode);

        assertEquals(75, reloaded.get());
    }
}
