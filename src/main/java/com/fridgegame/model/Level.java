package com.fridgegame.model;

import java.util.List;

public record Level(int number, List<GroceryItem> items, int timeLimitSeconds) {
}
