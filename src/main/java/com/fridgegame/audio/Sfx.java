package com.fridgegame.audio;

import java.net.URL;
import javafx.scene.control.Button;
import javafx.scene.media.AudioClip;

/**
 * The three sound effects, and the only place that knows a sound file exists.
 *
 * <p>Every call is <b>fail-soft</b>. A clip that will not load — missing file, no audio
 * device, a media stack the platform did not ship — is cached as {@code null} and its
 * play method becomes a no-op for the rest of the run. Sound is garnish here; a machine
 * that cannot produce it should still get a playable game rather than a stack trace on
 * the FX thread mid-level.
 *
 * <p>Loading is lazy for the same reason it is guarded: constructing an {@link AudioClip}
 * touches the JavaFX media stack, and nothing should drag that in before the first sound
 * is actually wanted.
 */
public final class Sfx {

    private static final String SOUND_BASE = "/com/fridgegame/sound/";

    /** Fires on every piece that comes to rest, so it sits below the other two. */
    private static final Clip LOCK = new Clip("29177__junggle__btn367.wav", 0.5);
    private static final Clip CLICK = new Clip("29394__junggle__btn214.wav", 0.7);
    private static final Clip STORE = new Clip("29444__junggle__btn264.wav", 0.7);

    private static boolean muted;

    private Sfx() {
    }

    /** A UI button was pressed. */
    public static void click() {
        CLICK.play();
    }

    /** A Tetris piece settled onto the stack. */
    public static void lock() {
        LOCK.play();
    }

    /** A grocery landed in the zone it belongs to. */
    public static void store() {
        STORE.play();
    }

    /**
     * {@code setOnAction} that clicks first, then runs {@code action}.
     *
     * <p>Exists so a button's wiring stays one line and no view has to remember which
     * sound a button makes.
     */
    public static void onAction(Button button, Runnable action) {
        button.setOnAction(e -> {
            click();
            action.run();
        });
    }

    /** Silences every effect without unwiring anything. Nothing calls this yet. */
    public static void setMuted(boolean value) {
        muted = value;
    }

    public static boolean isMuted() {
        return muted;
    }

    /** One effect: resolved on first play, and at most once however badly that goes. */
    private static final class Clip {

        private final String fileName;
        private final double volume;

        private AudioClip clip;
        private boolean loaded;

        Clip(String fileName, double volume) {
            this.fileName = fileName;
            this.volume = volume;
        }

        void play() {
            if (muted) {
                return;
            }
            if (!loaded) {
                loaded = true;
                clip = load();
            }
            if (clip != null) {
                clip.play();
            }
        }

        private AudioClip load() {
            // Throwable, not Exception: a missing javafx.media on the module path surfaces
            // as NoClassDefFoundError, which is exactly the case this must survive.
            try {
                URL url = Sfx.class.getResource(SOUND_BASE + fileName);
                if (url == null) {
                    return null;
                }
                AudioClip loadedClip = new AudioClip(url.toExternalForm());
                loadedClip.setVolume(volume);
                return loadedClip;
            } catch (Throwable t) {
                return null;
            }
        }
    }
}
