package com.warehouse.ai;

public interface ChatClient {

    String completeJson(String systemPrompt, String userMessage);

    boolean isAvailable();

    boolean isEnabled();

    String modelName();
}
