package com.fridgegame.view;

import com.fridgegame.model.GameState;
import javafx.animation.Animation;
import javafx.animation.ScaleTransition;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

public class GameOverView extends StackPane {

    public GameOverView(GameState state, boolean won, boolean isNewHighScore, int highScore, Runnable onRestart) {

        Label title = new Label(won ? "You cleared every level!" : "Game Over");
        title.getStyleClass().add("overlay-title");
        if (won) {
            title.getStyleClass().add("game-over-title-win");
        }

        Label scoreCaption = new Label("FINAL SCORE");
        scoreCaption.getStyleClass().add("stat-label");

        Label scoreValue = new Label();
        scoreValue.textProperty().bind(state.scoreProperty().asString());
        scoreValue.getStyleClass().add("stat-value");

        VBox scoreBlock = new VBox(2, scoreCaption, scoreValue);
        scoreBlock.setAlignment(Pos.CENTER);

        Label highScoreLabel = new Label("High Score: " + highScore);
        highScoreLabel.getStyleClass().add("game-over-highscore");

        Button restartButton = new Button("Play Again");
        restartButton.getStyleClass().addAll("primary-button", "large-action-button");
        restartButton.setOnAction(e -> onRestart.run());

        VBox card = new VBox(16, title, scoreBlock);
        card.getStyleClass().add("overlay-card");
        card.setAlignment(Pos.CENTER);
        card.setMaxSize(USE_PREF_SIZE, USE_PREF_SIZE);

        if (!won) {
            Label levelReached = new Label("You made it to Level " + state.getLevel());
            levelReached.getStyleClass().add("overlay-subtitle");
            card.getChildren().add(1, levelReached);
        }

        if (isNewHighScore) {
            Label badge = new Label("⭐ New High Score!");
            badge.getStyleClass().add("high-score-badge");
            card.getChildren().add(badge);
            pulse(badge);
        } else {
            card.getChildren().add(highScoreLabel);
        }
        card.getChildren().add(restartButton);

        getStyleClass().add("overlay-backdrop");
        if (won) {
            getStyleClass().add("overlay-backdrop-win");
        }
        getChildren().add(card);

        ViewTransitions.fadeScaleIn(card);
    }

    private void pulse(Label badge) {
        ScaleTransition pulse = new ScaleTransition(Duration.millis(650), badge);
        pulse.setFromX(1.0);
        pulse.setFromY(1.0);
        pulse.setToX(1.08);
        pulse.setToY(1.08);
        pulse.setAutoReverse(true);
        pulse.setCycleCount(Animation.INDEFINITE);
        pulse.play();
    }
}
