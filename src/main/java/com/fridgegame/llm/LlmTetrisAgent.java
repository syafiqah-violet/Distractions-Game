package com.fridgegame.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fridgegame.ai.TetrisAgent;
import com.fridgegame.model.Cell;
import com.fridgegame.model.TetrisBoard;
import com.fridgegame.model.TetrisMove;
import com.fridgegame.model.Tetromino;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * A Tetris opponent that asks the local LLM where to put each piece.
 *
 * <p>The board is rendered as ASCII and the reply is constrained by guided decoding to
 * {@code {"rotation":R,"column":C}}. The prompt also lists the legal column range per
 * rotation and the current column heights — supplying the geometry the model would
 * otherwise have to infer makes the difference between usable and useless answers.
 *
 * <p>Per the {@link TetrisAgent} threading contract, the board is read <b>synchronously</b>
 * in {@link #chooseMove} and only the resulting string crosses onto the HTTP thread; the
 * live board keeps mutating under gravity and must not be touched from a callback.
 */
public final class LlmTetrisAgent implements TetrisAgent {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_TOKENS = 40;
    private static final String SCHEMA_NAME = "tetris_move";

    private static final String SYSTEM_PROMPT = """
            You are a Tetris engine playing to survive and clear lines.
            Choose where to drop the current piece.
            Priorities, most important first:
            1. Clear rows when you can.
            2. Never leave an empty cell covered by a filled one.
            3. Keep the stack low and its surface flat.
            Answer with JSON only.""";

    /** Guided-decoding schema — the reply cannot be anything else. */
    static final JsonNode MOVE_SCHEMA = buildSchema();

    private final JsonChat chat;
    private final String label;

    public LlmTetrisAgent(JsonChat chat, String label) {
        this.chat = chat;
        this.label = label;
    }

    /** Wires an agent onto a live client, binding the move schema. */
    public static LlmTetrisAgent using(LlmClient client) {
        return new LlmTetrisAgent(
                client.bind(MOVE_SCHEMA, SCHEMA_NAME, MAX_TOKENS),
                client.config().model());
    }

    @Override
    public String label() {
        return label;
    }

    @Override
    public CompletableFuture<TetrisMove> chooseMove(TetrisBoard board) {
        final String prompt;
        try {
            prompt = describe(board);
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
        return chat.send(SYSTEM_PROMPT, prompt).thenApply(LlmTetrisAgent::parse);
    }

    /**
     * Reads the reply into a move.
     *
     * <p>Only shape is checked here. Whether the placement is actually reachable is the
     * caller's business — it holds the live board, which may have moved on since the
     * prompt was built.
     */
    static TetrisMove parse(String content) {
        JsonNode node;
        try {
            node = MAPPER.readTree(content);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalArgumentException("Move reply was not JSON: " + content, e);
        }
        JsonNode rotation = node.get("rotation");
        JsonNode column = node.get("column");
        if (rotation == null || column == null || !rotation.isNumber() || !column.isNumber()) {
            throw new IllegalArgumentException("Move reply lacked numeric rotation/column: " + content);
        }
        return new TetrisMove(rotation.asInt(), column.asInt());
    }

    /** Renders the board, the piece geometry, and the legal placements as a prompt. */
    static String describe(TetrisBoard board) {
        Tetromino piece = board.current();
        if (piece == null) {
            throw new IllegalStateException("No current piece to place");
        }
        Cell[][] stack = board.snapshot();
        StringBuilder out = new StringBuilder(600);

        out.append("Board is ").append(TetrisBoard.WIDTH).append(" wide, ")
                .append(TetrisBoard.HEIGHT).append(" tall. Top row first, last row is the floor.\n")
                .append("X = filled, . = empty.\n");
        for (int r = 0; r < TetrisBoard.HEIGHT; r++) {
            for (int c = 0; c < TetrisBoard.WIDTH; c++) {
                out.append(stack[r][c] == Cell.EMPTY ? '.' : 'X');
            }
            out.append('\n');
        }

        out.append("\nColumn heights (left to right): ");
        int[] heights = board.columnHeights();
        for (int c = 0; c < heights.length; c++) {
            out.append(heights[c]);
            if (c < heights.length - 1) {
                out.append(',');
            }
        }

        out.append("\n\nCurrent piece: ").append(piece.name()).append('\n');
        appendRotations(out, piece);
        out.append("Next piece: ").append(board.next() == null ? "?" : board.next().name()).append('\n');

        out.append("\nLegal placements as rotation:columns\n");
        for (int rotation = 0; rotation < Tetromino.ROTATIONS; rotation++) {
            List<Integer> legal = legalColumns(board, piece, rotation);
            if (legal.isEmpty()) {
                continue;
            }
            out.append("  ").append(rotation).append(": ");
            for (int i = 0; i < legal.size(); i++) {
                out.append(legal.get(i));
                if (i < legal.size() - 1) {
                    out.append(',');
                }
            }
            out.append('\n');
        }

        out.append("\n\"column\" is the leftmost column the piece occupies.")
                .append(" Pick one of the legal placements listed above.");
        return out.toString();
    }

    /** Draws each distinct rotation so the model knows the geometry it is placing. */
    private static void appendRotations(StringBuilder out, Tetromino piece) {
        List<String> drawn = new ArrayList<>();
        for (int rotation = 0; rotation < Tetromino.ROTATIONS; rotation++) {
            String shape = shapeOf(piece, rotation);
            if (drawn.contains(shape)) {
                continue; // I, O, S and Z repeat every two rotations
            }
            drawn.add(shape);
            out.append("  rotation ").append(rotation)
                    .append(" (").append(piece.width(rotation)).append(" wide, ")
                    .append(piece.height(rotation)).append(" tall): ")
                    .append(shape).append('\n');
        }
    }

    /** One rotation as a single line, rows separated by {@code /}, top row first. */
    private static String shapeOf(Tetromino piece, int rotation) {
        int height = piece.height(rotation);
        int width = piece.width(rotation);
        char[][] grid = new char[height][width];
        for (char[] row : grid) {
            java.util.Arrays.fill(row, '.');
        }
        for (int[] offset : piece.cells(rotation)) {
            grid[offset[0]][offset[1]] = 'X';
        }
        StringBuilder shape = new StringBuilder();
        for (int r = 0; r < height; r++) {
            if (r > 0) {
                shape.append('/');
            }
            shape.append(grid[r]);
        }
        return shape.toString();
    }

    private static List<Integer> legalColumns(TetrisBoard board, Tetromino piece, int rotation) {
        List<Integer> legal = new ArrayList<>();
        for (int column = 0; column + piece.width(rotation) <= TetrisBoard.WIDTH; column++) {
            if (board.restingRow(piece, rotation, column) >= 0) {
                legal.add(column);
            }
        }
        return legal;
    }

    private static JsonNode buildSchema() {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        schema.putArray("required").add("rotation").add("column");
        ObjectNode properties = schema.putObject("properties");
        properties.putObject("rotation")
                .put("type", "integer")
                .put("minimum", 0)
                .put("maximum", Tetromino.ROTATIONS - 1);
        properties.putObject("column")
                .put("type", "integer")
                .put("minimum", 0)
                .put("maximum", TetrisBoard.WIDTH - 1);
        return schema;
    }
}
