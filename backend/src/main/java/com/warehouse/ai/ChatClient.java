package com.warehouse.ai;

/**
 * Minimal chat-completion abstraction. Implementations must fail fast with
 * {@link AiUnavailableException} so features can fall back to non-AI behaviour.
 */
public interface ChatClient {

    /**
     * Sends a system + user message and returns the model's reply, asking for a JSON object.
     *
     * @throws AiUnavailableException if AI is disabled, cooling down after a failure, or the call fails
     */
    String completeJson(String systemPrompt, String userMessage);

    /** Whether a call is worth attempting right now (enabled and not in failure cooldown). */
    boolean isAvailable();

    boolean isEnabled();

    String modelName();
}
