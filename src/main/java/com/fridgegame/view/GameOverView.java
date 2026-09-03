package com.fridgegame.view;

import com.fridgegame.model.GameState;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

public class GameOverView extends VBox {

    public GameOverView(GameState state, boolean won, Runnable onRestart) {
        Label title = new Label(won ? "You cleared every level!" : "Game Over");
        title.getStyleClass().add("game-over-title");

        Label scoreLabel = new Label();
        scoreLabel.textProperty().bind(state.scoreProperty().asString("Final score: %d"));

        Button restartButton = new Button("Play Again");
        restartButton.setOnAction(e -> onRestart.run());

        getStyleClass().add("game-over");
        setAlignment(Pos.CENTER);
        setSpacing(16);
        getChildren().addAll(title, scoreLabel, restartButton);
    }
}
