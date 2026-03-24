package ru.sbrf.uddk.ai.testing.lsprag.llm;

import java.io.IOException;

public interface LLMGateway {
    String generate(String prompt) throws IOException, LLMException;

    boolean isAvailable();

    String getModelName();

    class LLMException extends RuntimeException {
        public LLMException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}