package com.fridgegame.view;

import com.fridgegame.model.GameState;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

public class LevelCompleteView extends VBox {

    public LevelCompleteView(GameState state, int timeBonus, Runnable onNext) {
        Label title = new Label("Level " + state.getLevel() + " Complete!");
        title.getStyleClass().add("level-complete-title");

        Label bonusLabel = new Label("Time bonus: +" + timeBonus);

        Label scoreLabel = new Label();
        scoreLabel.textProperty().bind(state.scoreProperty().asString("Score: %d"));

        Button nextButton = new Button("Next Level");
        nextButton.setOnAction(e -> onNext.run());

        getStyleClass().add("level-complete");
        setAlignment(Pos.CENTER);
        setSpacing(16);
        getChildren().addAll(title, bonusLabel, scoreLabel, nextButton);
    }
}
