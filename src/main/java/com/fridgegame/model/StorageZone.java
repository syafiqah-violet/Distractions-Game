package com.fridgegame.model;

import java.util.EnumSet;
import java.util.Set;

public enum StorageZone {
    DOOR("Door", EnumSet.of(FoodCategory.DRINKS)),
    TOP_SHELF("Top Shelf", EnumSet.of(FoodCategory.DAIRY)),
    MID_SHELF("Mid Shelf", EnumSet.of(FoodCategory.MEAT)),
    CRISPER("Crisper", EnumSet.of(FoodCategory.PRODUCE)),
    FREEZER("Freezer", EnumSet.of(FoodCategory.FROZEN));

    private final String label;
    private final Set<FoodCategory> accepted;

    StorageZone(String label, Set<FoodCategory> accepted) {
        this.label = label;
        this.accepted = accepted;
    }

    public String label() {
        return label;
    }

    public boolean accepts(GroceryItem item) {
        return accepted.contains(item.category());
    }
}
