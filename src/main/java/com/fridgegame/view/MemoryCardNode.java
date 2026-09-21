package com.fridgegame.view;

import com.fridgegame.model.GroceryItem;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * One card in the mix-and-match grid: a grocery on one side, a blank back on the other.
 *
 * <p>Both faces are built once and stacked, with visibility deciding which one shows. The
 * alternative — building the face on demand — would decode the image mid-flip, which is a
 * stutter on the one frame the player is watching most closely.
 *
 * <p>The node holds no rules. It renders whatever state it is handed, so the board can be
 * re-rendered from scratch at any time without the display and the model disagreeing.
 */
class MemoryCardNode extends StackPane {

    static final double CARD_WIDTH = 104;
    static final double CARD_HEIGHT = 124;

    private final GroceryItem item;
    private final VBox face;
    private final Label back;

    private boolean faceUp;
    private boolean matched;

    MemoryCardNode(GroceryItem item) {
        this.item = item;

        ImageView icon = new ImageView(new Image(getClass().getResourceAsStream(item.iconPath())));
        icon.setFitWidth(56);
        icon.setFitHeight(56);
        icon.setPreserveRatio(true);
        // Same reason as GroceryNode: smoothing turns pixel art into mush.
        icon.setSmooth(false);
        icon.getStyleClass().add("grocery-icon");

        Label name = new Label(item.name());
        name.getStyleClass().add("memory-card-name");

        face = new VBox(6, icon, name);
        face.setAlignment(Pos.CENTER);
        face.getStyleClass().add("memory-card-face");

        back = new Label("?");
        back.getStyleClass().add("memory-card-back");

        getStyleClass().add("memory-card");
        setPrefSize(CARD_WIDTH, CARD_HEIGHT);
        setMinSize(CARD_WIDTH, CARD_HEIGHT);
        setMaxSize(CARD_WIDTH, CARD_HEIGHT);
        getChildren().addAll(back, face);
        applySides();
    }

    GroceryItem getItem() {
        return item;
    }

    /**
     * Shows the requested side, flipping only if that is a change.
     *
     * <p>The guard is what lets {@link MemoryBoardView} repaint the whole grid on every board
     * change: cards that did not move are left alone instead of re-flipping onto the side
     * they were already showing.
     */
    void setFaceUp(boolean value) {
        if (faceUp == value) {
            return;
        }
        faceUp = value;
        ViewTransitions.flip(this, this::applySides);
    }

    /** Marks the card as settled for the rest of the level; purely cosmetic. */
    void setMatched(boolean value) {
        if (matched == value) {
            return;
        }
        matched = value;
        getStyleClass().removeAll("memory-card-matched");
        if (value) {
            getStyleClass().add("memory-card-matched");
        }
    }

    private void applySides() {
        face.setVisible(faceUp);
        back.setVisible(!faceUp);
    }
}
