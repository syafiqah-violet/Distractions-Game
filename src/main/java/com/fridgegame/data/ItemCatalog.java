package com.fridgegame.data;

import com.fridgegame.model.FoodCategory;
import com.fridgegame.model.GroceryItem;
import com.fridgegame.model.Level;
import com.fridgegame.model.LevelMode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Master list of groceries and the levels built from them. */
public final class ItemCatalog {

    private static final String ICON_BASE = "/com/fridgegame/images/food_pixel/";

    public static final List<GroceryItem> ALL_ITEMS = List.of(
            new GroceryItem("milk", "Milk", ICON_BASE + "milk.png", FoodCategory.DAIRY),
            new GroceryItem("cheese", "Cheese", ICON_BASE + "cheese.png", FoodCategory.DAIRY),
            new GroceryItem("butter", "Butter", ICON_BASE + "butter.png", FoodCategory.DAIRY),
            new GroceryItem("lettuce", "Lettuce", ICON_BASE + "lettuce.png", FoodCategory.PRODUCE),
            new GroceryItem("carrot", "Carrot", ICON_BASE + "carrot.png", FoodCategory.PRODUCE),
            new GroceryItem("tomato", "Tomato", ICON_BASE + "tomato.png", FoodCategory.PRODUCE),
            new GroceryItem("broccoli", "Broccoli", ICON_BASE + "broccoli.png", FoodCategory.PRODUCE),
            new GroceryItem("chicken", "Chicken", ICON_BASE + "chicken.png", FoodCategory.MEAT),
            new GroceryItem("steak", "Steak", ICON_BASE + "steak.png", FoodCategory.MEAT),
            new GroceryItem("fish", "Fish", ICON_BASE + "fish.png", FoodCategory.MEAT),
            new GroceryItem("juice", "Juice", ICON_BASE + "juice.png", FoodCategory.DRINKS),
            new GroceryItem("soda", "Soda", ICON_BASE + "soda.png", FoodCategory.DRINKS),
            new GroceryItem("water", "Water", ICON_BASE + "water.png", FoodCategory.DRINKS),
            new GroceryItem("ice_cream", "Ice Cream", ICON_BASE + "ice_cream.png", FoodCategory.FROZEN),
            new GroceryItem("ice", "Ice", ICON_BASE + "ice.png", FoodCategory.FROZEN)
    );

    private static final Map<String, GroceryItem> BY_ID = ALL_ITEMS.stream()
            .collect(Collectors.toUnmodifiableMap(GroceryItem::id, Function.identity()));

    /**
     * The three rounds, each teaching a different thing.
     *
     * <p>Level 1 is drag-and-drop with the shopping already done; level 2 is Tetris with
     * nothing to sort; level 3 is both, with triple the clock because it is the only level
     * where you have to do two things at once.
     *
     * <p>These are <b>templates</b>. The level director may retune gravity and the item
     * quota of levels 2 and 3 from how the player actually performed — see
     * {@link Level#withTuning}. Mode, clock and required rows are fixed here.
     */
    public static final List<Level> LEVELS = List.of(
            //        n  mode                     items                             time  grav  rows
            new Level(1, LevelMode.SORT_ONLY,
                    itemsById("milk", "lettuce", "chicken", "juice"),                60,     0, 0),
            new Level(2, LevelMode.TETRIS_ONLY,
                    List.of(),                                                       60,   550, 1),
            new Level(3, LevelMode.COMBINED,
                    itemsById("milk", "cheese", "lettuce", "carrot",
                            "tomato", "chicken"),                                   180,   480, 0)
    );

    private ItemCatalog() {
    }

    public static GroceryItem byId(String id) {
        GroceryItem item = BY_ID.get(id);
        if (item == null) {
            throw new IllegalArgumentException("No grocery item with id: " + id);
        }
        return item;
    }

    /**
     * Picks {@code count} groceries, favouring {@code emphasis} categories.
     *
     * <p>Always spans at least two categories: a quota drawn entirely from one category
     * would make the fridge a single target instead of a sorting problem, and the director
     * asking for "all dairy" should not be able to delete the mechanic.
     *
     * <p>Deterministic for a given {@code seed}, so a director decision can be replayed.
     *
     * @param emphasis categories to draw from first; empty or null means no preference
     */
    public static List<GroceryItem> pick(int count, List<FoodCategory> emphasis, long seed) {
        int wanted = Math.max(1, Math.min(count, ALL_ITEMS.size()));
        Random random = new Random(seed);

        Set<FoodCategory> preferred = emphasis == null || emphasis.isEmpty()
                ? Set.of()
                : EnumSet.copyOf(emphasis);

        List<GroceryItem> favoured = new ArrayList<>();
        List<GroceryItem> rest = new ArrayList<>();
        for (GroceryItem item : ALL_ITEMS) {
            (preferred.contains(item.category()) ? favoured : rest).add(item);
        }
        Collections.shuffle(favoured, random);
        Collections.shuffle(rest, random);

        List<GroceryItem> pool = new ArrayList<>(favoured);
        pool.addAll(rest);

        List<GroceryItem> picked = new ArrayList<>(pool.subList(0, wanted));
        // A one-category quota is not a sorting problem; swap the last slot for an outsider.
        if (wanted > 1 && picked.stream().map(GroceryItem::category).distinct().count() == 1) {
            FoodCategory only = picked.get(0).category();
            pool.stream()
                    .filter(item -> item.category() != only)
                    .findFirst()
                    .ifPresent(outsider -> picked.set(wanted - 1, outsider));
        }
        return List.copyOf(picked);
    }

    private static List<GroceryItem> itemsById(String... ids) {
        return List.of(ids).stream().map(ItemCatalog::byId).collect(Collectors.toUnmodifiableList());
    }
}
