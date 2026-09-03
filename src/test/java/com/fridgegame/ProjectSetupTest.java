package com.fridgegame;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Phase 0 smoke test: proves the test harness runs and that resources are
 * packaged where {@link FridgeGameApp} looks for them. Needs no JavaFX
 * toolkit, so it is safe on a headless CI machine.
 */
class ProjectSetupTest {

    @Test
    @DisplayName("styles.css is on the classpath next to FridgeGameApp")
    void stylesheetIsPackaged() {
        assertNotNull(FridgeGameApp.class.getResource("styles.css"),
                "styles.css must live in src/main/resources/com/fridgegame/");
    }
}
