package com.fridgegame.view;

import com.fridgegame.llm.LlmCommentator;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * The rival's caption, pinned to the bottom-right corner of the combined level.
 *
 * <p>Deliberately small and out of the way. It is glanceable context, not something the
 * player stops to read, so it never takes space from the board or the fridge — it floats
 * over them.
 *
 * <p><b>Must stay mouse-transparent.</b> The card overlaps the Freezer zone, which is a
 * live drag-and-drop target; a card that intercepted events would silently swallow drops
 * in the bottom-right corner. The owner sets that on the node.
 */
public class CommentaryView extends VBox {

    private static final double MAX_WIDTH = 260;

    private final Label line = new Label();

    public CommentaryView() {
        Label name = new Label(LlmCommentator.RIVAL_NAME);
        name.getStyleClass().add("commentary-name");

        line.getStyleClass().add("commentary-line");
        line.setWrapText(true);
        line.setMaxWidth(MAX_WIDTH);

        getStyleClass().add("commentary");
        setSpacing(4);
        // USE_PREF_SIZE, not just a max width: a StackPane stretches a child to the whole
        // cell unless its maximum says otherwise, and Region's default maximum is
        // unbounded. Without this the card grows to the full height of the window and
        // blacks out the fridge. The label's own max width is what wraps the text.
        setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        getChildren().addAll(name, line);
        // Nothing to say yet: hidden rather than an empty card.
        setVisible(false);
    }

    /** Shows {@code text}, replacing whatever was there. */
    public void show(String text) {
        line.setText(text);
        setVisible(true);
        ViewTransitions.fadeScaleIn(this);
    }

    /** Hides the card — used between levels and on the levels that have no rival. */
    public void clear() {
        line.setText("");
        setVisible(false);
    }
}
