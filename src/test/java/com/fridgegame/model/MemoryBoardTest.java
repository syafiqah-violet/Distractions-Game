package com.fridgegame.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The flip rules for the mix-and-match level.
 *
 * <p>The reveal window is where every interesting case lives. A mismatched pair stays visible
 * until the caller hides it, which means for a fraction of a second the board is showing two
 * cards it is about to take away — and a player clicking through that window must not be able
 * to start a new pair, skip the reveal, or wipe a card the timer never meant to touch.
 */
class MemoryBoardTest {

    private static final GroceryItem MILK =
            new GroceryItem("milk", "Milk", "/icons/milk.png", FoodCategory.DAIRY);
    private static final GroceryItem LETTUCE =
            new GroceryItem("lettuce", "Lettuce", "/icons/lettuce.png", FoodCategory.PRODUCE);
    private static final GroceryItem STEAK =
            new GroceryItem("steak", "Steak", "/icons/steak.png", FoodCategory.MEAT);

    private static final List<GroceryItem> PAIRS = List.of(MILK, LETTUCE, STEAK);

    /** The two positions holding {@code item}, in index order. */
    private static int[] positionsOf(MemoryBoard board, GroceryItem item) {
        List<Integer> found = new ArrayList<>();
        for (int i = 0; i < board.size(); i++) {
            if (board.cardAt(i).id().equals(item.id())) {
                found.add(i);
            }
        }
        assertEquals(2, found.size(), item.name() + " must appear exactly twice");
        return new int[] {found.get(0), found.get(1)};
    }

    /** Matches every pair on the board, hiding mismatches as it goes. */
    private static void solve(MemoryBoard board) {
        for (GroceryItem item : PAIRS) {
            int[] at = positionsOf(board, item);
            board.flip(at[0]);
            board.flip(at[1]);
        }
    }

    // ------------------------------------------------------------------ deal

    @Test
    void dealsTwoOfEveryItem() {
        MemoryBoard board = new MemoryBoard(PAIRS, 1L);

        assertEquals(6, board.size());
        assertEquals(3, board.pairCount());

        Map<String, Integer> counts = new HashMap<>();
        for (int i = 0; i < board.size(); i++) {
            counts.merge(board.cardAt(i).id(), 1, Integer::sum);
        }
        assertEquals(Map.of("milk", 2, "lettuce", 2, "steak", 2), counts);
    }

    @Test
    void theSameSeedDealsTheSameBoard() {
        MemoryBoard first = new MemoryBoard(PAIRS, 99L);
        MemoryBoard second = new MemoryBoard(PAIRS, 99L);

        for (int i = 0; i < first.size(); i++) {
            assertEquals(first.cardAt(i), second.cardAt(i));
        }
    }

    @Test
    void differentSeedsDealDifferentBoards() {
        // Not a strict guarantee for any one pair of seeds, so scan a range: a shuffle that
        // ignored its seed would return the same layout for every single one of them.
        MemoryBoard reference = new MemoryBoard(PAIRS, 0L);
        List<String> referenceIds = new ArrayList<>();
        for (int i = 0; i < reference.size(); i++) {
            referenceIds.add(reference.cardAt(i).id());
        }

        boolean sawSomethingElse = false;
        for (long seed = 1; seed < 30 && !sawSomethingElse; seed++) {
            MemoryBoard other = new MemoryBoard(PAIRS, seed);
            for (int i = 0; i < other.size(); i++) {
                if (!other.cardAt(i).id().equals(referenceIds.get(i))) {
                    sawSomethingElse = true;
                    break;
                }
            }
        }
        assertTrue(sawSomethingElse, "the seed is being ignored");
    }

    @Test
    void startsWithEverythingFaceDown() {
        MemoryBoard board = new MemoryBoard(PAIRS, 5L);

        for (int i = 0; i < board.size(); i++) {
            assertFalse(board.isFaceUp(i));
            assertFalse(board.isMatched(i));
        }
        assertFalse(board.isSolved());
        assertFalse(board.isRevealPending());
    }

    // ----------------------------------------------------------------- flips

    @Test
    void theFirstFlipTurnsOneCardOverAndWaits() {
        MemoryBoard board = new MemoryBoard(PAIRS, 7L);
        int card = positionsOf(board, MILK)[0];

        MemoryBoard.FlipResult result = board.flip(card);

        assertEquals(MemoryBoard.Outcome.FIRST_UP, result.outcome());
        assertEquals(card, result.first());
        assertEquals(-1, result.second(), "there is no second card yet");
        assertTrue(board.isFaceUp(card));
        assertFalse(board.isMatched(card));
        assertFalse(board.isRevealPending());
    }

    @Test
    void aMatchingSecondFlipLocksBothCardsFaceUp() {
        MemoryBoard board = new MemoryBoard(PAIRS, 11L);
        int[] milk = positionsOf(board, MILK);

        board.flip(milk[0]);
        MemoryBoard.FlipResult result = board.flip(milk[1]);

        assertEquals(MemoryBoard.Outcome.MATCH, result.outcome());
        assertEquals(milk[0], result.first());
        assertEquals(milk[1], result.second());
        assertTrue(board.isMatched(milk[0]));
        assertTrue(board.isMatched(milk[1]));
        assertEquals(1, board.matchedPairs());
        assertFalse(board.isRevealPending(), "a match has nothing to hide");
    }

    @Test
    void aMismatchLeavesBothOnShowUntilTheyAreHidden() {
        MemoryBoard board = new MemoryBoard(PAIRS, 13L);
        int milk = positionsOf(board, MILK)[0];
        int lettuce = positionsOf(board, LETTUCE)[0];

        MemoryBoard.FlipResult result = board.flip(milk);
        assertEquals(MemoryBoard.Outcome.FIRST_UP, result.outcome());
        result = board.flip(lettuce);

        assertEquals(MemoryBoard.Outcome.MISMATCH, result.outcome());
        assertTrue(board.isRevealPending());
        assertTrue(board.isFaceUp(milk), "the player must get to see what they turned over");
        assertTrue(board.isFaceUp(lettuce));

        board.hideMismatched();

        assertFalse(board.isFaceUp(milk));
        assertFalse(board.isFaceUp(lettuce));
        assertFalse(board.isMatched(milk));
        assertFalse(board.isRevealPending());
    }

    @Test
    void aThirdCardClickedDuringTheRevealIsIgnored() {
        MemoryBoard board = new MemoryBoard(PAIRS, 17L);
        int milk = positionsOf(board, MILK)[0];
        int lettuce = positionsOf(board, LETTUCE)[0];
        int steak = positionsOf(board, STEAK)[0];

        board.flip(milk);
        board.flip(lettuce);
        MemoryBoard.FlipResult result = board.flip(steak);

        assertEquals(MemoryBoard.Outcome.IGNORED, result.outcome());
        assertFalse(board.isFaceUp(steak), "clicking fast must not skip the reveal");
    }

    @Test
    void hidingAfterTheRevealDoesNotWipeTheNextPairsFirstCard() {
        // The timer fires once, but a duplicated or late callback must not reach past the
        // pair it was scheduled for and take down a card the player has just turned over.
        MemoryBoard board = new MemoryBoard(PAIRS, 19L);
        int milk = positionsOf(board, MILK)[0];
        int lettuce = positionsOf(board, LETTUCE)[0];
        int steak = positionsOf(board, STEAK)[0];

        board.flip(milk);
        board.flip(lettuce);
        board.hideMismatched();

        board.flip(steak);
        board.hideMismatched();

        assertTrue(board.isFaceUp(steak));
    }

    @Test
    void reflippingTheSameCardChangesNothing() {
        MemoryBoard board = new MemoryBoard(PAIRS, 23L);
        int milk = positionsOf(board, MILK)[0];

        board.flip(milk);
        MemoryBoard.FlipResult result = board.flip(milk);

        assertEquals(MemoryBoard.Outcome.IGNORED, result.outcome(),
                "a double-click must not match a card against itself");
        assertTrue(board.isFaceUp(milk));
        assertFalse(board.isMatched(milk));
    }

    @Test
    void aMatchedCardCannotBeFlippedAgain() {
        MemoryBoard board = new MemoryBoard(PAIRS, 29L);
        int[] milk = positionsOf(board, MILK);

        board.flip(milk[0]);
        board.flip(milk[1]);
        MemoryBoard.FlipResult result = board.flip(milk[0]);

        assertEquals(MemoryBoard.Outcome.IGNORED, result.outcome());
        assertTrue(board.isMatched(milk[0]), "still matched, still face up");
    }

    @Test
    void outOfRangeIndicesAreIgnoredRatherThanThrowing() {
        // The caller is a mouse click, not a programmer.
        MemoryBoard board = new MemoryBoard(PAIRS, 31L);

        assertEquals(MemoryBoard.Outcome.IGNORED, board.flip(-1).outcome());
        assertEquals(MemoryBoard.Outcome.IGNORED, board.flip(board.size()).outcome());
    }

    // ---------------------------------------------------------------- solved

    @Test
    void isSolvedOnlyOnceEveryPairIsMatched() {
        MemoryBoard board = new MemoryBoard(PAIRS, 37L);
        int[] milk = positionsOf(board, MILK);

        board.flip(milk[0]);
        board.flip(milk[1]);
        assertFalse(board.isSolved(), "one pair of three is not a solved board");
        assertNotEquals(board.pairCount(), board.matchedPairs());

        solve(board);

        assertTrue(board.isSolved());
        assertEquals(board.pairCount(), board.matchedPairs());
    }
}
