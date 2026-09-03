package com.fridgegame.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fridgegame.model.GroceryItem;
import com.fridgegame.model.Level;
import com.fridgegame.model.StorageZone;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ItemCatalogTest {

    @Test
    void hasFifteenItemsWithUniqueIds() {
        assertEquals(15, ItemCatalog.ALL_ITEMS.size());

        Set<String> ids = new HashSet<>();
        for (GroceryItem item : ItemCatalog.ALL_ITEMS) {
            assertTrue(ids.add(item.id()), "Duplicate id: " + item.id());
        }
    }

    @Test
    void byIdResolvesEveryCatalogItem() {
        for (GroceryItem item : ItemCatalog.ALL_ITEMS) {
            assertEquals(item, ItemCatalog.byId(item.id()));
        }
    }

    @Test
    void byIdThrowsForUnknownId() {
        assertThrows(IllegalArgumentException.class, () -> ItemCatalog.byId("does-not-exist"));
    }

    @Test
    void everyItemHasAZoneThatAcceptsIt() {
        for (GroceryItem item : ItemCatalog.ALL_ITEMS) {
            boolean someZoneAccepts = false;
            for (StorageZone zone : StorageZone.values()) {
                if (zone.accepts(item)) {
                    someZoneAccepts = true;
                    break;
                }
            }
            assertTrue(someZoneAccepts, "No zone accepts " + item.name());
        }
    }

    @Test
    void levelsHaveItemsAndPositiveTimeLimits() {
        assertEquals(3, ItemCatalog.LEVELS.size());
        for (Level level : ItemCatalog.LEVELS) {
            assertTrue(level.items().size() > 0, "Level " + level.number() + " has no items");
            assertTrue(level.timeLimitSeconds() > 0, "Level " + level.number() + " has no time limit");
        }
    }
}
