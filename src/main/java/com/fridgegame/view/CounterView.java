package com.fridgegame.view;

import com.fridgegame.model.GroceryItem;
import java.util.List;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class CounterView extends VBox {

    private final FlowPane body = new FlowPane();

    public CounterView(List<GroceryItem> items) {
        Label header = new Label("Counter");
        header.getStyleClass().add("counter-header");

        body.getStyleClass().add("counter-body");
        body.setHgap(10);
        body.setVgap(10);
        for (GroceryItem item : items) {
            body.getChildren().add(new GroceryNode(item));
        }

        ScrollPane scrollPane = new ScrollPane(body);
        scrollPane.setFitToWidth(true);
        scrollPane.getStyleClass().add("counter-scroll");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        getStyleClass().add("counter");
        getChildren().addAll(header, scrollPane);
    }

    public FlowPane getBody() {
        return body;
    }
}
