package com.fridgegame.view;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/** The rules, reachable from the start screen and nowhere else. */
public class AboutView extends StackPane {

    public AboutView(Runnable onPlay) {
        Label title = new Label("ABOUT THIS GAME");
        title.getStyleClass().addAll("overlay-title", "about-title");

        Button playButton = new Button("Play");
        playButton.getStyleClass().addAll("primary-button", "large-action-button");
        playButton.setOnAction(e -> onPlay.run());
        // The card is a left-aligned VBox, so Play only reaches the right edge by sitting
        // in a row that stretches the full width. A bare Button would stay on the left.
        HBox playRow = new HBox(playButton);
        playRow.setAlignment(Pos.CENTER_RIGHT);

        VBox card = new VBox(10,
                title,
                body("Three levels, each one asking for something different. A wrong drop "
                        + "and a top-out each cost a life; lose all three and the run ends."),
                heading("Level 1 — Sort only, 60s"),
                body("The shopping is already on the counter. Drag all four items into the "
                        + "zone that accepts them before the clock runs out."),
                heading("Level 2 — Tetris only, 60s"),
                body("Nothing to sort. Survive the minute having cleared at least one row. "
                        + "Rows pay 20 / 60 / 150 / 400 for one to four at once, so stacking "
                        + "for a big clear is worth the risk."),
                heading("Level 3 — Both, 3 minutes"),
                body("Every row you clear buys one grocery onto the counter. Sort all six to "
                        + "win. This is the only level the rival shows up for."),
                heading("Scoring"),
                body("A correct drop pays 10, rising with your streak. A wrong one costs 5 "
                        + "and a life. Finishing a sorting level early pays 2 per second left."),
                heading("Controls"),
                body("Arrows or WASD to move and rotate, Space to hard-drop, mouse to drag "
                        + "groceries. Esc or P pauses, on every level."),
                heading("The rival"),
                body("With a local LLM configured, it heckles you on level 3 and retunes the "
                        + "difficulty between levels. Without one the game plays exactly the "
                        + "same — you just get silence and the authored difficulty."),
                playRow);

        card.getStyleClass().add("overlay-card");
        card.setAlignment(Pos.TOP_LEFT);
        card.setMaxSize(USE_PREF_SIZE, USE_PREF_SIZE);

        getStyleClass().add("overlay-backdrop");
        getChildren().add(card);

        ViewTransitions.fadeScaleIn(card);
    }

    private static Label heading(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("about-heading");
        return label;
    }

    private static Label body(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("about-body");
        label.setWrapText(true);
        label.setMaxWidth(520);
        return label;
    }
}
