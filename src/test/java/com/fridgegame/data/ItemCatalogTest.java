package com.fridgegame.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fridgegame.model.FoodCategory;
import com.fridgegame.model.GroceryItem;
import com.fridgegame.model.Level;
import com.fridgegame.model.LevelMode;
import com.fridgegame.model.StorageZone;
import java.util.HashSet;
import java.util.List;
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
    void everyLevelHasAPositiveTimeLimitAndConsecutiveNumbering() {
        assertEquals(3, ItemCatalog.LEVELS.size());
        for (int i = 0; i < ItemCatalog.LEVELS.size(); i++) {
            Level level = ItemCatalog.LEVELS.get(i);
            assertEquals(i + 1, level.number(), "levels must be numbered in order");
            assertTrue(level.timeLimitSeconds() > 0,
                    "Level " + level.number() + " has no time limit");
        }
    }

    @Test
    void levelOneIsSortingPracticeWithTheShoppingAlreadyDone() {
        Level level = ItemCatalog.LEVELS.get(0);

        assertEquals(LevelMode.SORT_ONLY, level.mode());
        assertTrue(level.items().size() > 0, "there is nothing to practise sorting otherwise");
        assertEquals(60, level.timeLimitSeconds());
    }

    @Test
    void levelTwoIsTetrisPracticeWithNothingToSortAndOneRowToSurvive() {
        Level level = ItemCatalog.LEVELS.get(1);

        assertEquals(LevelMode.TETRIS_ONLY, level.mode());
        assertTrue(level.items().isEmpty(), "no groceries exist on the Tetris-only level");
        assertEquals(1, level.requiredRows(), "parking pieces in a corner must not pass");
        assertTrue(level.gravityMillis() > 0);
        assertEquals(60, level.timeLimitSeconds());
    }

    @Test
    void levelThreeCombinesBothAndGetsTheLongClock() {
        Level level = ItemCatalog.LEVELS.get(2);

        assertEquals(LevelMode.COMBINED, level.mode());
        assertTrue(level.items().size() > 0);
        assertTrue(level.gravityMillis() > 0);
        assertEquals(180, level.timeLimitSeconds(),
                "doing two things at once needs more than a minute");
    }

    @Test
    void pickHonoursTheRequestedCount() {
        for (int count = 1; count <= ItemCatalog.ALL_ITEMS.size(); count++) {
            assertEquals(count, ItemCatalog.pick(count, List.of(), 7L).size());
        }
    }

    @Test
    void pickClampsCountsOutsideTheCatalog() {
        assertEquals(1, ItemCatalog.pick(0, List.of(), 7L).size());
        assertEquals(1, ItemCatalog.pick(-5, List.of(), 7L).size());
        assertEquals(ItemCatalog.ALL_ITEMS.size(), ItemCatalog.pick(999, List.of(), 7L).size());
    }

    @Test
    void pickIsDeterministicForASeed() {
        assertEquals(
                ItemCatalog.pick(5, List.of(FoodCategory.DAIRY), 42L),
                ItemCatalog.pick(5, List.of(FoodCategory.DAIRY), 42L));
    }

    @Test
    void pickFavoursTheEmphasisedCategories() {
        List<GroceryItem> picked = ItemCatalog.pick(3, List.of(FoodCategory.FROZEN), 3L);

        long frozen = picked.stream().filter(i -> i.category() == FoodCategory.FROZEN).count();
        assertEquals(2, frozen, "both frozen items should be drawn before anything else");
    }

    @Test
    void pickNeverReturnsASingleCategoryQuota() {
        // Only two FROZEN items exist, so asking for two of them would otherwise produce a
        // quota with exactly one valid zone — which is not a sorting problem at all.
        for (long seed = 0; seed < 20; seed++) {
            List<GroceryItem> picked = ItemCatalog.pick(2, List.of(FoodCategory.FROZEN), seed);
            long categories = picked.stream().map(GroceryItem::category).distinct().count();
            assertTrue(categories > 1, "seed " + seed + " produced a one-category quota");
        }
    }

    @Test
    void pickTakesNoEmphasisWithoutComplaint() {
        assertEquals(4, ItemCatalog.pick(4, null, 1L).size());
        assertEquals(4, ItemCatalog.pick(4, List.of(), 1L).size());
    }
}
