package com.fridgegame;

/**
 * Plain (non-{@code Application}) entry point.
 *
 * <p>A JAR whose main class extends {@code Application} fails with
 * "JavaFX runtime components are missing" when launched outside the
 * Maven plugin, so the manifest points here instead.
 */
public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) {
        FridgeGameApp.main(args);
    }
}
