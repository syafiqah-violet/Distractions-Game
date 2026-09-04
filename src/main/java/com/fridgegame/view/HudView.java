package com.fridgegame.view;

import com.fridgegame.data.HighScoreStore;
import com.fridgegame.model.GameState;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

public class HudView extends HBox {

    public HudView(GameState state, HighScoreStore highScoreStore) {
        Label scoreLabel = new Label();
        scoreLabel.textProperty().bind(state.scoreProperty().asString("Score: %d"));

        Label livesLabel = new Label();
        livesLabel.textProperty().bind(state.livesProperty().asString("Lives: %d"));

        Label levelLabel = new Label();
        levelLabel.textProperty().bind(state.levelProperty().asString("Level: %d"));

        Label highScoreLabel = new Label();
        highScoreLabel.textProperty().bind(highScoreStore.highScoreProperty().asString("High Score: %d"));

        Label timeLabel = new Label();
        timeLabel.textProperty().bind(state.secondsLeftProperty().asString("Time: %ds"));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        getStyleClass().add("hud");
        setAlignment(Pos.CENTER_LEFT);
        setSpacing(20);
        setPadding(new Insets(10, 16, 10, 16));
        getChildren().addAll(scoreLabel, livesLabel, levelLabel, highScoreLabel, spacer, timeLabel);
    }
}
