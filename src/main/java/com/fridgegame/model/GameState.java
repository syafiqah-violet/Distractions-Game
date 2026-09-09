package com.fridgegame.model;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;

/**
 * Mutable game state exposed as JavaFX properties, so the HUD can bind
 * directly to it instead of being pushed updates.
 */
public class GameState {

    public static final int STARTING_LIVES = 3;

    private final IntegerProperty score = new SimpleIntegerProperty(0);
    private final IntegerProperty lives = new SimpleIntegerProperty(STARTING_LIVES);
    private final IntegerProperty streak = new SimpleIntegerProperty(0);
    private final IntegerProperty secondsLeft = new SimpleIntegerProperty(0);
    private final IntegerProperty level = new SimpleIntegerProperty(1);
    private final IntegerProperty rowsCleared = new SimpleIntegerProperty(0);
    private final IntegerProperty itemsLeft = new SimpleIntegerProperty(0);

    public IntegerProperty scoreProperty() {
        return score;
    }

    public int getScore() {
        return score.get();
    }

    public void setScore(int value) {
        score.set(value);
    }

    public IntegerProperty livesProperty() {
        return lives;
    }

    public int getLives() {
        return lives.get();
    }

    public void setLives(int value) {
        lives.set(value);
    }

    public IntegerProperty streakProperty() {
        return streak;
    }

    public int getStreak() {
        return streak.get();
    }

    public void setStreak(int value) {
        streak.set(value);
    }

    public IntegerProperty secondsLeftProperty() {
        return secondsLeft;
    }

    public int getSecondsLeft() {
        return secondsLeft.get();
    }

    public void setSecondsLeft(int value) {
        secondsLeft.set(value);
    }

    public IntegerProperty levelProperty() {
        return level;
    }

    public int getLevel() {
        return level.get();
    }

    public void setLevel(int value) {
        level.set(value);
    }

    /** Rows of Tetris cleared during the current level — one grocery item earned per row. */
    public IntegerProperty rowsClearedProperty() {
        return rowsCleared;
    }

    public int getRowsCleared() {
        return rowsCleared.get();
    }

    public void setRowsCleared(int value) {
        rowsCleared.set(value);
    }

    /** Items still to be sorted correctly before this level is complete. */
    public IntegerProperty itemsLeftProperty() {
        return itemsLeft;
    }

    public int getItemsLeft() {
        return itemsLeft.get();
    }

    public void setItemsLeft(int value) {
        itemsLeft.set(value);
    }

    /** Loads a new level's time limit and level number; score/lives/streak carry over. */
    public void startLevel(Level newLevel) {
        setLevel(newLevel.number());
        setSecondsLeft(newLevel.timeLimitSeconds());
        setRowsCleared(0);
        setItemsLeft(newLevel.items().size());
    }

    /** Resets score, lives and streak for a brand-new game (level is set separately via startLevel). */
    public void reset() {
        setScore(0);
        setLives(STARTING_LIVES);
        setStreak(0);
    }
}
