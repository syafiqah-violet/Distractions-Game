package com.fridgegame.view;

import com.fridgegame.audio.Sfx;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * The paused screen, floating over a live game rather than replacing it.
 *
 * <p>Two things here are load-bearing rather than cosmetic:
 *
 * <ul>
 *   <li>The backdrop is <b>translucent</b>. Unlike {@link GameOverView}, which swaps the
 *       scene root and can afford to be opaque, this sits on top of a running level — and
 *       a pause that hides the clock you are not losing reads as a trap.</li>
 *   <li>The backdrop is <b>painted and pickable</b>, which is the exact inverse of
 *       {@link CommentaryView}'s {@code mouseTransparent}. Swallowing mouse input is this
 *       overlay's job: the fridge zones underneath are live drop targets, and a player who
 *       could still sort during a pause would be sorting against a frozen clock. A
 *       {@code Region} with no background is not a mouse target at all, so the fill in
 *       {@code .pause-backdrop} is what makes the block work.</li>
 * </ul>
 */
public class PauseOverlay extends StackPane {

    public PauseOverlay(Runnable onResume, Runnable onNewGame) {
        Label title = new Label("PAUSED");
        title.getStyleClass().add("overlay-title");

        Label hint = new Label("The clock is stopped. Esc or P also resumes.");
        hint.getStyleClass().add("overlay-subtitle");

        Button resumeButton = new Button("Resume");
        resumeButton.getStyleClass().addAll("primary-button", "large-action-button");
        Sfx.onAction(resumeButton, onResume);
        // Otherwise Space — the hard-drop key — activates it the moment it takes focus.
        resumeButton.setFocusTraversable(false);

        // Discards the run in progress, so it sits second: Resume is what a pause is for.
        Button newGameButton = new Button("New Game");
        newGameButton.getStyleClass().addAll("primary-button", "large-action-button");
        Sfx.onAction(newGameButton, onNewGame);
        newGameButton.setFocusTraversable(false);

        HBox actions = new HBox(14, resumeButton, newGameButton);
        actions.setAlignment(Pos.CENTER);

        VBox card = new VBox(16, title, hint, actions);
        card.getStyleClass().add("overlay-card");
        card.setAlignment(Pos.CENTER);
        card.setMaxSize(USE_PREF_SIZE, USE_PREF_SIZE);

        getStyleClass().add("pause-backdrop");
        setPickOnBounds(true);
        getChildren().add(card);

        setVisible(false);
        setManaged(false);
    }
}
