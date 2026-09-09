package com.fridgegame.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fridgegame.data.ItemCatalog;
import com.fridgegame.director.LevelDirector;
import com.fridgegame.director.LevelStats;
import com.fridgegame.model.FoodCategory;
import com.fridgegame.model.Level;
import com.fridgegame.model.LevelMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * Asks the model to set the next level's difficulty from measured performance.
 *
 * <p>The model sees counts, not a verdict — clear rate, drop accuracy, top-outs, how much
 * clock was left — and answers with gravity, a quota size, and which food categories to
 * emphasise, plus a one-line reason that goes to the console.
 *
 * <p><b>Every field is clamped in Java.</b> Guided decoding constrains the reply's shape,
 * not its judgement: a 40ms gravity is schema-valid and unplayable. The bounds here are
 * the actual contract, and the model is choosing within them. Mode, clock, level number
 * and required rows are never asked for at all — those are the level design.
 *
 * <p>Any failure returns the template. A difficulty tweak is a nice-to-have; being unable
 * to reach the next level is not an acceptable outcome of an unplugged network cable.
 */
public final class LlmLevelDirector implements LevelDirector {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_TOKENS = 140;
    private static final String SCHEMA_NAME = "level_tuning";

    static final int MIN_GRAVITY_MILLIS = 300;
    static final int MAX_GRAVITY_MILLIS = 800;
    static final int MIN_ITEMS = 3;
    static final int MAX_ITEMS = 10;
    /**
     * Room for a whole sentence. At 120 the schema's hard cut landed mid-word and the
     * console read as if the model had crashed halfway through a thought.
     */
    private static final int MAX_REASON_CHARS = 200;

    private static final String SYSTEM_PROMPT = """
            You tune the difficulty of a grocery-sorting game between levels.
            The player clears Tetris rows to earn groceries, then drags each into the
            fridge zone matching its category.
            Your goal is flow: they should be working hard and still succeeding.
            - If they struggled, failed, or barely beat the clock, make it EASIER.
            - If they cruised with time to spare, make it HARDER.
            - Change one or two things meaningfully rather than everything slightly.

            gravityMillis is the DELAY between a piece falling one row, in milliseconds.
            To make it HARDER, pick a SMALLER gravityMillis (pieces fall faster).
            To make it EASIER, pick a LARGER gravityMillis (pieces fall slower).
            itemCount is how many groceries they must sort: MORE is harder.
            categories biases which groceries appear.

            Keep reason under 25 words and make sure it matches the numbers you chose.
            Answer with JSON only.""";

    /** Guided-decoding schema — the reply cannot be anything else. */
    static final JsonNode TUNING_SCHEMA = buildSchema();

    private final JsonChat chat;
    private final long seed;

    public LlmLevelDirector(JsonChat chat, long seed) {
        this.chat = chat;
        this.seed = seed;
    }

    /** Wires a director onto a live client, binding the tuning schema. */
    public static LlmLevelDirector using(LlmClient client, long seed) {
        return new LlmLevelDirector(client.bind(TUNING_SCHEMA, SCHEMA_NAME, MAX_TOKENS), seed);
    }

    @Override
    public CompletableFuture<Level> nextLevel(Level template, LevelStats last) {
        LlmLog.director("level " + template.number() + " stats: " + describeStats(last));
        final String prompt;
        try {
            prompt = describe(template, last);
        } catch (RuntimeException e) {
            LlmLog.note("director prompt failed: " + e.getMessage());
            return CompletableFuture.completedFuture(template);
        }
        return chat.send(SYSTEM_PROMPT, prompt)
                .thenApply(reply -> apply(template, reply, seed))
                .exceptionally(error -> {
                    LlmLog.note("director unavailable, using authored level "
                            + template.number() + ": " + rootMessage(error));
                    return template;
                });
    }

    /**
     * Turns a reply into a level, clamping everything.
     *
     * <p>Visible for testing: the clamping is the interesting behaviour, and it must hold
     * for replies no live endpoint would happen to produce.
     */
    static Level apply(Level template, String reply, long seed) {
        JsonNode node;
        try {
            node = MAPPER.readTree(reply);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            LlmLog.note("director reply was not JSON, using authored level: " + reply);
            return template;
        }

        String reason = text(node.get("reason"));
        if (!reason.isBlank()) {
            LlmLog.thinking(reason.length() <= MAX_REASON_CHARS
                    ? reason
                    : reason.substring(0, MAX_REASON_CHARS).trim());
        }

        int gravity = clamp(
                node.path("gravityMillis").asInt(template.gravityMillis()),
                MIN_GRAVITY_MILLIS, MAX_GRAVITY_MILLIS);
        int itemCount = clamp(
                node.path("itemCount").asInt(template.items().size()),
                MIN_ITEMS, MAX_ITEMS);
        List<FoodCategory> categories = parseCategories(node.get("categories"));

        // A board-less level has no gravity to set; a level with nothing to sort has no quota.
        int appliedGravity = template.mode().hasTetris() ? gravity : template.gravityMillis();
        List<com.fridgegame.model.GroceryItem> items = template.mode().hasSorting()
                ? ItemCatalog.pick(itemCount, categories, seed + template.number())
                : template.items();

        // "proposes", not "applied": the player may already have clicked through to the
        // authored level while this request was still in flight. FridgeGameApp logs what
        // actually loaded.
        // Naming every category is the same as naming none; logging all five as an
        // "emphasis" reads like a decision when it is the absence of one.
        boolean noPreference =
                categories.isEmpty() || categories.size() == FoodCategory.values().length;
        LlmLog.director("level " + template.number() + " proposes gravity=" + appliedGravity
                + "ms items=" + items.size()
                + " emphasis=" + (noPreference ? "[any]" : categories.toString()));

        return template.withTuning(appliedGravity, items);
    }

    /** The measured numbers, as one console-friendly line. */
    private static String describeStats(LevelStats stats) {
        StringBuilder out = new StringBuilder(120);
        out.append("mode=").append(stats.mode())
                .append(" pieces=").append(stats.piecesLocked())
                .append(" rows=").append(stats.rowsCleared())
                .append(String.format(Locale.ROOT, " clear=%.2f", stats.clearRate()));
        if (stats.hadDrops()) {
            out.append(String.format(Locale.ROOT, " accuracy=%.0f%%", stats.dropAccuracy() * 100))
                    .append(" (").append(stats.correctDrops()).append("/")
                    .append(stats.correctDrops() + stats.wrongDrops()).append(")");
        } else {
            out.append(" accuracy=n/a");
        }
        out.append(" topouts=").append(stats.topOuts())
                .append(String.format(Locale.ROOT, " margin=%.2f", stats.clockMargin()))
                .append(" (").append(stats.secondsRemaining()).append("s of ")
                .append(stats.timeLimitSeconds()).append("s left)");
        return out.toString();
    }

    /** The prompt: what the level is, what it currently asks for, and how the player did. */
    static String describe(Level template, LevelStats last) {
        StringBuilder out = new StringBuilder(700);
        out.append("Level just finished:\n")
                .append("  mode: ").append(describeMode(last.mode())).append('\n')
                .append("  pieces placed: ").append(last.piecesLocked()).append('\n')
                .append("  rows cleared: ").append(last.rowsCleared())
                .append(String.format(Locale.ROOT, " (%.2f rows per piece)", last.clearRate()))
                .append('\n');
        if (last.hadDrops()) {
            out.append("  groceries sorted: ").append(last.correctDrops())
                    .append(" correct, ").append(last.wrongDrops()).append(" wrong")
                    .append(String.format(Locale.ROOT, " (%.0f%% accurate)",
                            last.dropAccuracy() * 100))
                    .append('\n');
        } else {
            out.append("  groceries sorted: none, that level had no sorting\n");
        }
        out.append("  topped out: ").append(last.topOuts()).append(" times\n")
                .append("  clock left at the end: ").append(last.secondsRemaining())
                .append("s of ").append(last.timeLimitSeconds()).append("s")
                .append(String.format(Locale.ROOT, " (%.0f%% spare)", last.clockMargin() * 100))
                .append('\n');
        if (last.mode() == LevelMode.TETRIS_ONLY) {
            // Without this the model reads the inevitable 0s as a near miss and eases off
            // every single time, which is the opposite of what the numbers mean.
            out.append("\n  IGNORE THE CLOCK FOR THIS LEVEL. It is designed to end only when\n")
                    .append("  the clock reaches zero, so 0s left is the normal, expected outcome\n")
                    .append("  and is NOT a near miss. Do not describe them as having had no time\n")
                    .append("  to spare. Judge this level on rows per piece and top-outs only:\n")
                    .append("  above 0.20 rows per piece with no top-outs is comfortable.\n");
        }
        out.append('\n');

        out.append("Level to configure now:\n")
                .append("  mode: ").append(describeMode(template.mode())).append('\n')
                .append("  clock: ").append(template.timeLimitSeconds())
                .append("s (fixed, you cannot change it)\n")
                .append("  currently authored gravityMillis: ").append(template.gravityMillis())
                .append('\n')
                .append("  currently authored itemCount: ").append(template.items().size())
                .append("\n\n");

        out.append("Allowed ranges: gravityMillis ").append(MIN_GRAVITY_MILLIS).append('-')
                .append(MAX_GRAVITY_MILLIS).append(", itemCount ").append(MIN_ITEMS)
                .append('-').append(MAX_ITEMS).append(".\n")
                .append("categories must come from: ");
        FoodCategory[] all = FoodCategory.values();
        for (int i = 0; i < all.length; i++) {
            out.append(all[i]);
            if (i < all.length - 1) {
                out.append(", ");
            }
        }
        out.append(".\nGive a one-sentence reason for your choice.");
        return out.toString();
    }

    private static String describeMode(LevelMode mode) {
        return switch (mode) {
            case SORT_ONLY -> "sorting only, no Tetris board";
            case TETRIS_ONLY -> "Tetris only, nothing to sort";
            case COMBINED -> "Tetris and sorting together";
        };
    }

    private static List<FoodCategory> parseCategories(JsonNode node) {
        List<FoodCategory> categories = new ArrayList<>();
        if (node == null || !node.isArray()) {
            return categories;
        }
        for (JsonNode entry : node) {
            if (!entry.isTextual()) {
                continue;
            }
            try {
                FoodCategory category =
                        FoodCategory.valueOf(entry.asText().trim().toUpperCase(Locale.ROOT));
                if (!categories.contains(category)) {
                    categories.add(category);
                }
            } catch (IllegalArgumentException e) {
                // An unknown category is a preference we cannot honour, not a failure.
                LlmLog.note("director named unknown category '" + entry.asText() + "', ignoring");
            }
        }
        return categories;
    }

    private static String text(JsonNode node) {
        return node == null || !node.isTextual() ? "" : node.asText().trim();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String rootMessage(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        return message == null || message.isBlank() ? cause.getClass().getSimpleName() : message;
    }

    private static JsonNode buildSchema() {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        schema.putArray("required")
                .add("gravityMillis").add("itemCount").add("categories").add("reason");

        ObjectNode properties = schema.putObject("properties");
        properties.putObject("gravityMillis")
                .put("type", "integer")
                .put("minimum", MIN_GRAVITY_MILLIS)
                .put("maximum", MAX_GRAVITY_MILLIS);
        properties.putObject("itemCount")
                .put("type", "integer")
                .put("minimum", MIN_ITEMS)
                .put("maximum", MAX_ITEMS);

        ObjectNode categories = properties.putObject("categories");
        categories.put("type", "array");
        categories.put("maxItems", FoodCategory.values().length);
        ArrayNode allowed = categories.putObject("items").put("type", "string").putArray("enum");
        for (FoodCategory category : FoodCategory.values()) {
            allowed.add(category.name());
        }

        properties.putObject("reason")
                .put("type", "string")
                .put("minLength", 1)
                .put("maxLength", MAX_REASON_CHARS);
        return schema;
    }
}
