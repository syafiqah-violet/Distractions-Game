package com.fridgegame.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fridgegame.model.FoodCategory;
import com.fridgegame.model.GameState;
import com.fridgegame.model.GroceryItem;
import com.fridgegame.model.Level;
import com.fridgegame.model.LevelMode;
import com.fridgegame.model.StorageZone;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Level 1 hands you the shopping.
 *
 * <p>Every other mode earns groceries a row at a time, so releasing the whole quota up
 * front is the one place that rule is deliberately broken — a level with no Tetris board
 * would otherwise be unwinnable, since there is no way to unlock anything.
 */
class SortOnlyReleaseTest {

    private static final GroceryItem MILK =
            new GroceryItem("milk", "Milk", "/icons/milk.png", FoodCategory.DAIRY);
    private static final GroceryItem LETTUCE =
            new GroceryItem("lettuce", "Lettuce", "/icons/lettuce.png", FoodCategory.PRODUCE);
    private static final GroceryItem CHICKEN =
            new GroceryItem("chicken", "Chicken", "/icons/chicken.png", FoodCategory.MEAT);

    private final GameState state = new GameState();
    private final GameController controller = new GameController(state);
    private final List<GroceryItem> unlocked = new ArrayList<>();

    SortOnlyReleaseTest() {
        controller.setOnItemUnlocked(unlocked::add);
    }

    @Test
    void theWholeQuotaLandsOnTheCounterAtLevelStart() {
        controller.startLevel(
                new Level(1, LevelMode.SORT_ONLY, List.of(MILK, LETTUCE, CHICKEN), 60));

        assertEquals(List.of(MILK, LETTUCE, CHICKEN), unlocked, "in level order");
        assertEquals(3, state.getItemsLeft());
    }

    @Test
    void clearingRowsAfterwardsReleasesNothingMore() {
        controller.startLevel(new Level(1, LevelMode.SORT_ONLY, List.of(MILK, LETTUCE), 60));
        unlocked.clear();

        int released = controller.awardClearedRows(2);

        assertEquals(0, released, "the queue was drained at level start");
        assertTrue(unlocked.isEmpty());
    }

    @Test
    void sortingTheWholeQuotaCompletesTheLevelWithATimeBonus() {
        int[] bonus = {-1};
        controller.setOnLevelComplete(b -> bonus[0] = b);
        controller.startLevel(new Level(1, LevelMode.SORT_ONLY, List.of(MILK, LETTUCE), 60));

        controller.handleDrop(MILK, StorageZone.TOP_SHELF);
        assertEquals(-1, bonus[0], "one item still unsorted");

        controller.handleDrop(LETTUCE, StorageZone.CRISPER);

        assertTrue(bonus[0] > 0, "beating the clock on level 1 still pays");
        assertEquals(0, state.getItemsLeft());
    }

    @Test
    void aTetrisOnlyLevelReleasesNothingBecauseItHasNoQuota() {
        controller.startLevel(new Level(2, LevelMode.TETRIS_ONLY, List.of(), 60, 550, 1));

        assertTrue(unlocked.isEmpty());
        assertEquals(0, state.getItemsLeft());
    }

    @Test
    void aCombinedLevelStillMakesYouEarnEveryItem() {
        controller.startLevel(new Level(3, LevelMode.COMBINED, List.of(MILK, LETTUCE), 180));

        assertTrue(unlocked.isEmpty(), "nothing is given away on the combined level");
        assertEquals(2, state.getItemsLeft());

        controller.awardClearedRows(1);
        assertEquals(List.of(MILK), unlocked);
    }

    @Test
    void aPreviousLevelsQuotaDoesNotLeakIntoTheNext() {
        controller.startLevel(new Level(1, LevelMode.SORT_ONLY, List.of(MILK, LETTUCE), 60));
        unlocked.clear();

        controller.startLevel(new Level(2, LevelMode.SORT_ONLY, List.of(CHICKEN), 60));

        assertEquals(List.of(CHICKEN), unlocked);
        assertEquals(1, state.getItemsLeft());
    }
}
