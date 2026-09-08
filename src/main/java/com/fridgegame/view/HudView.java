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
        Label scoreLabel = stat();
        scoreLabel.textProperty().bind(state.scoreProperty().asString("Score: %d"));

        Label livesLabel = stat();
        livesLabel.textProperty().bind(state.livesProperty().asString("Lives: %d"));

        Label levelLabel = stat();
        levelLabel.textProperty().bind(state.levelProperty().asString("Level: %d"));

        Label rowsLabel = stat();
        rowsLabel.getStyleClass().add("hud-rows");
        rowsLabel.textProperty().bind(state.rowsClearedProperty().asString("Rows: %d"));

        Label itemsLeftLabel = stat();
        itemsLeftLabel.getStyleClass().add("hud-quota");
        itemsLeftLabel.textProperty().bind(state.itemsLeftProperty().asString("To sort: %d"));

        Label highScoreLabel = stat();
        highScoreLabel.getStyleClass().add("hud-muted");
        highScoreLabel.textProperty().bind(highScoreStore.highScoreProperty().asString("Best: %d"));

        Label timeLabel = stat();
        timeLabel.getStyleClass().add("hud-time");
        timeLabel.textProperty().bind(state.secondsLeftProperty().asString("Time: %ds"));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        getStyleClass().add("hud");
        setAlignment(Pos.CENTER_LEFT);
        setSpacing(18);
        setPadding(new Insets(10, 16, 10, 16));
        getChildren().addAll(
                scoreLabel, livesLabel, levelLabel, rowsLabel, itemsLeftLabel,
                spacer, highScoreLabel, timeLabel);
    }

    private static Label stat() {
        Label label = new Label();
        label.getStyleClass().add("hud-stat");
        return label;
    }
}
