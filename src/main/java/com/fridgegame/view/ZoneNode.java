package com.fridgegame.view;

import com.fridgegame.model.StorageZone;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class ZoneNode extends VBox {

    private final StorageZone zone;
    private final FlowPane body = new FlowPane();

    public ZoneNode(StorageZone zone) {
        this.zone = zone;

        Label header = new Label(zone.label());
        header.getStyleClass().add("zone-header");

        body.getStyleClass().add("zone-body");
        body.setHgap(8);
        body.setVgap(8);
        VBox.setVgrow(body, Priority.ALWAYS);

        getStyleClass().addAll("zone", styleClassFor(zone));
        setPickOnBounds(true);
        getChildren().addAll(header, body);
    }

    public StorageZone getZone() {
        return zone;
    }

    public FlowPane getBody() {
        return body;
    }

    private static String styleClassFor(StorageZone zone) {
        return "zone-" + zone.name().toLowerCase().replace('_', '-');
    }
}
