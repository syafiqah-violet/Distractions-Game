package com.fridgegame;

import com.fridgegame.controller.DragHandler;
import com.fridgegame.controller.GameController;
import com.fridgegame.data.ItemCatalog;
import com.fridgegame.model.GameState;
import com.fridgegame.view.CounterView;
import com.fridgegame.view.FridgeView;
import com.fridgegame.view.HudView;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

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

    @Override
    public void start(Stage stage) {
        GameState state = new GameState();
        state.startLevel(ItemCatalog.LEVELS.get(0));

        FridgeView fridgeView = new FridgeView();
        CounterView counterView = new CounterView(ItemCatalog.LEVELS.get(0).items());
        GameController controller = new GameController(state);
        DragHandler.wire(fridgeView, counterView, controller);

        BorderPane root = new BorderPane();
        root.getStyleClass().add("game-root");
        root.setPadding(new Insets(12));
        root.setTop(new HudView(state));
        root.setLeft(fridgeView);
        root.setCenter(counterView);

        scene = new Scene(root, WIDTH, HEIGHT);
        scene.getStylesheets().add(
                FridgeGameApp.class.getResource("styles.css").toExternalForm());

        stage.setTitle("Refrigerator Sorting Game");
        stage.setMinWidth(800);
        stage.setMinHeight(600);
        stage.setScene(scene);
        stage.show();
    }

    /** Swaps the visible screen, keeping the Scene (and its stylesheet) alive. */
    public void showScreen(javafx.scene.Parent root) {
        scene.setRoot(root);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
