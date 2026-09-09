package com.fridgegame.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.concurrent.CompletableFuture;

/**
 * The rival's voice: one short line of text reacting to what the player just did.
 *
 * <p>This is the job the model is actually good at. Asking it where to drop a tetromino
 * produced correct answers no player could perceive; asking it to needle someone about
 * putting milk in the freezer produces something readable in the half-second of attention
 * a player has spare mid-game.
 *
 * <p>The reply is capped hard — {@link #MAX_LINE_CHARS} by schema and
 * {@link #MAX_TOKENS} by budget — because a caption that needs a second line has already
 * lost its audience.
 */
public final class LlmCommentator {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_TOKENS = 48;
    private static final int MAX_LINE_CHARS = 90;
    private static final String SCHEMA_NAME = "commentary_line";

    /** The rival's name, shown above its caption. */
    public static final String RIVAL_NAME = "THE FRIDGE CRITIC";

    private static final String SYSTEM_PROMPT = """
            You are a smug rival watching someone play a grocery-sorting game.
            They clear Tetris rows to earn groceries, then drag each one into the fridge
            zone that matches its category. You cannot play; you can only comment.
            Rules:
            - Exactly one sentence, under 90 characters.
            - React to the specific thing described. Be dry and needling, never cruel.
            - Grudging respect when they do well; mock desperation when they beat you.
            - No emoji, no exclamation marks, no advice, no repeating yourself.
            Answer with JSON only.""";

    /** Guided-decoding schema — the reply cannot be anything else. */
    static final JsonNode LINE_SCHEMA = buildSchema();

    private final JsonChat chat;

    public LlmCommentator(JsonChat chat) {
        this.chat = chat;
    }

    /** Wires a commentator onto a live client, binding the line schema. */
    public static LlmCommentator using(LlmClient client) {
        return new LlmCommentator(client.bind(LINE_SCHEMA, SCHEMA_NAME, MAX_TOKENS));
    }

    /**
     * Asks for one line about {@code event}.
     *
     * @param context a compact one-line game state, so the tone can track whether the
     *                player is comfortable or in trouble
     */
    public CompletableFuture<String> react(CommentaryEvent event, String context) {
        return react(event, context, null);
    }

    /**
     * Asks for one line about {@code event}, having just said {@code previousLine}.
     *
     * <p>Each call is a fresh conversation, so the model has no idea what it said thirty
     * seconds ago — left to itself it will answer "You buried a cell. How careless."
     * twice in a row and read as a loop rather than a personality. Feeding the last line
     * back is the cheapest way to make it vary.
     */
    public CompletableFuture<String> react(
            CommentaryEvent event, String context, String previousLine) {

        StringBuilder prompt = new StringBuilder(240);
        prompt.append("Situation: ").append(context).append('\n')
                .append("What just happened: ").append(event.detail()).append('\n');
        if (previousLine != null && !previousLine.isBlank()) {
            prompt.append("You already said: \"").append(previousLine)
                    .append("\" - say something different this time.\n");
        }
        prompt.append("Say one line about it.");
        return chat.send(SYSTEM_PROMPT, prompt.toString()).thenApply(LlmCommentator::parse);
    }

    /** Reads the reply into a caption, trimmed and truncated to fit the card. */
    static String parse(String content) {
        JsonNode node;
        try {
            node = MAPPER.readTree(content);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalArgumentException("Commentary reply was not JSON: " + content, e);
        }
        JsonNode line = node.get("line");
        if (line == null || !line.isTextual() || line.asText().isBlank()) {
            throw new IllegalArgumentException("Commentary reply lacked a line: " + content);
        }
        String text = line.asText().trim();
        // Guided decoding enforces the cap, but a served model behind a proxy might not.
        return text.length() <= MAX_LINE_CHARS ? text : text.substring(0, MAX_LINE_CHARS).trim();
    }

    private static JsonNode buildSchema() {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        schema.putArray("required").add("line");
        schema.putObject("properties")
                .putObject("line")
                .put("type", "string")
                .put("minLength", 1)
                .put("maxLength", MAX_LINE_CHARS);
        return schema;
    }
}
