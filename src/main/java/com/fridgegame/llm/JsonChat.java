package com.fridgegame.llm;

import java.util.concurrent.CompletableFuture;

/**
 * One schema-constrained chat round trip.
 *
 * <p>The seam between the agent and the network. {@link LlmClient} supplies the real
 * implementation; tests supply a lambda returning canned JSON, so the agent's parsing and
 * validation can be exercised without ever opening a socket.
 */
@FunctionalInterface
public interface JsonChat {

    /**
     * Sends one prompt pair and yields the assistant's reply.
     *
     * @return a future of the raw {@code content} string, which guided decoding constrains
     *         to the requested schema. Completes exceptionally on transport failure, a
     *         non-200 status, or an empty reply.
     */
    CompletableFuture<String> send(String systemPrompt, String userPrompt);
}
