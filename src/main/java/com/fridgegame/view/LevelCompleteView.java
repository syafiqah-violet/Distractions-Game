package com.fridgegame.view;

import com.fridgegame.model.GameState;
import com.fridgegame.model.LevelMode;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

public class LevelCompleteView extends StackPane {

    public LevelCompleteView(GameState state, LevelMode mode, int timeBonus, Runnable onNext) {
        Label emoji = new Label("🎊");
        emoji.getStyleClass().add("level-complete-emoji");

        Label title = new Label("Level " + state.getLevel() + " Complete!");
        title.getStyleClass().add("level-complete-title");

        Label bonusBadge = new Label("⏱ Time Bonus +" + timeBonus);
        bonusBadge.getStyleClass().add("time-bonus-badge");

        Label summary = new Label(summarise(state, mode));
        summary.getStyleClass().add("game-over-subtitle");

        Label scoreCaption = new Label("SCORE");
        scoreCaption.getStyleClass().add("stat-label");

        Label scoreValue = new Label();
        scoreValue.textProperty().bind(state.scoreProperty().asString());
        scoreValue.getStyleClass().add("stat-value");

        VBox scoreBlock = new VBox(2, scoreCaption, scoreValue);
        scoreBlock.setAlignment(Pos.CENTER);

        Button nextButton = new Button("Next Level");
        nextButton.getStyleClass().addAll("primary-button", "large-action-button");
        nextButton.setOnAction(e -> onNext.run());

        VBox card = new VBox(16, emoji, title, summary, bonusBadge, scoreBlock, nextButton);
        card.getStyleClass().add("level-complete-card");
        card.setAlignment(Pos.CENTER);

        getStyleClass().add("level-complete-backdrop");
        getChildren().add(card);

        ViewTransitions.fadeScaleIn(card);
    }

    /**
     * One line about what the player actually did, which depends on what the level asked.
     *
     * <p>"0 rows cleared to earn your groceries" after a level with no Tetris board reads
     * as a failure the player does not understand, so each mode gets its own summary.
     */
    private static String summarise(GameState state, LevelMode mode) {
        int rows = state.getRowsCleared();
        return switch (mode) {
            case SORT_ONLY -> "Every grocery in the right place";
            case TETRIS_ONLY -> rows == 1
                    ? "1 row cleared"
                    : rows + " rows cleared";
            case COMBINED -> rows + (rows == 1 ? " row" : " rows")
                    + " cleared to earn your groceries";
        };
    }
}
