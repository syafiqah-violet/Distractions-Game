package com.fridgegame.view;

import com.fridgegame.model.GroceryItem;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;

public class GroceryNode extends VBox {

    private final GroceryItem item;

    public GroceryNode(GroceryItem item) {
        this.item = item;

        ImageView icon = new ImageView(new Image(getClass().getResourceAsStream(item.iconPath())));
        icon.setFitWidth(50);
        icon.setFitHeight(50);
        icon.setPreserveRatio(true);
        icon.setSmooth(false);
        icon.getStyleClass().add("grocery-icon");

        Label name = new Label(item.name());
        name.getStyleClass().add("grocery-name");

        getStyleClass().add("grocery-node");
        setAlignment(Pos.CENTER);
        setPrefSize(80, 90);
        setMinSize(80, 90);
        getChildren().addAll(icon, name);
    }

    public GroceryItem getItem() {
        return item;
    }
}
