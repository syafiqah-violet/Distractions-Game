package com.fridgegame.view;

import com.fridgegame.model.StorageZone;
import java.util.ArrayList;
import java.util.List;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class FridgeView extends VBox {

    private final List<ZoneNode> zones = new ArrayList<>();

    public FridgeView() {
        getStyleClass().add("fridge");
        setSpacing(10);

        for (StorageZone zone : StorageZone.values()) {
            ZoneNode zoneNode = new ZoneNode(zone);
            zones.add(zoneNode);
            VBox.setVgrow(zoneNode, Priority.ALWAYS);
            getChildren().add(zoneNode);
        }
    }

    public List<ZoneNode> getZones() {
        return List.copyOf(zones);
    }
}
