package com.fridgegame.data;

import com.fridgegame.model.FoodCategory;
import com.fridgegame.model.GroceryItem;
import com.fridgegame.model.Level;

import java.util.List;
import java.util.Map;
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

    public static final List<Level> LEVELS = List.of(
            new Level(1, itemsById("milk", "lettuce", "chicken", "juice", "ice"), 60),
            new Level(2, itemsById(
                    "milk", "cheese", "lettuce", "carrot", "chicken", "steak", "juice", "ice_cream"), 50),
            new Level(3, itemsById(
                    "milk", "cheese", "butter", "lettuce", "carrot", "tomato", "broccoli",
                    "chicken", "steak", "fish", "juice", "soda"), 45)
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

    private static List<GroceryItem> itemsById(String... ids) {
        return List.of(ids).stream().map(ItemCatalog::byId).collect(Collectors.toUnmodifiableList());
    }
}
