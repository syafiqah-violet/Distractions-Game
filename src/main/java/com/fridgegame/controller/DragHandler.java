package com.fridgegame.controller;

import com.fridgegame.data.ItemCatalog;
import com.fridgegame.model.GroceryItem;
import com.fridgegame.view.FridgeView;
import com.fridgegame.view.GroceryNode;
import com.fridgegame.view.ZoneNode;
import javafx.animation.KeyFrame;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.scene.Node;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.Pane;
import javafx.util.Duration;

/**
 * Wires the Dragboard API onto grocery and zone nodes.
 *
 * <p>Split in two because groceries no longer all exist at level start: zones are wired
 * once per level via {@link #wireZones}, while each item earned from a cleared row is
 * made draggable on arrival via {@link #makeDraggable}.
 */
public final class DragHandler {

    private DragHandler() {
    }

    /** Installs the drop targets for a level's fridge. Call once per level. */
    public static void wireZones(FridgeView fridge, GameController controller) {
        for (ZoneNode zone : fridge.getZones()) {
            installDropTarget(zone, controller);
        }
    }

    /**
     * Makes one counter item draggable.
     *
     * <p>{@code tetris} is notified for the duration of the gesture so gravity can pause —
     * JavaFX runs a nested event loop during a drag and would otherwise leave the player
     * unable to steer a falling piece.
     *
     * <p>{@code controller} is consulted only to refuse gestures while the game is paused.
     */
    public static void makeDraggable(
            GroceryNode node, TetrisController tetris, GameController controller) {
        node.setOnDragDetected(e -> {
            if (controller.isPaused()) {
                e.consume();
                return;
            }
            // A node that has just been sorted is removed from the counter in onDragDone,
            // but it keeps this handler and JavaFX can still fire drag-detected at it.
            // startDragAndDrop then throws IllegalStateException on the FX thread and the
            // gesture is lost. Reachable by hand on the sorting level, where four items
            // sit on the counter at once and a fast player drags from the same spot twice
            // in a row as the tiles reflow under the cursor.
            if (node.getScene() == null || node.getParent() == null) {
                e.consume();
                return;
            }
            Dragboard db = node.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent content = new ClipboardContent();
            content.putString(node.getItem().id());
            db.setContent(content);
            db.setDragView(node.snapshot(null, null));
            node.setOpacity(0.3);
            tetris.setDraggingGrocery(true);
            e.consume();
        });

        node.setOnDragDone(e -> {
            node.setOpacity(1.0);
            tetris.setDraggingGrocery(false);
            // Parent-checked for the same reason as above: this must not throw on the FX
            // thread if the node has already left the counter.
            if (e.getTransferMode() == TransferMode.MOVE
                    && node.getParent() instanceof Pane parent) {
                parent.getChildren().remove(node);
            }
            e.consume();
        });
    }

    private static void installDropTarget(ZoneNode zone, GameController controller) {
        zone.setOnDragOver(e -> {
            // Refusing the transfer mode while paused shows the "no drop" cursor and stops
            // onDragDropped firing at all. Letting the drop through to a paused
            // handleDrop would return false and flash the zone red, which reads as a
            // mis-sort rather than as a paused game.
            if (e.getGestureSource() != zone
                    && e.getDragboard().hasString()
                    && !controller.isPaused()) {
                e.acceptTransferModes(TransferMode.MOVE);
            }
            e.consume();
        });

        zone.setOnDragEntered(e -> zone.getStyleClass().add("zone-hover"));
        zone.setOnDragExited(e -> zone.getStyleClass().remove("zone-hover"));

        zone.setOnDragDropped(e -> {
            Dragboard db = e.getDragboard();
            boolean success = false;
            if (db.hasString() && !controller.isPaused()) {
                GroceryItem item = ItemCatalog.byId(db.getString());
                success = controller.handleDrop(item, zone.getZone());
                if (success) {
                    GroceryNode placed = new GroceryNode(item);
                    zone.getBody().getChildren().add(placed);
                    pulseCorrect(placed);
                } else {
                    flashWrong(zone);
                }
            }
            e.setDropCompleted(success);
            e.consume();
        });
    }

    private static void pulseCorrect(Node node) {
        ScaleTransition pulse = new ScaleTransition(Duration.millis(200), node);
        pulse.setFromX(0.5);
        pulse.setFromY(0.5);
        pulse.setToX(1.0);
        pulse.setToY(1.0);
        pulse.play();
    }

    private static void flashWrong(ZoneNode zone) {
        Timeline flash = new Timeline(
                new KeyFrame(Duration.ZERO, e -> zone.getStyleClass().add("zone-wrong-flash")),
                new KeyFrame(Duration.millis(300), e -> zone.getStyleClass().remove("zone-wrong-flash")));
        flash.play();
    }
}
