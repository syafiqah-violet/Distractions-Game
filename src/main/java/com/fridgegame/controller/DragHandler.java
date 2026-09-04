package com.fridgegame.controller;

import com.fridgegame.data.ItemCatalog;
import com.fridgegame.model.GroceryItem;
import com.fridgegame.view.CounterView;
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

/** Wires the Dragboard API onto grocery/zone nodes. No rules or scoring yet (Phase 4). */
public final class DragHandler {

    private DragHandler() {
    }

    public static void wire(FridgeView fridge, CounterView counter, GameController controller) {
        for (Node child : counter.getBody().getChildren()) {
            if (child instanceof GroceryNode node) {
                installDragSource(node);
            }
        }
        for (ZoneNode zone : fridge.getZones()) {
            installDropTarget(zone, controller);
        }
    }

    private static void installDragSource(GroceryNode node) {
        node.setOnDragDetected(e -> {
            Dragboard db = node.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent content = new ClipboardContent();
            content.putString(node.getItem().id());
            db.setContent(content);
            db.setDragView(node.snapshot(null, null));
            node.setOpacity(0.3);
            e.consume();
        });

        node.setOnDragDone(e -> {
            node.setOpacity(1.0);
            if (e.getTransferMode() == TransferMode.MOVE) {
                ((Pane) node.getParent()).getChildren().remove(node);
            }
            e.consume();
        });
    }

    private static void installDropTarget(ZoneNode zone, GameController controller) {
        zone.setOnDragOver(e -> {
            if (e.getGestureSource() != zone && e.getDragboard().hasString()) {
                e.acceptTransferModes(TransferMode.MOVE);
            }
            e.consume();
        });

        zone.setOnDragEntered(e -> zone.getStyleClass().add("zone-hover"));
        zone.setOnDragExited(e -> zone.getStyleClass().remove("zone-hover"));

        zone.setOnDragDropped(e -> {
            Dragboard db = e.getDragboard();
            boolean success = false;
            if (db.hasString()) {
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
