package com.fridgegame.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fridgegame.model.TetrisBoard;
import com.fridgegame.model.TetrisMove;
import com.fridgegame.model.Tetromino;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * The agent's prompt building, parsing and validation — all with a stubbed
 * {@link JsonChat}. Nothing here touches the network, so the suite passes with the
 * DGX box switched off.
 */
class LlmTetrisAgentTest {

    private static TetrisBoard boardStartingWith(Tetromino wanted) {
        for (long seed = 0; seed < 500; seed++) {
            TetrisBoard board = new TetrisBoard(seed);
            if (board.current() == wanted) {
                return board;
            }
        }
        throw new AssertionError("No seed below 500 deals " + wanted + " first");
    }

    private static LlmTetrisAgent agentReplying(String content) {
        return new LlmTetrisAgent((system, user) -> CompletableFuture.completedFuture(content), "stub");
    }

    // ------------------------------------------------------------------ parsing

    @Test
    void parsesTheCompactReplyGuidedDecodingProduces() {
        assertEquals(new TetrisMove(1, 3), LlmTetrisAgent.parse("{\"rotation\":1,\"column\":3}"));
    }

    @Test
    void parsesThePrettyPrintedFormToo() {
        assertEquals(new TetrisMove(0, 7),
                LlmTetrisAgent.parse("{\n  \"rotation\": 0,\n  \"column\": 7\n}"));
    }

    @Test
    void ignoresExtraFields() {
        assertEquals(new TetrisMove(2, 4),
                LlmTetrisAgent.parse("{\"rotation\":2,\"column\":4,\"why\":\"clears a row\"}"));
    }

    @Test
    void rejectsNonJsonReplies() {
        assertThrows(IllegalArgumentException.class,
                () -> LlmTetrisAgent.parse("I would put it on the left"));
    }

    @Test
    void rejectsRepliesMissingAField() {
        assertThrows(IllegalArgumentException.class,
                () -> LlmTetrisAgent.parse("{\"rotation\":1}"));
        assertThrows(IllegalArgumentException.class,
                () -> LlmTetrisAgent.parse("{\"column\":1}"));
    }

    @Test
    void rejectsNonNumericFields() {
        assertThrows(IllegalArgumentException.class,
                () -> LlmTetrisAgent.parse("{\"rotation\":\"left\",\"column\":3}"));
    }

    // -------------------------------------------------------------- legality

    @Test
    void anOutOfRangeAnswerIsParsedButRejectedByTheBoard() {
        TetrisBoard board = boardStartingWith(Tetromino.I);

        TetrisMove move = LlmTetrisAgent.parse("{\"rotation\":0,\"column\":9}");

        assertEquals(new TetrisMove(0, 9), move);
        assertFalse(board.isLegal(move), "a 4-wide piece cannot start at column 9");
    }

    @Test
    void aLegalAnswerPassesTheBoardsCheck() throws Exception {
        TetrisBoard board = boardStartingWith(Tetromino.I);

        TetrisMove move = agentReplying("{\"rotation\":0,\"column\":3}").chooseMove(board).get();

        assertTrue(board.isLegal(move));
    }

    @Test
    void aFailedRequestSurfacesAsAFailedFutureRatherThanAnException() {
        TetrisBoard board = boardStartingWith(Tetromino.O);
        LlmTetrisAgent agent = new LlmTetrisAgent(
                (system, user) -> CompletableFuture.failedFuture(new RuntimeException("connection refused")),
                "stub");

        CompletableFuture<TetrisMove> future = agent.chooseMove(board);

        assertTrue(future.isCompletedExceptionally());
        assertThrows(ExecutionException.class, future::get);
    }

    @Test
    void garbageContentFailsTheFutureInsteadOfReturningAWildMove() {
        TetrisBoard board = boardStartingWith(Tetromino.O);

        CompletableFuture<TetrisMove> future = agentReplying("not json at all").chooseMove(board);

        assertTrue(future.isCompletedExceptionally());
    }

    // ----------------------------------------------------------------- prompt

    @Test
    void thePromptIsBuiltSynchronouslyBeforeTheRequestGoesOut() {
        TetrisBoard board = boardStartingWith(Tetromino.T);
        AtomicReference<String> seen = new AtomicReference<>();
        LlmTetrisAgent agent = new LlmTetrisAgent((system, user) -> {
            seen.set(user);
            return CompletableFuture.completedFuture("{\"rotation\":0,\"column\":0}");
        }, "stub");

        agent.chooseMove(board);

        assertTrue(seen.get() != null && !seen.get().isBlank(),
                "the board must be read before the call, not in a callback");
    }

    @Test
    void thePromptDescribesTheBoardPieceAndLegalPlacements() {
        TetrisBoard board = boardStartingWith(Tetromino.I);
        board.setStack("XXX....XXX");

        String prompt = LlmTetrisAgent.describe(board);

        assertTrue(prompt.contains("XXX....XXX"), "the stack must appear verbatim");
        assertTrue(prompt.contains("Current piece: I"));
        assertTrue(prompt.contains("Next piece: "));
        assertTrue(prompt.contains("Legal placements"));
        assertTrue(prompt.contains("Column heights"));
        assertTrue(prompt.contains("leftmost column"), "the column convention must be stated");
    }

    @Test
    void thePromptRendersTheFullBoardHeight() {
        TetrisBoard board = new TetrisBoard(1);

        String prompt = LlmTetrisAgent.describe(board);

        long boardRows = prompt.lines().filter(line -> line.length() == TetrisBoard.WIDTH
                && line.chars().allMatch(c -> c == '.' || c == 'X')).count();
        assertEquals(TetrisBoard.HEIGHT, boardRows);
    }

    @Test
    void thePromptOnlyOffersColumnsThePieceActuallyFits() {
        TetrisBoard board = boardStartingWith(Tetromino.I);

        String prompt = LlmTetrisAgent.describe(board);
        String legalSection = prompt.substring(prompt.indexOf("Legal placements"));

        // Horizontal I (4 wide) can only start at columns 0-6.
        assertTrue(legalSection.contains("0: 0,1,2,3,4,5,6"),
                "rotation 0 columns were: " + legalSection);
    }

    @Test
    void thePromptDrawsEachDistinctRotationOnce() {
        TetrisBoard board = boardStartingWith(Tetromino.I);

        String prompt = LlmTetrisAgent.describe(board);

        assertTrue(prompt.contains("XXXX"), "the flat I must be drawn");
        assertTrue(prompt.contains("X/X/X/X"), "the upright I must be drawn");
        assertEquals(2, prompt.lines().filter(l -> l.contains("rotation ") && l.contains("wide")).count(),
                "I has only two distinct shapes, so only two should be listed");
    }

    // ----------------------------------------------------------------- schema

    @Test
    void theGuidedDecodingSchemaPinsBothFieldsToTheBoardsRange() {
        String schema = LlmTetrisAgent.MOVE_SCHEMA.toString();

        assertTrue(schema.contains("\"required\":[\"rotation\",\"column\"]"));
        assertTrue(schema.contains("\"additionalProperties\":false"));
        assertEquals(Tetromino.ROTATIONS - 1,
                LlmTetrisAgent.MOVE_SCHEMA.path("properties").path("rotation").path("maximum").asInt());
        assertEquals(TetrisBoard.WIDTH - 1,
                LlmTetrisAgent.MOVE_SCHEMA.path("properties").path("column").path("maximum").asInt());
    }

    @Test
    void theLabelIsWhateverTheModelIsCalled() {
        assertEquals("stub", agentReplying("{\"rotation\":0,\"column\":0}").label());
    }
}
