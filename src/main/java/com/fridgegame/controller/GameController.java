package com.fridgegame.controller;

import com.fridgegame.director.LevelStats;
import com.fridgegame.model.GameState;
import com.fridgegame.model.GroceryItem;
import com.fridgegame.model.Level;
import com.fridgegame.model.LevelMode;
import com.fridgegame.model.StorageZone;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/** Correct/wrong drop rules, scoring, level completion and the countdown tick. */
public class GameController {

    private static final int CORRECT_BASE_POINTS = 10;
    private static final int WRONG_PENALTY = 5;
    private static final int STREAK_DIVISOR = 5;
    private static final int TIME_BONUS_PER_SECOND = 2;

    /**
     * Points for clearing 1–4 rows at once, indexed by row count.
     *
     * <p>Steeply superlinear on purpose, in the spirit of the original Tetris table: on the
     * Tetris-only level there are no groceries to earn, so stacking for a multi-row clear
     * has to be worth the risk of doing it.
     */
    private static final int[] ROW_POINTS = {0, 20, 60, 150, 400};

    /**
     * What a mismatched pair on the mix-and-match level costs.
     *
     * <p>Points and the streak, and deliberately <b>not</b> a life — the one place a mistake
     * is scored differently from a wrong drop. Turning two cards over is how you find out
     * what is under them, so the first guess at any pair is unavoidable rather than careless:
     * six pairs costs even a perfect memory four to six mismatches to solve. Charging a life
     * for each, out of three that carry over from earlier levels, would make the level a
     * formality to fail rather than a puzzle to solve.
     *
     * <p>The clock is the real pressure here. A mismatch costs the seconds spent reading it,
     * which is a penalty that scales with how lost the player actually is.
     */
    private static final int MISMATCH_PENALTY = 5;

    private final GameState state;

    /** The level's quota, waiting to be earned one cleared row at a time. */
    private final Deque<GroceryItem> locked = new ArrayDeque<>();

    private Level level;
    private boolean paused;

    // Measured performance for the current level; read by the director between levels.
    private int correctDrops;
    private int wrongDrops;
    private int topOuts;

    private IntConsumer onLevelComplete = bonus -> { };
    private Runnable onGameOver = () -> { };
    private Consumer<GroceryItem> onItemUnlocked = item -> { };
    private BiConsumer<GroceryItem, StorageZone> onWrongDrop = (item, zone) -> { };
    private IntConsumer onStreak = streak -> { };
    private Runnable onActivity = () -> { };

    public GameController(GameState state) {
        this.state = state;
    }

    /**
     * Loads a new level's quota and time limit.
     *
     * <p>On {@link LevelMode#SORT_ONLY} the whole quota is released immediately — that
     * level has no Tetris board to earn it from, and the point is to practise sorting.
     * Otherwise nothing is on the counter until rows are cleared.
     *
     * <p>On {@link LevelMode#MEMORY} the level's items are the <i>pairs</i> to find rather
     * than a quota to sort, so nothing is released at all and {@code itemsLeft} counts down
     * pairs instead of groceries. Both readings agree on what matters: the level is over when
     * it reaches zero.
     *
     * <p>Callers must have the counter view built before calling this, since the release
     * fires {@code onItemUnlocked} synchronously.
     */
    public void startLevel(Level level) {
        this.level = level;
        state.startLevel(level);
        locked.clear();
        locked.addAll(level.items());
        correctDrops = 0;
        wrongDrops = 0;
        topOuts = 0;
        if (level.mode() == LevelMode.SORT_ONLY) {
            releaseAll();
        }
    }

    public void setOnLevelComplete(IntConsumer listener) {
        this.onLevelComplete = listener;
    }

    public void setOnGameOver(Runnable listener) {
        this.onGameOver = listener;
    }

    /** Fired once per grocery item released onto the counter. */
    public void setOnItemUnlocked(Consumer<GroceryItem> listener) {
        this.onItemUnlocked = listener;
    }

    /** Fired when an item goes into a zone that does not accept it. */
    public void setOnWrongDrop(BiConsumer<GroceryItem, StorageZone> listener) {
        this.onWrongDrop = listener;
    }

    /** Fired with the streak length each time it reaches a multiple of {@link #STREAK_DIVISOR}. */
    public void setOnStreak(IntConsumer listener) {
        this.onStreak = listener;
    }

    /**
     * Fired on every drop, right or wrong.
     *
     * <p>Separate from the other listeners because most drops are not worth remarking on
     * but all of them are evidence that the player is still playing.
     */
    public void setOnActivity(Runnable listener) {
        this.onActivity = listener;
    }

    /**
     * Mirrors the app-wide pause, so a paused game cannot be scored against.
     *
     * <p>The pause overlay already swallows the mouse, so nothing should reach
     * {@link #handleDrop} while this is set. It is here anyway because the overlay's block
     * is a property of a stylesheet — one missing background colour and the node stops
     * being a mouse target, silently, with the whole fridge live underneath and the clock
     * frozen. This is the layer that makes that a cosmetic bug rather than free score.
     */
    public void setPaused(boolean value) {
        this.paused = value;
    }

    public boolean isPaused() {
        return paused;
    }

    /** Everything the director needs to know about how the level just went. */
    public LevelStats snapshot(int piecesLocked) {
        return new LevelStats(
                level == null ? LevelMode.COMBINED : level.mode(),
                piecesLocked,
                state.getRowsCleared(),
                correctDrops,
                wrongDrops,
                topOuts,
                Math.max(0, state.getSecondsLeft()),
                level == null ? 0 : level.timeLimitSeconds());
    }

    /**
     * Pays out {@code rows} cleared rows.
     *
     * <p>Rows always score — that is the whole economy on the Tetris-only level. On a level
     * with a quota they additionally buy one grocery item each, in level order, until the
     * quota is exhausted.
     *
     * @return how many items were actually released
     */
    public int awardClearedRows(int rows) {
        if (rows <= 0) {
            return 0;
        }
        state.setRowsCleared(state.getRowsCleared() + rows);
        state.setScore(state.getScore() + ROW_POINTS[Math.min(rows, ROW_POINTS.length - 1)]);

        int released = 0;
        for (int i = 0; i < rows; i++) {
            GroceryItem item = locked.poll();
            if (item == null) {
                break;
            }
            onItemUnlocked.accept(item);
            released++;
        }
        return released;
    }

    /** Applies scoring rules for dropping {@code item} into {@code zone}; returns whether it was correct. */
    public boolean handleDrop(GroceryItem item, StorageZone zone) {
        if (paused) {
            return false; // scores nothing, costs nothing, and counts as no activity
        }
        boolean correct = zone.accepts(item);
        onActivity.run();
        if (correct) {
            creditProgress();
        } else {
            wrongDrops++;
            state.setScore(state.getScore() - WRONG_PENALTY);
            state.setStreak(0);
            state.setLives(state.getLives() - 1);
            onWrongDrop.accept(item, zone);
            if (state.getLives() <= 0) {
                onGameOver.run();
            }
        }
        return correct;
    }

    /**
     * Pays out one matched pair on the mix-and-match level.
     *
     * <p>Scored exactly like a correct drop, streak multiplier included, so a run of matches
     * is worth what a run of correct drops is worth and the HUD's streak means one thing
     * across the whole game. The last pair completes the level the same way the last grocery
     * does — see {@link #creditProgress()}.
     */
    public void awardMatchedPair() {
        if (paused) {
            return;
        }
        onActivity.run();
        creditProgress();
    }

    /**
     * Charges a mismatched pair on the mix-and-match level.
     *
     * <p>Costs points and the streak but never a life, which is the one asymmetry with a
     * wrong drop; {@link #MISMATCH_PENALTY} has the reasoning. A memory level can therefore
     * only be lost on the clock, never by running out of lives on it.
     */
    public void penalizeMismatch() {
        if (paused) {
            return;
        }
        onActivity.run();
        // Counted as a wrong drop, so the director sees one measure of "got it wrong" rather
        // than a level that looks flawless because its mistakes had a different name.
        wrongDrops++;
        state.setScore(state.getScore() - MISMATCH_PENALTY);
        state.setStreak(0);
    }

    /**
     * Charges a life for letting the Tetris stack reach the top.
     *
     * <p>Deliberately the same penalty shape as a wrong drop — lose a life, lose the
     * streak — so topping out is a setback rather than an instant loss. The caller
     * clears the board afterwards if any lives remain.
     */
    public void penalizeTopOut() {
        topOuts++;
        state.setStreak(0);
        state.setLives(state.getLives() - 1);
        if (state.getLives() <= 0) {
            onGameOver.run();
        }
    }

    /**
     * Advances the countdown by one second and decides what hitting zero means.
     *
     * <p>Mode-dependent, and that asymmetry is the point. On the sorting levels the clock
     * is a deadline: unsorted items when it expires is a loss. On the Tetris-only level
     * there is nothing to finish, so surviving the full minute <i>is</i> the objective —
     * provided at least {@link Level#requiredRows()} rows went down, which is what stops
     * a player from passing by parking pieces in a corner and waiting.
     *
     * <p>{@link LevelMode#MEMORY} takes the deadline reading deliberately, not by accident of
     * falling through: pairs still on the board when the clock expires is a loss. Finding
     * some of them is not finishing, and the level-complete card would be a lie.
     */
    public void tick() {
        state.setSecondsLeft(state.getSecondsLeft() - 1);
        if (state.getSecondsLeft() > 0) {
            return;
        }
        if (level != null && level.mode() == LevelMode.TETRIS_ONLY) {
            if (state.getRowsCleared() >= level.requiredRows()) {
                onLevelComplete.accept(0);
            } else {
                onGameOver.run();
            }
        } else {
            onGameOver.run();
        }
    }

    /**
     * One unit of progress towards finishing the level: a sorted grocery, or a matched pair.
     *
     * <p>Shared so that the two mechanics cannot drift apart on the thing that ends a level.
     * When this was written out twice, only one copy knew about the time bonus.
     */
    private void creditProgress() {
        correctDrops++;
        int bonus = CORRECT_BASE_POINTS * (1 + state.getStreak() / STREAK_DIVISOR);
        state.setScore(state.getScore() + bonus);
        state.setStreak(state.getStreak() + 1);
        state.setItemsLeft(state.getItemsLeft() - 1);
        if (state.getItemsLeft() == 0) {
            int timeBonus = state.getSecondsLeft() * TIME_BONUS_PER_SECOND;
            state.setScore(state.getScore() + timeBonus);
            onLevelComplete.accept(timeBonus);
            return;
        }
        if (state.getStreak() % STREAK_DIVISOR == 0) {
            onStreak.accept(state.getStreak());
        }
    }

    /** Hands the entire remaining quota to the counter at once. */
    private void releaseAll() {
        GroceryItem item;
        while ((item = locked.poll()) != null) {
            onItemUnlocked.accept(item);
        }
    }
}
