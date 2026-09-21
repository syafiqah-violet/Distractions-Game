package com.fridgegame.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * A grid of face-down grocery cards for the mix-and-match level.
 *
 * <p>Deliberately free of JavaFX imports, like the rest of {@code model/} — the flip rules
 * are unit-testable with no display. The board knows nothing about rows and columns: it is a
 * flat list of cards and the view decides how to lay them out.
 *
 * <p>The shuffle is seeded, for the same reason the Tetris bag is: deterministic tests, and
 * a real game that does not deal both halves of a pair side by side every time.
 *
 * <p>At most two cards are face up and unmatched at once. The second flip resolves
 * immediately — the board reports {@link Outcome#MATCH} or {@link Outcome#MISMATCH} there and
 * then — but a mismatched pair stays visible until the caller calls {@link #hideMismatched()}.
 * That gap is the whole game: it is the player's one chance to see what they just turned over.
 * Every flip arriving during it is ignored, so a fast clicker cannot skip the reveal.
 */
public final class MemoryBoard {

    /** What a call to {@link #flip(int)} did. */
    public enum Outcome {

        /** Nothing happened: the index was out of range, already resolved, or a reveal is pending. */
        IGNORED,

        /** The first card of a pair is now face up; the board is waiting for its partner. */
        FIRST_UP,

        /** The two face-up cards match. They stay up for good. */
        MATCH,

        /** The two face-up cards do not match. They stay up until {@link #hideMismatched()}. */
        MISMATCH
    }

    /**
     * The result of a flip, naming the cards it touched.
     *
     * <p>Carries the indices so the view can animate exactly the cards that changed rather
     * than re-rendering the whole grid. {@code second} is -1 for {@link Outcome#FIRST_UP} and
     * both are -1 for {@link Outcome#IGNORED}.
     */
    public record FlipResult(Outcome outcome, int first, int second) {

        private static final FlipResult IGNORED = new FlipResult(Outcome.IGNORED, -1, -1);
    }

    private final List<GroceryItem> cards;
    private final boolean[] matched;

    /** Index of the first card of the pair in progress, or -1 when no pair is in progress. */
    private int firstUp = -1;

    /** Index of the second card, set only while a mismatch is on show awaiting its hide. */
    private int secondUp = -1;

    /**
     * Builds a shuffled board holding two of every item in {@code pairs}.
     *
     * @param pairs the distinct items to pair up; each contributes exactly two cards
     * @param seed  shuffle seed
     */
    public MemoryBoard(List<GroceryItem> pairs, long seed) {
        List<GroceryItem> deck = new ArrayList<>(pairs.size() * 2);
        for (GroceryItem item : pairs) {
            deck.add(item);
            deck.add(item);
        }
        Collections.shuffle(deck, new Random(seed));
        this.cards = List.copyOf(deck);
        this.matched = new boolean[cards.size()];
    }

    /** How many cards are on the board — twice the number of pairs. */
    public int size() {
        return cards.size();
    }

    /** The total number of pairs to find. */
    public int pairCount() {
        return cards.size() / 2;
    }

    /** How many pairs have been matched so far. */
    public int matchedPairs() {
        int count = 0;
        for (boolean m : matched) {
            if (m) {
                count++;
            }
        }
        return count / 2;
    }

    public GroceryItem cardAt(int index) {
        return cards.get(index);
    }

    /** Whether this card has been matched and is up for the rest of the level. */
    public boolean isMatched(int index) {
        return matched[index];
    }

    /** Whether the player can currently see this card's face, matched or merely turned over. */
    public boolean isFaceUp(int index) {
        return matched[index] || index == firstUp || index == secondUp;
    }

    /** Whether a mismatched pair is on show, waiting for {@link #hideMismatched()}. */
    public boolean isRevealPending() {
        return secondUp >= 0;
    }

    public boolean isSolved() {
        for (boolean m : matched) {
            if (!m) {
                return false;
            }
        }
        return true;
    }

    /**
     * Turns card {@code index} face up and resolves the pair if it completes one.
     *
     * <p>Ignores anything that is not a legal flip rather than throwing: the caller is a mouse
     * click, and clicking a matched card or double-clicking the same one is ordinary play, not
     * a programming error.
     */
    public FlipResult flip(int index) {
        if (index < 0 || index >= cards.size()) {
            return FlipResult.IGNORED;
        }
        if (isRevealPending() || matched[index] || index == firstUp) {
            return FlipResult.IGNORED;
        }

        if (firstUp < 0) {
            firstUp = index;
            return new FlipResult(Outcome.FIRST_UP, index, -1);
        }

        int first = firstUp;
        if (cards.get(first).id().equals(cards.get(index).id())) {
            matched[first] = true;
            matched[index] = true;
            firstUp = -1;
            return new FlipResult(Outcome.MATCH, first, index);
        }

        // Left face up on purpose. The pair only goes back down when the caller has given the
        // player long enough to read it.
        secondUp = index;
        return new FlipResult(Outcome.MISMATCH, first, index);
    }

    /**
     * Turns the mismatched pair back down, ending the reveal.
     *
     * <p>A no-op unless a mismatch is actually on show, so a late or duplicated timer callback
     * cannot wipe the first card of the next pair.
     */
    public void hideMismatched() {
        if (!isRevealPending()) {
            return;
        }
        firstUp = -1;
        secondUp = -1;
    }
}
