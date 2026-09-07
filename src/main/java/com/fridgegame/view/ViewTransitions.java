package com.fridgegame.view;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.scene.Node;
import javafx.util.Duration;

/** Shared entrance animation for end-of-round overlay cards (phase 6 polish). */
final class ViewTransitions {

    private ViewTransitions() {
    }

    static void fadeScaleIn(Node node) {
        node.setOpacity(0);
        node.setScaleX(0.85);
        node.setScaleY(0.85);

        FadeTransition fade = new FadeTransition(Duration.millis(380), node);
        fade.setToValue(1);

        ScaleTransition scale = new ScaleTransition(Duration.millis(380), node);
        scale.setToX(1);
        scale.setToY(1);
        scale.setInterpolator(Interpolator.EASE_OUT);

        new ParallelTransition(fade, scale).play();
    }
}
