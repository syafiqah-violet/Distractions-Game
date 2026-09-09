package com.fridgegame.view;

import com.fridgegame.data.HighScoreStore;
import com.fridgegame.model.GameState;
import com.fridgegame.model.LevelMode;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

public class HudView extends HBox {

    private final Label rowsLabel;
    private final Label itemsLeftLabel;

    public HudView(GameState state, HighScoreStore highScoreStore) {
        Label scoreLabel = stat();
        scoreLabel.textProperty().bind(state.scoreProperty().asString("Score: %d"));

        Label livesLabel = stat();
        livesLabel.textProperty().bind(state.livesProperty().asString("Lives: %d"));

        Label levelLabel = stat();
        levelLabel.textProperty().bind(state.levelProperty().asString("Level: %d"));

        rowsLabel = stat();
        rowsLabel.getStyleClass().add("hud-rows");
        rowsLabel.textProperty().bind(state.rowsClearedProperty().asString("Rows: %d"));

        itemsLeftLabel = stat();
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

    /**
     * Shows only the stats the level can actually change.
     *
     * <p>"Rows: 0" on a level with no board, or "To sort: 0" on a level with nothing to
     * sort, reads as a goal the player is failing at rather than one that does not exist.
     * Toggling {@code managed} as well as {@code visible} closes the gap the hidden label
     * would otherwise leave in the {@link HBox}.
     */
    public void applyMode(LevelMode mode) {
        show(rowsLabel, mode.hasTetris());
        show(itemsLeftLabel, mode.hasSorting());
    }

    private static void show(Label label, boolean visible) {
        label.setVisible(visible);
        label.setManaged(visible);
    }

    private static Label stat() {
        Label label = new Label();
        label.getStyleClass().add("hud-stat");
        return label;
    }
}
