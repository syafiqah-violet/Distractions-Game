package com.fridgegame;

import com.fridgegame.controller.DragHandler;
import com.fridgegame.controller.GameController;
import com.fridgegame.data.ItemCatalog;
import com.fridgegame.model.GameState;
import com.fridgegame.model.Level;
import com.fridgegame.view.CounterView;
import com.fridgegame.view.FridgeView;
import com.fridgegame.view.GameOverView;
import com.fridgegame.view.HudView;
import com.fridgegame.view.LevelCompleteView;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
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

    private static final int WIDTH = 1000;
    private static final int HEIGHT = 700;

    private Scene scene;
    private GameState state;
    private GameController controller;
    private Timeline timer;
    private BorderPane gameRoot;
    private int levelIndex;

    @Override
    public void start(Stage stage) {
        state = new GameState();
        controller = new GameController(state);
        controller.setOnLevelComplete(this::onLevelComplete);
        controller.setOnGameOver(() -> endGame(false));

        gameRoot = new BorderPane();
        gameRoot.getStyleClass().add("game-root");
        gameRoot.setPadding(new Insets(12));
        gameRoot.setTop(new HudView(state));

        timer = new Timeline(new KeyFrame(Duration.seconds(1), e -> controller.tick()));
        timer.setCycleCount(Animation.INDEFINITE);

        scene = new Scene(gameRoot, WIDTH, HEIGHT);
        scene.getStylesheets().add(
                FridgeGameApp.class.getResource("styles.css").toExternalForm());

        stage.setTitle("Refrigerator Sorting Game");
        stage.setMinWidth(800);
        stage.setMinHeight(600);
        stage.setScene(scene);
        stage.show();

        levelIndex = 0;
        loadLevel(levelIndex);
        timer.play();
    }

    /** Builds the fridge/counter for {@code index} and wires drag & drop for it. */
    private void loadLevel(int index) {
        Level level = ItemCatalog.LEVELS.get(index);
        controller.startLevel(level);

        FridgeView fridgeView = new FridgeView();
        CounterView counterView = new CounterView(level.items());
        DragHandler.wire(fridgeView, counterView, controller);

        gameRoot.setLeft(fridgeView);
        gameRoot.setCenter(counterView);
    }

    private void onLevelComplete(int timeBonus) {
        timer.stop();
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
        showScreen(new GameOverView(state, won, this::restart));
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
