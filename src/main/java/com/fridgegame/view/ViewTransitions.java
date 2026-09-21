package com.fridgegame.view;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.scene.Node;
import javafx.util.Duration;

/** Shared animations: entrances for end-of-round overlay cards, and the memory card flip. */
final class ViewTransitions {

    private ViewTransitions() {
    }

    /**
     * Turns a card over: squash to nothing on the X axis, swap the face, open out again.
     *
     * <p>Two half-length scales rather than one animation, because the face has to change at
     * the moment the card is edge-on. Swapping it before or after means the player watches the
     * new face shrink away, or the old face grow back — either way the card reads as flipping
     * to what it already showed.
     *
     * @param atMidpoint swaps the visible face; runs edge-on, exactly once
     */
    static void flip(Node node, Runnable atMidpoint) {
        ScaleTransition close = new ScaleTransition(Duration.millis(110), node);
        close.setFromX(1);
        close.setToX(0);
        close.setInterpolator(Interpolator.EASE_IN);

        ScaleTransition open = new ScaleTransition(Duration.millis(110), node);
        open.setFromX(0);
        open.setToX(1);
        open.setInterpolator(Interpolator.EASE_OUT);

        close.setOnFinished(e -> {
            atMidpoint.run();
            open.play();
        });
        close.play();
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
