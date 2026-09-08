package com.fridgegame;

import com.fridgegame.controller.AgentController;
import com.fridgegame.controller.DragHandler;
import com.fridgegame.controller.GameController;
import com.fridgegame.controller.TetrisController;
import com.fridgegame.data.HighScoreStore;
import com.fridgegame.data.ItemCatalog;
import com.fridgegame.llm.LlmClient;
import com.fridgegame.llm.LlmConfig;
import com.fridgegame.llm.LlmTetrisAgent;
import com.fridgegame.model.GameState;
import com.fridgegame.model.Level;
import com.fridgegame.view.CounterView;
import com.fridgegame.view.FridgeView;
import com.fridgegame.view.GameOverView;
import com.fridgegame.view.HudView;
import com.fridgegame.view.LevelCompleteView;
import com.fridgegame.view.VersusView;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.Stage;
import javafx.util.Duration;

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

    /** Points awarded for burying the opponent. */
    private static final int AGENT_TOP_OUT_BONUS = 40;

    /**
     * Per-move HTTP budget. Comfortably above the ~650ms the endpoint takes, but short
     * enough that a stalled request is abandoned rather than outliving the round.
     */
    private static final java.time.Duration LLM_MOVE_TIMEOUT = java.time.Duration.ofSeconds(3);

    private Scene scene;
    private GameState state;
    private GameController controller;
    private TetrisController tetris;
    private AgentController opponent;
    private boolean garbageEnabled;
    private boolean llmOnline;
    private String llmLabel = "Bot";
    private HighScoreStore highScoreStore;
    private Timeline timer;
    private BorderPane gameRoot;
    private HBox content;
    private VersusView versusView;
    private CounterView counterView;
    private int levelIndex;

    @Override
    public void start(Stage stage) {
        state = new GameState();
        controller = new GameController(state);
        controller.setOnLevelComplete(this::onLevelComplete);
        controller.setOnGameOver(() -> endGame(false));
        controller.setOnItemUnlocked(item ->
                DragHandler.makeDraggable(counterView.addItem(item), tetris));
        highScoreStore = new HighScoreStore();

        long seed = System.nanoTime();
        tetris = new TetrisController(seed);
        // A different seed, so the two boards do not get identical piece sequences.
        opponent = new AgentController(seed ^ 0x5DEECE66DL);
        versusView = new VersusView();

        tetris.setOnChanged(() -> versusView.renderPlayer(tetris.getBoard()));
        tetris.setOnRowsCleared(this::onRowsCleared);
        tetris.setOnTopOut(this::onTopOut);

        opponent.setOnChanged(() -> versusView.renderAgent(opponent.getBoard()));
        opponent.setOnRowsCleared(this::onAgentRowsCleared);
        opponent.setOnTopOut(this::onAgentTopOut);
        connectAgent();

        content = new HBox(14);
        gameRoot = new BorderPane();
        gameRoot.getStyleClass().add("game-root");
        gameRoot.setPadding(new Insets(12));
        gameRoot.setTop(new HudView(state, highScoreStore));
        gameRoot.setCenter(content);

        timer = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            controller.tick();
            refreshAgentStatus();
        }));
        timer.setCycleCount(Animation.INDEFINITE);

        scene = new Scene(gameRoot, WIDTH, HEIGHT);
        scene.getStylesheets().add(
                FridgeGameApp.class.getResource("styles.css").toExternalForm());
        tetris.installKeys(scene);

        stage.setTitle("Refrigerator Sorting Game");
        stage.setMinWidth(1100);
        stage.setMinHeight(720);
        stage.setScene(scene);
        stage.show();

        levelIndex = 0;
        loadLevel(levelIndex);
        timer.play();
    }

    /**
     * Probes the local LLM and promotes it to opponent if it answers.
     *
     * <p>Runs off the JavaFX thread and starts the game with the heuristic bot already in
     * place, so a slow or dead endpoint delays nothing — the swap just never happens.
     */
    private void connectAgent() {
        LlmConfig llmConfig = LlmConfig.load();
        versusView.setAgentInfo("Bot", "checking " + llmConfig.describe(), false);

        LlmClient client = new LlmClient(llmConfig, LLM_MOVE_TIMEOUT);
        client.reachable().thenAccept(probe -> Platform.runLater(() -> {
            llmOnline = probe.online();
            if (probe.online()) {
                opponent.setAgent(LlmTetrisAgent.using(client));
                llmLabel = llmConfig.model();
                versusView.setAgentInfo(llmLabel, probe.detail(), true);
            } else {
                versusView.setAgentInfo("Bot", "local bot · " + probe.detail(), false);
            }
        }));
    }

    /**
     * Keeps the agent caption honest about who is actually deciding moves.
     *
     * <p>Worth showing: it makes a silently-degrading LLM visible instead of leaving the
     * player to wonder why the opponent got dull.
     */
    private void refreshAgentStatus() {
        if (!llmOnline) {
            return;
        }
        versusView.setAgentInfo(llmLabel,
                opponent.getAgentMoves() + " LLM · " + opponent.getFallbackMoves() + " fallback",
                true);
    }

    /** Builds the fridge/counter for {@code index} and wires drag & drop for it. */
    private void loadLevel(int index) {
        Level level = ItemCatalog.LEVELS.get(index);
        controller.startLevel(level);

        FridgeView fridgeView = new FridgeView();
        counterView = new CounterView();
        counterView.setPrefWidth(COUNTER_WIDTH);
        counterView.setMinWidth(COUNTER_WIDTH);
        DragHandler.wireZones(fridgeView, controller);

        HBox.setHgrow(fridgeView, Priority.ALWAYS);
        content.getChildren().setAll(versusView, counterView, fridgeView);

        garbageEnabled = level.sendsGarbage();
        tetris.startLevel(level.gravityMillis());
        opponent.startLevel(level.aiGravityMillis());
    }

    /** Each cleared row buys one grocery item onto the counter. */
    private void onRowsCleared(int rows) {
        controller.awardClearedRows(rows);
        versusView.flashPlayer(VersusView.CLEAR_FLASH);
    }

    /** Topping out costs a life; the board is wiped so play can continue. */
    private void onTopOut() {
        controller.penalizeTopOut();
        if (state.getLives() > 0) {
            tetris.resetAfterTopOut();
        }
    }

    /**
     * Versus-Tetris convention: an N-row clear by the opponent pushes N−1 garbage rows
     * at the player. A single line is therefore harmless, but a tetris hurts.
     */
    private void onAgentRowsCleared(int rows) {
        int garbage = GameController.garbageFor(rows);
        if (garbageEnabled && garbage > 0) {
            tetris.receiveGarbage(garbage);
            versusView.flashPlayer(VersusView.GARBAGE_FLASH);
        } else {
            versusView.flashAgent(VersusView.CLEAR_FLASH);
        }
    }

    /** Burying the opponent pays a bonus; its board resets so it keeps playing. */
    private void onAgentTopOut() {
        state.setScore(state.getScore() + AGENT_TOP_OUT_BONUS);
        opponent.resetAfterTopOut();
    }

    private void onLevelComplete(int timeBonus) {
        timer.stop();
        tetris.stop();
        opponent.stop();
        showScreen(new LevelCompleteView(state, timeBonus, this::proceedToNextLevel));
    }

    private void proceedToNextLevel() {
        levelIndex++;
        if (levelIndex < ItemCatalog.LEVELS.size()) {
            loadLevel(levelIndex);
            showScreen(gameRoot);
            timer.play();
        } else {
            endGame(true);
        }
    }

    private void endGame(boolean won) {
        timer.stop();
        tetris.stop();
        opponent.stop();
        boolean isNewHighScore = highScoreStore.submit(state.getScore());
        showScreen(new GameOverView(state, won, isNewHighScore, highScoreStore.get(), this::restart));
    }

    private void restart() {
        state.reset();
        levelIndex = 0;
        loadLevel(levelIndex);
        showScreen(gameRoot);
        timer.play();
    }

    /** Swaps the visible screen, keeping the Scene (and its stylesheet) alive. */
    public void showScreen(Parent root) {
        scene.setRoot(root);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
