package com.fridgegame.view;

import com.fridgegame.model.GameState;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

public class LevelCompleteView extends StackPane {

    public LevelCompleteView(GameState state, int timeBonus, Runnable onNext) {
        Label emoji = new Label("🎊");
        emoji.getStyleClass().add("level-complete-emoji");

        Label title = new Label("Level " + state.getLevel() + " Complete!");
        title.getStyleClass().add("level-complete-title");

        Label bonusBadge = new Label("⏱ Time Bonus +" + timeBonus);
        bonusBadge.getStyleClass().add("time-bonus-badge");

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

        VBox card = new VBox(16, emoji, title, bonusBadge, scoreBlock, nextButton);
        card.getStyleClass().add("level-complete-card");
        card.setAlignment(Pos.CENTER);

        getStyleClass().add("level-complete-backdrop");
        getChildren().add(card);

        ViewTransitions.fadeScaleIn(card);
    }
}
