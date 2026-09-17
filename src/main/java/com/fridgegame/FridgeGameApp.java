package com.fridgegame;

import com.fridgegame.audio.PiperVoice;
import com.fridgegame.audio.RivalVoice;
import com.fridgegame.audio.Sfx;
import com.fridgegame.controller.CommentaryController;
import com.fridgegame.controller.DragHandler;
import com.fridgegame.controller.GameController;
import com.fridgegame.controller.TetrisController;
import com.fridgegame.data.HighScoreStore;
import com.fridgegame.data.ItemCatalog;
import com.fridgegame.director.FixedLevelDirector;
import com.fridgegame.director.LevelDirector;
import com.fridgegame.director.LevelStats;
import com.fridgegame.llm.CommentaryEvent;
import com.fridgegame.llm.LlmClient;
import com.fridgegame.llm.LlmCommentator;
import com.fridgegame.llm.LlmConfig;
import com.fridgegame.llm.LlmLevelDirector;
import com.fridgegame.llm.LlmLog;
import com.fridgegame.llm.TtsConfig;
import com.fridgegame.model.BoardMetrics;
import com.fridgegame.model.GameState;
import com.fridgegame.model.Level;
import com.fridgegame.model.LevelMode;
import com.fridgegame.view.AboutView;
import com.fridgegame.view.CommentaryView;
import com.fridgegame.view.CounterView;
import com.fridgegame.view.FridgeView;
import com.fridgegame.view.GameOverView;
import com.fridgegame.view.HudView;
import com.fridgegame.view.LevelCompleteView;
import com.fridgegame.view.PauseOverlay;
import com.fridgegame.view.StartView;
import com.fridgegame.view.TetrisPanel;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.util.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Owns the {@link Stage} and the single {@link Scene}.
 *
 * <p>Screens are swapped with {@code scene.setRoot(...)} rather than by
 * building a new Scene, which would drop the stylesheet and reset the
 * window size.
 */
public class FridgeGameApp extends Application {

    private static final int WIDTH = 1280;
    private static final int HEIGHT = 780;
    private static final double COUNTER_WIDTH = 210;

    /** Seconds-remaining marks where the rival starts counting down at you. */
    private static final int[] CLOCK_LOW_MARKS = {30, 10};

    /**
     * Per-request budget for a caption. Short on purpose: a taunt that lands after the
     * player has moved on is worse than silence, and {@link CommentaryController} would
     * discard it anyway.
     */
    private static final java.time.Duration COMMENTARY_TIMEOUT = java.time.Duration.ofSeconds(3);

    /**
     * Per-request budget for a difficulty decision. Longer because it runs while the
     * player reads the level-complete card, so its latency costs nothing.
     */
    private static final java.time.Duration DIRECTOR_TIMEOUT = java.time.Duration.ofSeconds(6);

    /**
     * Per-request budget for synthesizing one caption. Longer than the caption request
     * that produced it: a TTS server is doing audio work, and the line is already on
     * screen by the time this runs, so a slow voice is late rather than wrong. A warm
     * Piper answers in about 160ms, so this is headroom rather than an expectation.
     */
    private static final java.time.Duration TTS_TIMEOUT = java.time.Duration.ofSeconds(8);

    /**
     * How long to let the speech server load its voice model before giving up on it.
     *
     * <p>Generous because this is a one-off cost paid off-screen while the player is still
     * on level 1, and because model load time belongs to the machine: the same voice that
     * is ready in two seconds here may take fifteen on a slower disk.
     */
    private static final java.time.Duration TTS_READY_TIMEOUT = java.time.Duration.ofSeconds(45);

    private Scene scene;
    private GameState state;
    private GameController controller;
    private TetrisController tetris;
    private HighScoreStore highScoreStore;
    private Timeline timer;
    private StackPane gameShell;
    private BorderPane gameRoot;
    private HBox content;
    private HudView hudView;
    private TetrisPanel tetrisPanel;
    private CounterView counterView;
    private CommentaryView commentaryView;
    private PauseOverlay pauseOverlay;
    private StartView startView;

    private CommentaryController commentary;
    private LevelDirector director = new FixedLevelDirector();
    private CompletableFuture<Level> nextLevelFuture;

    /**
     * Says the captions out loud. Silent until {@link #connectLlm} brings a voice up, and
     * still silent afterwards if the speech server never became ready.
     */
    private RivalVoice voice = RivalVoice.SILENT;

    private Level currentLevel;
    private int levelIndex;
    private boolean levelRunning;
    private boolean paused;

    /** Suppresses key auto-repeat, which would otherwise strobe the pause toggle. */
    private boolean pauseKeyHeld;

    /** Holes in the stack as of the last piece lock, so a new one can be attributed. */
    private int lastHoles;

    @Override
    public void start(Stage stage) {
        state = new GameState();
        controller = new GameController(state);
        controller.setOnLevelComplete(this::onLevelComplete);
        controller.setOnGameOver(() -> endGame(false));
        controller.setOnItemUnlocked(item ->
                DragHandler.makeDraggable(counterView.addItem(item), tetris, controller));
        controller.setOnWrongDrop((item, zone) -> comment(CommentaryEvent.Kind.WRONG_DROP,
                "they put " + item.name() + " in the " + zone.label() + " - wrong zone"));
        controller.setOnStreak(streak -> comment(CommentaryEvent.Kind.STREAK,
                "they have sorted " + streak + " in a row without a mistake"));
        controller.setOnActivity(() -> {
            if (commentary != null) {
                commentary.noteActivity();
            }
        });
        highScoreStore = new HighScoreStore();

        tetris = new TetrisController(System.nanoTime());
        tetrisPanel = new TetrisPanel();
        tetris.setOnChanged(() -> tetrisPanel.render(tetris.getBoard()));
        tetris.setOnRowsCleared(this::onRowsCleared);
        tetris.setOnTopOut(this::onTopOut);
        tetris.setOnPieceLocked(this::onPieceLocked);

        hudView = new HudView(state, highScoreStore);
        hudView.setOnPauseToggle(this::togglePause);
        commentaryView = new CommentaryView();
        // The card floats over the fridge, whose zones are live drop targets. Without this
        // it would silently swallow drops aimed at the bottom-right corner.
        commentaryView.setMouseTransparent(true);
        pauseOverlay = new PauseOverlay(() -> setPaused(false), this::startGame);
        startView = new StartView(this::startGame, this::showAbout);

        content = new HBox(14);
        gameRoot = new BorderPane();
        gameRoot.getStyleClass().add("game-root");
        gameRoot.setPadding(new Insets(12));
        gameRoot.setTop(hudView);
        gameRoot.setCenter(content);

        // The pause overlay is last on purpose: it is the top layer, and anything stacked
        // above it — the rival's card in particular — would float over the pause screen.
        gameShell = new StackPane(gameRoot, commentaryView, pauseOverlay);
        StackPane.setAlignment(commentaryView, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(commentaryView, new Insets(0, 18, 18, 0));

        timer = new Timeline(new KeyFrame(Duration.seconds(1), e -> onSecond()));
        timer.setCycleCount(Animation.INDEFINITE);

        scene = new Scene(gameShell, WIDTH, HEIGHT);
        scene.getStylesheets().add(
                FridgeGameApp.class.getResource("styles.css").toExternalForm());
        installPauseKeys(scene);
        tetris.installKeys(scene);

        stage.setTitle("Distractions game");
        stage.setMinWidth(1100);
        stage.setMinHeight(720);
        stage.setScene(scene);
        stage.show();

        // Probing while the player reads the start screen makes the latency free.
        connectLlm();

        showScreen(startView);
    }

    /**
     * Binds Esc and P to the pause toggle.
     *
     * <p>Deliberately its own Scene filter rather than a case inside
     * {@link TetrisController#installKeys}: that filter returns early unless a Tetris board
     * is active, so a pause key living there would be dead on the sorting level — the one
     * level with no board, and the first one every player meets.
     *
     * <p>Registered before the Tetris filter so the toggle is seen regardless of what the
     * game keys later consume.
     */
    private void installPauseKeys(Scene scene) {
        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() != KeyCode.ESCAPE && event.getCode() != KeyCode.P) {
                return;
            }
            event.consume();
            // Held keys auto-repeat, and a repeating toggle strobes the game rather than
            // pausing it. JavaFX exposes no isRepeat(), so track the key down ourselves.
            if (pauseKeyHeld || !levelRunning) {
                return;
            }
            pauseKeyHeld = true;
            togglePause();
        });
        scene.addEventFilter(KeyEvent.KEY_RELEASED, event -> {
            if (event.getCode() == KeyCode.ESCAPE || event.getCode() == KeyCode.P) {
                pauseKeyHeld = false;
            }
        });
    }

    /** Begins a fresh run from level 1. Nothing is running until this is called. */
    private void startGame() {
        // A new game can be asked for mid-pause, where the timer is parked part-way
        // through a second; play() alone would resume there and dock the first tick.
        timer.stop();
        state.reset();
        levelIndex = 0;
        nextLevelFuture = null;
        loadLevel(ItemCatalog.LEVELS.get(0));
        showScreen(gameShell);
        timer.play();
    }

    private void showAbout() {
        showScreen(new AboutView(this::startGame));
    }

    private void togglePause() {
        // Mirrors setPaused's own guard, so a toggle that would do nothing also makes no
        // sound — a click with no pause behind it reads as a swallowed input.
        if (!levelRunning) {
            return;
        }
        // The click lives here rather than on the HUD button, so the Esc/P shortcut — the
        // same action by another route — sounds the same. setPaused() would be the wrong
        // seam: forceUnpause() reaches it during level teardown, which must stay silent.
        Sfx.click();
        setPaused(!paused);
    }

    /**
     * Freezes or resumes everything the player is being timed against.
     *
     * <p>{@code timer.pause()}, never {@code timer.stop()}: stop rewinds the playhead, so
     * pausing nine tenths of the way through a second and resuming would hand back a whole
     * one — repeatable for as long as the player cares to tap the key, and invisible on a
     * HUD that only renders whole seconds.
     */
    private void setPaused(boolean value) {
        if (paused == value || !levelRunning) {
            return;
        }
        paused = value;
        tetris.setPaused(value);
        controller.setPaused(value);
        if (value) {
            timer.pause();
            // Mid-sentence when the player hits Esc: the caption stays on the card, but
            // the rival stops talking over a game that is no longer running.
            voice.stop();
            if (commentary != null) {
                commentary.notePauseStarted();
            }
        } else {
            if (commentary != null) {
                commentary.noteResumed();
            }
            timer.play();
        }
        hudView.setPaused(value);
        pauseOverlay.setVisible(value);
        pauseOverlay.setManaged(value);
    }

    /**
     * Clears a pause on the way out of a level, whatever the reason for leaving.
     *
     * <p>Separate from {@link #setPaused} because that one refuses to act once
     * {@code levelRunning} is false, which is exactly the moment teardown needs it to. The
     * case this exists for: pause, then lose the last life. {@code showScreen} swaps the
     * root and takes the still-visible overlay off screen with it, so nothing looks wrong —
     * until Play Again brings {@code gameShell} back with the overlay still there, over a
     * running level, with every drop refused and no way to clear it.
     *
     * <p>Leaves {@code timer} alone; callers stop it themselves.
     */
    private void forceUnpause() {
        paused = false;
        pauseKeyHeld = false;
        tetris.setPaused(false);
        controller.setPaused(false);
        hudView.setPaused(false);
        pauseOverlay.setVisible(false);
        pauseOverlay.setManaged(false);
    }

    /**
     * Probes the local LLM and, if it answers, gives it its two jobs.
     *
     * <p>Runs off the JavaFX thread and the game starts without waiting: an unreachable
     * endpoint costs the rival's commentary and the adaptive difficulty, nothing else.
     * Both fall back to something playable, so there is no reason to block on it.
     */
    private void connectLlm() {
        LlmConfig config = LlmConfig.load();
        LlmLog.note("probing " + config.describe());

        new LlmClient(config, COMMENTARY_TIMEOUT).reachable()
                .thenAccept(probe -> Platform.runLater(() -> {
                    if (!probe.online()) {
                        LlmLog.note("offline (" + probe.detail() + ") - no rival commentary, "
                                + "levels use their authored difficulty");
                        return;
                    }
                    LlmLog.note("online: " + config.describe());
                    connectVoice();
                    commentary = new CommentaryController(
                            LlmCommentator.using(new LlmClient(config, COMMENTARY_TIMEOUT)),
                            // Reads the field per call rather than capturing it, so the
                            // voice probe is free to land after this controller is built.
                            line -> {
                                commentaryView.show(line);
                                voice.speak(line);
                            },
                            // Monotonic rather than wall-clock: every threshold in that
                            // class is an elapsed-time test, and none of them should move
                            // because an NTP correction landed mid-level.
                            () -> System.nanoTime() / 1_000_000L,
                            Platform::runLater);
                    director = LlmLevelDirector.using(
                            new LlmClient(config, DIRECTOR_TIMEOUT), System.nanoTime());
                    // The probe may land after a level is already up.
                    if (levelRunning && currentLevel.mode() == LevelMode.COMBINED) {
                        startCommentary();
                        // ...and it may land while that level is paused, in which case this
                        // brand-new controller has not seen the pause begin.
                        if (paused) {
                            commentary.notePauseStarted();
                        }
                    }
                }));
    }

    /**
     * Brings up the voice that says the rival's captions out loud.
     *
     * <p>Called only once the LLM has answered, because without it there are no captions to
     * speak and no reason to spend a process on a speech server. That also puts the model
     * load on level 1, well before the commentary itself starts on level 3.
     *
     * <p>Failure here is not an error. A machine without Piper installed plays exactly the
     * same game with exactly the same captions, read rather than heard.
     */
    private void connectVoice() {
        TtsConfig ttsConfig = TtsConfig.load();
        if (!ttsConfig.enabled()) {
            LlmLog.note("rival voice disabled by config - captions stay text-only");
            return;
        }
        PiperVoice.start(ttsConfig, TTS_TIMEOUT, TTS_READY_TIMEOUT)
                .thenAccept(ready -> Platform.runLater(() -> {
                    voice = ready;
                    LlmLog.note("rival voice: " + ready.describe());
                }));
    }

    /**
     * Builds the screen for {@code level} and starts it.
     *
     * <p>The mode decides what exists: a sorting level has no board and never starts
     * gravity, a Tetris level builds no counter or fridge at all.
     */
    private void loadLevel(Level level) {
        // The one invariant that makes every other unpause belt-and-braces: a level never
        // starts paused, however the previous one ended.
        forceUnpause();
        // Whatever the rival was saying was about the level that just ended.
        voice.stop();
        currentLevel = level;
        LevelMode mode = level.mode();
        hudView.applyMode(mode);
        commentaryView.clear();

        if (mode.hasSorting()) {
            FridgeView fridgeView = new FridgeView();
            counterView = new CounterView();
            counterView.setPrefWidth(COUNTER_WIDTH);
            counterView.setMinWidth(COUNTER_WIDTH);
            DragHandler.wireZones(fridgeView, controller);
            HBox.setHgrow(fridgeView, Priority.ALWAYS);

            content.setAlignment(Pos.TOP_LEFT);
            if (mode.hasTetris()) {
                content.getChildren().setAll(tetrisPanel, counterView, fridgeView);
            } else {
                content.getChildren().setAll(counterView, fridgeView);
            }
        } else {
            counterView = null;
            content.setAlignment(Pos.TOP_CENTER);
            content.getChildren().setAll(tetrisPanel);
        }

        // After the views exist: a SORT_ONLY level releases its whole quota synchronously,
        // straight into the counter that was just built.
        controller.startLevel(level);

        if (mode.hasTetris()) {
            tetris.startLevel(level.gravityMillis());
            lastHoles = BoardMetrics.of(tetris.getBoard()).holes();
        } else {
            tetris.stop();
        }

        levelRunning = true;
        if (mode == LevelMode.COMBINED) {
            startCommentary();
        } else if (commentary != null) {
            commentary.stop();
        }
    }

    /** The rival only shows up for the level where there is enough going on to mock. */
    private void startCommentary() {
        if (commentary == null) {
            return;
        }
        commentary.start();
        comment(CommentaryEvent.Kind.LEVEL_START,
                "the final level just started: " + currentLevel.items().size()
                        + " groceries to earn and sort in " + currentLevel.timeLimitSeconds()
                        + " seconds");
    }

    /** One tick of the shared countdown, plus the rival's chance to speak. */
    private void onSecond() {
        int before = state.getSecondsLeft();
        controller.tick();
        if (!levelRunning) {
            return; // tick() ended the level
        }
        for (int mark : CLOCK_LOW_MARKS) {
            if (before > mark && state.getSecondsLeft() == mark && state.getItemsLeft() > 0) {
                comment(CommentaryEvent.Kind.CLOCK_LOW,
                        mark + " seconds left and " + state.getItemsLeft() + " still unsorted");
            }
        }
        if (commentary != null) {
            commentary.poll(describeState());
        }
    }

    /** Each cleared row scores, and on a level with a quota also buys a grocery item. */
    private void onRowsCleared(int rows) {
        int released = controller.awardClearedRows(rows);
        tetrisPanel.flash(TetrisPanel.CLEAR_FLASH);
        comment(CommentaryEvent.Kind.ROWS_CLEARED, released > 0
                ? "they cleared " + rows + " row(s) and earned " + released + " groceries"
                : "they cleared " + rows + " row(s)");
    }

    /**
     * A piece just settled: if it buried cells, that was a decision worth remarking on.
     *
     * <p>Sampled here rather than during the fall, so a hole can be attributed to the
     * placement the player chose instead of to gravity mid-descent.
     */
    private void onPieceLocked() {
        Sfx.lock();
        // Placing pieces is playing, even when the placement was unremarkable. Without
        // this the idle timer accuses an actively-playing player of staring at the screen.
        if (commentary != null) {
            commentary.noteActivity();
        }
        int holes = BoardMetrics.of(tetris.getBoard()).holes();
        if (holes > lastHoles) {
            comment(CommentaryEvent.Kind.NEW_HOLES,
                    "that placement buried " + (holes - lastHoles)
                            + " cell(s) they can no longer reach");
        }
        lastHoles = holes;
    }

    /** Topping out costs a life; the board is wiped so play can continue. */
    private void onTopOut() {
        comment(CommentaryEvent.Kind.TOP_OUT, "their stack hit the ceiling and they lost a life");
        controller.penalizeTopOut();
        if (state.getLives() > 0) {
            tetris.resetAfterTopOut();
            lastHoles = 0;
        }
    }

    /**
     * Ends the level and asks the director for the next one straight away.
     *
     * <p>The request overlaps the level-complete card, so by the time anyone clicks
     * "Next Level" the answer has almost always landed — and if it has not,
     * {@link #proceedToNextLevel} just uses the authored level instead of waiting.
     */
    private void onLevelComplete(int timeBonus) {
        forceUnpause();
        levelRunning = false;
        timer.stop();
        tetris.stop();
        voice.stop();
        if (commentary != null) {
            commentary.stop();
        }
        commentaryView.clear();

        int next = levelIndex + 1;
        if (next < ItemCatalog.LEVELS.size()) {
            LevelStats stats = controller.snapshot(tetris.getPiecesLocked());
            nextLevelFuture = director.nextLevel(ItemCatalog.LEVELS.get(next), stats);
        } else {
            nextLevelFuture = null;
        }
        Sfx.applause();
        showScreen(new LevelCompleteView(
                state, currentLevel.mode(), timeBonus, this::proceedToNextLevel));
    }

    private void proceedToNextLevel() {
        // Ten seconds of applause outlives the card that started it by a wide margin, and
        // the player can dismiss that card after one. Stopping here rather than letting it
        // ring out keeps the ovation on the screen it belongs to.
        //
        // Before the endGame branch, not after: the win plays its own applause, and
        // stopping afterwards would cut the victory off at the knees.
        Sfx.stopApplause();
        levelIndex++;
        if (levelIndex >= ItemCatalog.LEVELS.size()) {
            endGame(true);
            return;
        }
        Level authored = ItemCatalog.LEVELS.get(levelIndex);
        // getNow, never join: the JavaFX thread must not block on an HTTP call. A player
        // who clicks through faster than the request gets the authored level, which is a
        // fine outcome — just not one the console should describe as directed.
        Level level = nextLevelFuture == null ? authored : nextLevelFuture.getNow(authored);
        if (level == authored && nextLevelFuture != null && !nextLevelFuture.isDone()) {
            LlmLog.note("director still thinking, clicked through to the authored level");
        }
        LlmLog.director("level " + level.number() + " loaded: mode=" + level.mode()
                + " clock=" + level.timeLimitSeconds() + "s gravity=" + level.gravityMillis()
                + "ms items=" + level.items().size()
                + (level == authored ? " (authored)" : " (directed)"));
        loadLevel(level);
        showScreen(gameShell);
        timer.play();
    }

    private void endGame(boolean won) {
        forceUnpause();
        levelRunning = false;
        timer.stop();
        tetris.stop();
        // The run is over; a taunt landing under the Game Over card is about nothing.
        voice.stop();
        if (commentary != null) {
            commentary.stop();
        }
        commentaryView.clear();
        boolean isNewHighScore = highScoreStore.submit(state.getScore());
        // GameOverView serves both outcomes — "Game Over" and "You cleared every level!" —
        // so the branch is load-bearing rather than defensive: a failure sting under the
        // victory title would read as a bug.
        if (won) {
            Sfx.applause();
        } else {
            Sfx.fail();
        }
        showScreen(new GameOverView(state, won, isNewHighScore, highScoreStore.get(), this::restart));
    }

    /** Game Over returns to the front door rather than straight into another run. */
    private void restart() {
        // The third and last way off a card that may still be clapping: a win, then Play
        // Again. Nothing should still be cheering on the start screen.
        Sfx.stopApplause();
        state.reset();
        showScreen(startView);
    }

    /** Offers an event to the rival; a no-op when there is no rival or no level running. */
    private void comment(CommentaryEvent.Kind kind, String detail) {
        if (commentary == null || !levelRunning) {
            return;
        }
        commentary.offer(CommentaryEvent.of(kind, detail), describeState());
    }

    /** One line of game state, so the rival's tone can track how the player is doing. */
    private String describeState() {
        return "score=" + state.getScore()
                + " lives=" + state.getLives()
                + " time=" + state.getSecondsLeft() + "s"
                + " unsorted=" + state.getItemsLeft()
                + " rows=" + state.getRowsCleared();
    }

    /**
     * Releases the voice when the window closes.
     *
     * <p>The local backend is a child {@code powershell.exe}, and nothing else would ever
     * reap it: JavaFX exits, the JVM exits, and the helper sits there holding stdin open
     * for a game that is gone.
     */
    @Override
    public void stop() {
        voice.close();
    }

    /** Swaps the visible screen, keeping the Scene (and its stylesheet) alive. */
    public void showScreen(Parent root) {
        scene.setRoot(root);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
