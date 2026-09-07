package com.fridgegame.view;

import com.fridgegame.model.GameState;
import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

public class GameOverView extends StackPane {

    public GameOverView(GameState state, boolean won, boolean isNewHighScore, int highScore, Runnable onRestart) {
        Label emoji = new Label(won ? "🎉" : "🥶");
        emoji.getStyleClass().add("game-over-emoji");

        Label title = new Label(won ? "You cleared every level!" : "Game Over");
        title.getStyleClass().add("game-over-title");
        if (won) {
            title.getStyleClass().add("game-over-title-win");
        }

        Label scoreCaption = new Label("FINAL SCORE");
        scoreCaption.getStyleClass().add("game-over-score-label");

        Label scoreValue = new Label();
        scoreValue.textProperty().bind(state.scoreProperty().asString());
        scoreValue.getStyleClass().add("game-over-score-value");

        VBox scoreBlock = new VBox(2, scoreCaption, scoreValue);
        scoreBlock.setAlignment(Pos.CENTER);

        Label highScoreLabel = new Label("High Score: " + highScore);
        highScoreLabel.getStyleClass().add("game-over-highscore");

        Button restartButton = new Button("Play Again");
        restartButton.getStyleClass().addAll("primary-button", "game-over-button");
        restartButton.setOnAction(e -> onRestart.run());

        VBox card = new VBox(16, emoji, title, scoreBlock);
        card.getStyleClass().add("game-over-card");
        card.setAlignment(Pos.CENTER);

        if (!won) {
            Label levelReached = new Label("You made it to Level " + state.getLevel());
            levelReached.getStyleClass().add("game-over-subtitle");
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

        getStyleClass().add("game-over-backdrop");
        if (won) {
            getStyleClass().add("game-over-backdrop-win");
        }
        getChildren().add(card);

        animateEntrance(card);
    }

    private void animateEntrance(VBox card) {
        card.setOpacity(0);
        card.setScaleX(0.85);
        card.setScaleY(0.85);

        FadeTransition fade = new FadeTransition(Duration.millis(380), card);
        fade.setToValue(1);

        ScaleTransition scale = new ScaleTransition(Duration.millis(380), card);
        scale.setToX(1);
        scale.setToY(1);
        scale.setInterpolator(Interpolator.EASE_OUT);

        new ParallelTransition(fade, scale).play();
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
