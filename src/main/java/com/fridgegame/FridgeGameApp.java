package com.fridgegame;

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
import com.fridgegame.model.BoardMetrics;
import com.fridgegame.model.GameState;
import com.fridgegame.model.Level;
import com.fridgegame.model.LevelMode;
import com.fridgegame.view.CommentaryView;
import com.fridgegame.view.CounterView;
import com.fridgegame.view.FridgeView;
import com.fridgegame.view.GameOverView;
import com.fridgegame.view.HudView;
import com.fridgegame.view.LevelCompleteView;
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

    private CommentaryController commentary;
    private LevelDirector director = new FixedLevelDirector();
    private CompletableFuture<Level> nextLevelFuture;

    private Level currentLevel;
    private int levelIndex;
    private boolean levelRunning;

    /** Holes in the stack as of the last piece lock, so a new one can be attributed. */
    private int lastHoles;

    @Override
    public void start(Stage stage) {
        state = new GameState();
        controller = new GameController(state);
        controller.setOnLevelComplete(this::onLevelComplete);
        controller.setOnGameOver(() -> endGame(false));
        controller.setOnItemUnlocked(item ->
                DragHandler.makeDraggable(counterView.addItem(item), tetris));
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
        commentaryView = new CommentaryView();
        // The card floats over the fridge, whose zones are live drop targets. Without this
        // it would silently swallow drops aimed at the bottom-right corner.
        commentaryView.setMouseTransparent(true);

        content = new HBox(14);
        gameRoot = new BorderPane();
        gameRoot.getStyleClass().add("game-root");
        gameRoot.setPadding(new Insets(12));
        gameRoot.setTop(hudView);
        gameRoot.setCenter(content);

        gameShell = new StackPane(gameRoot, commentaryView);
        StackPane.setAlignment(commentaryView, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(commentaryView, new Insets(0, 18, 18, 0));

        timer = new Timeline(new KeyFrame(Duration.seconds(1), e -> onSecond()));
        timer.setCycleCount(Animation.INDEFINITE);

        scene = new Scene(gameShell, WIDTH, HEIGHT);
        scene.getStylesheets().add(
                FridgeGameApp.class.getResource("styles.css").toExternalForm());
        tetris.installKeys(scene);

        stage.setTitle("Refrigerator Sorting Game");
        stage.setMinWidth(1100);
        stage.setMinHeight(720);
        stage.setScene(scene);
        stage.show();

        connectLlm();

        levelIndex = 0;
        loadLevel(ItemCatalog.LEVELS.get(0));
        timer.play();
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
                    commentary = new CommentaryController(
                            LlmCommentator.using(new LlmClient(config, COMMENTARY_TIMEOUT)),
                            commentaryView::show,
                            System::currentTimeMillis,
                            Platform::runLater);
                    director = LlmLevelDirector.using(
                            new LlmClient(config, DIRECTOR_TIMEOUT), System.nanoTime());
                    // The probe may land after a level is already up.
                    if (levelRunning && currentLevel.mode() == LevelMode.COMBINED) {
                        startCommentary();
                    }
                }));
    }

    /**
     * Builds the screen for {@code level} and starts it.
     *
     * <p>The mode decides what exists: a sorting level has no board and never starts
     * gravity, a Tetris level builds no counter or fridge at all.
     */
    private void loadLevel(Level level) {
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
        levelRunning = false;
        timer.stop();
        tetris.stop();
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
        showScreen(new LevelCompleteView(
                state, currentLevel.mode(), timeBonus, this::proceedToNextLevel));
    }

    private void proceedToNextLevel() {
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
        levelRunning = false;
        timer.stop();
        tetris.stop();
        if (commentary != null) {
            commentary.stop();
        }
        commentaryView.clear();
        boolean isNewHighScore = highScoreStore.submit(state.getScore());
        showScreen(new GameOverView(state, won, isNewHighScore, highScoreStore.get(), this::restart));
    }

    private void restart() {
        state.reset();
        levelIndex = 0;
        nextLevelFuture = null;
        loadLevel(ItemCatalog.LEVELS.get(0));
        showScreen(gameShell);
        timer.play();
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

    /** Swaps the visible screen, keeping the Scene (and its stylesheet) alive. */
    public void showScreen(Parent root) {
        scene.setRoot(root);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
