package com.fridgegame.view;

import com.fridgegame.data.HighScoreStore;
import com.fridgegame.model.GameState;
import com.fridgegame.model.LevelMode;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

public class HudView extends HBox {

    private final Label rowsLabel;
    private final Label itemsLeftLabel;
    private final Label pairsLeftLabel;
    private final Button pauseButton = new Button("⏸ Pause");

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

        // The same counter, read the other way: on the memory level an "item" is a pair of
        // cards. One property, two labels, because "To sort: 6" over a grid with nothing to
        // sort names the wrong goal.
        pairsLeftLabel = stat();
        pairsLeftLabel.getStyleClass().add("hud-quota");
        pairsLeftLabel.textProperty().bind(state.itemsLeftProperty().asString("Pairs left: %d"));

        Label highScoreLabel = stat();
        highScoreLabel.getStyleClass().add("hud-muted");
        highScoreLabel.textProperty().bind(highScoreStore.highScoreProperty().asString("Best: %d"));

        Label timeLabel = stat();
        timeLabel.getStyleClass().add("hud-time");
        timeLabel.textProperty().bind(state.secondsLeftProperty().asString("Time: %ds"));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        pauseButton.getStyleClass().add("hud-pause-button");
        // Otherwise it keeps focus after a click and sits in the middle of the HUD wearing
        // a focus ring, and Space would reach it instead of hard-dropping.
        pauseButton.setFocusTraversable(false);

        getStyleClass().add("hud");
        setAlignment(Pos.CENTER_LEFT);
        setSpacing(18);
        setPadding(new Insets(10, 16, 10, 16));
        getChildren().addAll(
                scoreLabel, livesLabel, levelLabel, rowsLabel, itemsLeftLabel, pairsLeftLabel,
                spacer, highScoreLabel, timeLabel, pauseButton);
    }

    public void setOnPauseToggle(Runnable listener) {
        pauseButton.setOnAction(e -> listener.run());
    }

    /**
     * Keeps the button's label honest about what it will do next.
     *
     * <p>The pause overlay covers the HUD, so in practice this button only ever pauses and
     * the overlay carries Resume. The label still has to be right for the instant before
     * the overlay paints, and for anyone who later makes the overlay smaller.
     */
    public void setPaused(boolean paused) {
        pauseButton.setText(paused ? "▶ Resume" : "⏸ Pause");
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
        show(pairsLeftLabel, mode.hasMemory());
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
