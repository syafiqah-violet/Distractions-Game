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

/** Cleared rows are the only way groceries reach the counter (phase T3). */
class GroceryUnlockTest {

    private static final GroceryItem MILK =
            new GroceryItem("milk", "Milk", "/icons/milk.png", FoodCategory.DAIRY);
    private static final GroceryItem LETTUCE =
            new GroceryItem("lettuce", "Lettuce", "/icons/lettuce.png", FoodCategory.PRODUCE);
    private static final GroceryItem CHICKEN =
            new GroceryItem("chicken", "Chicken", "/icons/chicken.png", FoodCategory.MEAT);

    private final GameState state = new GameState();
    private final GameController controller = new GameController(state);
    private final List<GroceryItem> unlocked = new ArrayList<>();

    private void startLevel(GroceryItem... items) {
        controller.setOnItemUnlocked(unlocked::add);
        controller.startLevel(new Level(1, LevelMode.COMBINED, List.of(items), 60));
    }

    @Test
    void noItemsAreOnTheCounterUntilARowIsCleared() {
        startLevel(MILK, LETTUCE, CHICKEN);

        assertTrue(unlocked.isEmpty());
        assertEquals(3, state.getItemsLeft());
    }

    @Test
    void oneClearedRowReleasesExactlyOneItem() {
        startLevel(MILK, LETTUCE, CHICKEN);

        int released = controller.awardClearedRows(1);

        assertEquals(1, released);
        assertEquals(List.of(MILK), unlocked);
        assertEquals(1, state.getRowsCleared());
    }

    @Test
    void aFourRowClearReleasesFourItems() {
        startLevel(MILK, LETTUCE, CHICKEN, MILK, LETTUCE);

        int released = controller.awardClearedRows(4);

        assertEquals(4, released);
        assertEquals(4, unlocked.size());
        assertEquals(4, state.getRowsCleared());
    }

    @Test
    void itemsAreReleasedInLevelOrder() {
        startLevel(MILK, LETTUCE, CHICKEN);

        controller.awardClearedRows(3);

        assertEquals(List.of(MILK, LETTUCE, CHICKEN), unlocked);
    }

    @Test
    void rowsBeyondTheQuotaStillScoreEvenThoughNoItemIsLeftToEarn() {
        startLevel(MILK);
        int scoreBefore = state.getScore();

        int released = controller.awardClearedRows(3);

        assertEquals(1, released, "only one item was left to earn");
        assertEquals(1, unlocked.size());
        assertEquals(scoreBefore + 150, state.getScore(), "a triple pays regardless of the quota");
        assertEquals(3, state.getRowsCleared(), "all three rows still count on the HUD");
    }

    @Test
    void multiRowClearsScoreSteeplyMoreThanSingles() {
        startLevel();

        controller.awardClearedRows(1);
        assertEquals(20, state.getScore());

        controller.awardClearedRows(4);
        assertEquals(20 + 400, state.getScore(), "a four-row clear is worth far more than four singles");
    }

    @Test
    void awardingZeroRowsChangesNothing() {
        startLevel(MILK);

        assertEquals(0, controller.awardClearedRows(0));
        assertEquals(0, state.getScore());
        assertEquals(0, state.getRowsCleared());
        assertTrue(unlocked.isEmpty());
    }

    @Test
    void quotaIsRefilledOnTheNextLevel() {
        startLevel(MILK, LETTUCE);
        controller.awardClearedRows(2);
        unlocked.clear();

        controller.startLevel(new Level(2, LevelMode.COMBINED, List.of(CHICKEN), 60));
        assertEquals(0, state.getRowsCleared(), "row count resets at level start");

        controller.awardClearedRows(1);

        assertEquals(List.of(CHICKEN), unlocked, "the previous level's queue must not leak");
        assertEquals(1, state.getRowsCleared(), "counting restarts from the new level");
    }

    @Test
    void levelCompletesOnlyAfterEveryEarnedItemIsSorted() {
        int[] bonus = {-1};
        controller.setOnLevelComplete(b -> bonus[0] = b);
        startLevel(MILK, LETTUCE);
        controller.awardClearedRows(2);

        controller.handleDrop(MILK, StorageZone.TOP_SHELF);
        assertEquals(-1, bonus[0], "one item still unsorted");
        assertEquals(1, state.getItemsLeft());

        controller.handleDrop(LETTUCE, StorageZone.CRISPER);
        assertTrue(bonus[0] >= 0, "sorting the last item completes the level");
        assertEquals(0, state.getItemsLeft());
    }

    @Test
    void aWrongDropDoesNotConsumeTheQuota() {
        startLevel(MILK, LETTUCE);
        controller.awardClearedRows(2);

        controller.handleDrop(MILK, StorageZone.CRISPER);

        assertEquals(2, state.getItemsLeft(), "the item bounced back and still needs sorting");
    }

    @Test
    void toppingOutCostsALifeAndTheStreakButNotTheQuota() {
        startLevel(MILK, LETTUCE);
        controller.awardClearedRows(1);
        state.setStreak(4);

        controller.penalizeTopOut();

        assertEquals(GameState.STARTING_LIVES - 1, state.getLives());
        assertEquals(0, state.getStreak());
        assertEquals(2, state.getItemsLeft());
        assertEquals(1, unlocked.size(), "the earned item stays on the counter");
    }

    @Test
    void toppingOutOnTheLastLifeEndsTheGame() {
        boolean[] over = {false};
        controller.setOnGameOver(() -> over[0] = true);
        startLevel(MILK);
        state.setLives(1);

        controller.penalizeTopOut();

        assertTrue(over[0]);
        assertEquals(0, state.getLives());
    }
}
