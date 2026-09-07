package com.fridgegame.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StorageZoneTest {

    private static final GroceryItem LETTUCE =
            new GroceryItem("lettuce", "Lettuce", "/com/fridgegame/images/food_pixel/lettuce.png", FoodCategory.PRODUCE);
    private static final GroceryItem MILK =
            new GroceryItem("milk", "Milk", "/com/fridgegame/images/food_pixel/milk.png", FoodCategory.DAIRY);

    @Test
    void crisperAcceptsProduceButNotDairy() {
        assertTrue(StorageZone.CRISPER.accepts(LETTUCE));
        assertFalse(StorageZone.CRISPER.accepts(MILK));
    }

    @Test
    void everyZoneAcceptsExactlyOneCategory() {
        Map<StorageZone, FoodCategory> expected = new EnumMap<>(StorageZone.class);
        expected.put(StorageZone.DOOR, FoodCategory.DRINKS);
        expected.put(StorageZone.TOP_SHELF, FoodCategory.DAIRY);
        expected.put(StorageZone.MID_SHELF, FoodCategory.MEAT);
        expected.put(StorageZone.CRISPER, FoodCategory.PRODUCE);
        expected.put(StorageZone.FREEZER, FoodCategory.FROZEN);

        for (StorageZone zone : StorageZone.values()) {
            FoodCategory matchingCategory = expected.get(zone);
            for (FoodCategory category : FoodCategory.values()) {
                GroceryItem probe = new GroceryItem("probe", "Probe", "/com/fridgegame/images/food_pixel/probe.png", category);
                boolean shouldAccept = category == matchingCategory;
                assertTrue(zone.accepts(probe) == shouldAccept,
                        zone + " accepting " + category + " should be " + shouldAccept);
            }
        }
    }
}
