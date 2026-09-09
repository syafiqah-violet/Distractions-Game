package com.fridgegame.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * Guards the request shape. No network: these assert what we <i>send</i>, which is where
 * every problem with this endpoint has actually been.
 */
class LlmClientTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static LlmClient client() {
        return new LlmClient(
                new LlmConfig("http://box:8001", "test-model", null), Duration.ofSeconds(3));
    }

    private static JsonNode bodyOf(LlmClient client) throws Exception {
        return MAPPER.readTree(client.body(
                "sys", "user", LlmCommentator.LINE_SCHEMA, "commentary_line", 48));
    }

    @Test
    void talksHttp11BecauseVllmDropsTheBodyOnAnH2cUpgrade() {
        // Java defaults to HTTP/2; over plaintext that means an h2c upgrade attempt, and
        // uvicorn answers "400 Field required: body". This must not regress.
        assertEquals(HttpClient.Version.HTTP_1_1, client().httpVersion());
    }

    @Test
    void disablesThinkingSoTheModelActuallyAnswers() throws Exception {
        JsonNode body = bodyOf(client());

        assertTrue(body.has("chat_template_kwargs"),
                "without this the model returns content:null and reasons until it runs out of tokens");
        assertFalse(body.path("chat_template_kwargs").path("enable_thinking").asBoolean(true));
    }

    @Test
    void requestsGuidedDecodingAgainstTheSuppliedSchema() throws Exception {
        JsonNode format = bodyOf(client()).path("response_format");

        assertEquals("json_schema", format.path("type").asText());
        assertEquals("commentary_line", format.path("json_schema").path("name").asText());
        assertTrue(format.path("json_schema").path("strict").asBoolean());
        assertEquals(LlmCommentator.LINE_SCHEMA, format.path("json_schema").path("schema"));
    }

    @Test
    void carriesTheConfiguredModelAndTokenBudget() throws Exception {
        JsonNode body = bodyOf(client());

        assertEquals("test-model", body.path("model").asText());
        assertEquals(48, body.path("max_tokens").asInt());
    }

    @Test
    void sendsTheSystemAndUserPromptsInOrder() throws Exception {
        JsonNode messages = bodyOf(client()).path("messages");

        assertEquals(2, messages.size());
        assertEquals("system", messages.path(0).path("role").asText());
        assertEquals("sys", messages.path(0).path("content").asText());
        assertEquals("user", messages.path(1).path("role").asText());
        assertEquals("user", messages.path(1).path("content").asText());
    }

    @Test
    void usesALowTemperatureSoRepliesAreStable() throws Exception {
        assertTrue(bodyOf(client()).path("temperature").asDouble() <= 0.3);
    }

    @Test
    void promptsWithAwkwardCharactersAreEscapedNotConcatenated() throws Exception {
        String nasty = "line1\n\"quoted\" \\ backslash\ttab";

        JsonNode body = MAPPER.readTree(client().body(
                nasty, nasty, LlmCommentator.LINE_SCHEMA, "commentary_line", 48));

        assertEquals(nasty, body.path("messages").path(0).path("content").asText(),
                "round-tripping a prompt with quotes and newlines must be lossless");
    }

    @Test
    void anUnreachableEndpointReportsOfflineRatherThanFailing() throws Exception {
        // Port 9 is the discard port: connection is refused fast and deterministically.
        LlmClient offline = new LlmClient(
                new LlmConfig("http://127.0.0.1:9", "test-model", null), Duration.ofMillis(500));

        LlmClient.Reachability probe = offline.reachable().get();

        assertFalse(probe.online(), "offline is an expected state, not an error");
        assertFalse(probe.detail().isBlank(), "the reason must survive for the UI to show");
    }

    @Test
    void theOfflineReasonIsPlainEnglishNotAnExceptionName() throws Exception {
        LlmClient offline = new LlmClient(
                new LlmConfig("http://127.0.0.1:9", "test-model", null), Duration.ofMillis(500));

        String detail = offline.reachable().get().detail();

        assertEquals("endpoint unreachable", detail,
                "a refused connection surfaces as ClosedChannelException or ConnectException "
                        + "depending on platform; neither belongs in a caption");
    }

    @Test
    void anUnresolvableHostIsReportedAsSuch() throws Exception {
        LlmClient offline = new LlmClient(
                new LlmConfig("http://no-such-host.invalid:8001", "test-model", null),
                Duration.ofSeconds(2));

        assertEquals("unknown host", offline.reachable().get().detail());
    }
}
