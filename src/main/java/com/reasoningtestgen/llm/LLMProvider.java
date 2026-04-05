package com.reasoningtestgen.llm;

import org.jetbrains.annotations.NotNull;

/**
 * Abstract interface for LLM providers
 * According to ANALYTICS.md Section 5.3 - Abstraction
 */
public interface LLMProvider {

    /**
     * Send a chat request to the LLM and get response
     * @param prompt User prompt
     * @param systemPrompt System prompt
     * @return LLM response text
     */
    @NotNull
    String chat(@NotNull String prompt, @NotNull String systemPrompt) throws LLMException;

    /**
     * Send a chat request with expected JSON schema
     * @param prompt User prompt
     * @param systemPrompt System prompt
     * @param jsonSchema Expected JSON schema description
     * @return LLM response text (JSON)
     */
    @NotNull
    String chatWithJsonSchema(@NotNull String prompt, 
                               @NotNull String systemPrompt, 
                               @NotNull String jsonSchema) throws LLMException;

    /**
     * Get provider type
     */
    @NotNull
    LLMProviderType getType();

    /**
     * LLM Exception wrapper
     */
    class LLMException extends Exception {
        private final int statusCode;
        private final String response;

        public LLMException(String message, Throwable cause) {
            super(message, cause);
            this.statusCode = -1;
            this.response = null;
        }

        public LLMException(String message, int statusCode, String response) {
            super(message);
            this.statusCode = statusCode;
            this.response = response;
        }

        public int getStatusCode() {
            return statusCode;
        }

        public String getResponse() {
            return response;
        }

        public boolean isRetryable() {
            return statusCode >= 500 || statusCode == -1;
        }
    }
}
