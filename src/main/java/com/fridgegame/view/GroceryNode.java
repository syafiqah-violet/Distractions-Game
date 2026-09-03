package com.fridgegame.view;

import com.fridgegame.model.GroceryItem;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

public class GroceryNode extends VBox {

    private final GroceryItem item;

    public GroceryNode(GroceryItem item) {
        this.item = item;

        Label emoji = new Label(item.emoji());
        emoji.getStyleClass().add("grocery-emoji");

        Label name = new Label(item.name());
        name.getStyleClass().add("grocery-name");

        getStyleClass().add("grocery-node");
        setAlignment(Pos.CENTER);
        setPrefSize(80, 90);
        setMinSize(80, 90);
        getChildren().addAll(emoji, name);
    }

    public GroceryItem getItem() {
        return item;
    }
}
