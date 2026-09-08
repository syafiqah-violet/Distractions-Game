package com.fridgegame.view;

import com.fridgegame.model.GroceryItem;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Holds the groceries earned but not yet sorted.
 *
 * <p>Starts empty: items arrive one per cleared row via {@link #addItem}, so the counter
 * is the visible link between the Tetris half of the screen and the fridge half.
 */
public class CounterView extends VBox {

    private final FlowPane body = new FlowPane();
    private final Label placeholder = new Label("Clear rows to\nearn groceries");

    public CounterView() {
        Label header = new Label("Counter");
        header.getStyleClass().add("counter-header");

        body.getStyleClass().add("counter-body");
        body.setHgap(10);
        body.setVgap(10);

        placeholder.getStyleClass().add("counter-placeholder");
        placeholder.setWrapText(true);

        VBox stack = new VBox(body, placeholder);
        ScrollPane scrollPane = new ScrollPane(stack);
        scrollPane.setFitToWidth(true);
        scrollPane.getStyleClass().add("counter-scroll");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        // The hint only makes sense while there is nothing to drag.
        body.getChildren().addListener((javafx.collections.ListChangeListener<javafx.scene.Node>)
                change -> {
                    boolean empty = body.getChildren().isEmpty();
                    placeholder.setVisible(empty);
                    placeholder.setManaged(empty);
                });

        getStyleClass().add("counter");
        getChildren().addAll(header, scrollPane);
    }

    /**
     * Puts one earned item on the counter and returns its node so the caller can
     * make it draggable.
     */
    public GroceryNode addItem(GroceryItem item) {
        GroceryNode node = new GroceryNode(item);
        body.getChildren().add(node);
        ViewTransitions.fadeScaleIn(node);
        return node;
    }
}
