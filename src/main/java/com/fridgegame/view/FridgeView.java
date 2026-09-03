package com.fridgegame.view;

import com.fridgegame.model.StorageZone;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class FridgeView extends VBox {

    public FridgeView() {
        getStyleClass().add("fridge");
        setSpacing(10);

        for (StorageZone zone : StorageZone.values()) {
            ZoneNode zoneNode = new ZoneNode(zone);
            VBox.setVgrow(zoneNode, Priority.ALWAYS);
            getChildren().add(zoneNode);
        }
    }
}
