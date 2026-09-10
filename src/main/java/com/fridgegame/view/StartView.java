package com.fridgegame.view;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * The front door: nothing runs until the player asks for it.
 *
 * <p>The game used to begin the moment the window opened, which meant the level-1 clock
 * was already running while the player was still working out what they were looking at.
 */
public class StartView extends StackPane {

    public StartView(Runnable onPlay, Runnable onAbout) {
        Label title = new Label("DISTRACTIONS GAME");
        title.getStyleClass().addAll("overlay-title", "start-title");

        Label subtitle = new Label("Sort the fridge. Play the Tetris. Ignore the rival.");
        subtitle.getStyleClass().add("overlay-subtitle");

        Button playButton = new Button("PLAY");
        playButton.getStyleClass().addAll("pixel-button", "pixel-button-play");
        playButton.setOnAction(e -> onPlay.run());

        Button infoButton = new Button("i");
        infoButton.getStyleClass().addAll("pixel-button", "pixel-icon-button");
        infoButton.setTooltip(new Tooltip("About this game"));
        infoButton.setOnAction(e -> onAbout.run());

        Label hint = new Label("Esc or P pauses once you are in.");
        hint.getStyleClass().add("overlay-hint");

        VBox card = new VBox(22, title, subtitle, playButton, infoButton, hint);
        card.getStyleClass().add("overlay-card");
        card.setAlignment(Pos.CENTER);
        card.setMaxSize(USE_PREF_SIZE, USE_PREF_SIZE);

        getStyleClass().add("overlay-backdrop");
        getChildren().add(card);

        ViewTransitions.fadeScaleIn(card);
    }
}
