package com.reasoningtestgen.llm;

import com.reasoningtestgen.model.PromptEntry.ReasoningStep;
import com.reasoningtestgen.service.PromptHistoryService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Wrapper for LLM provider that logs all prompts and responses
 */
public class LLMProviderWithLogging implements LLMProvider {

    private static final Logger LOG = LoggerFactory.getLogger(LLMProviderWithLogging.class);
    
    private final LLMProvider delegate;
    private final PromptHistoryService promptHistoryService;
    private final String model;

    public LLMProviderWithLogging(@NotNull LLMProvider delegate,
                                   @Nullable PromptHistoryService promptHistoryService,
                                   @NotNull String model) {
        this.delegate = delegate;
        this.promptHistoryService = promptHistoryService;
        this.model = model;
    }

    @NotNull
    @Override
    public String chat(@NotNull String prompt, @NotNull String systemPrompt) throws LLMException {
        long startTime = System.currentTimeMillis();
        
        try {
            String response = delegate.chat(prompt, systemPrompt);
            long responseTime = System.currentTimeMillis() - startTime;
            
            LOG.debug("LLM request completed in {}ms", responseTime);
            
            // Store prompt history
            if (promptHistoryService != null) {
                promptHistoryService.storePrompt(
                    ReasoningStep.CODE_GENERATION, // Will be overridden by caller
                    systemPrompt,
                    prompt,
                    response,
                    model,
                    responseTime,
                    true
                );
            }
            
            return response;
        } catch (LLMException e) {
            long responseTime = System.currentTimeMillis() - startTime;
            LOG.error("LLM request failed after {}ms: {}", responseTime, e.getMessage());
            throw e;
        }
    }

    @NotNull
    @Override
    public String chatWithJsonSchema(@NotNull String prompt,
                                      @NotNull String systemPrompt,
                                      @NotNull String jsonSchema) throws LLMException {
        return delegate.chatWithJsonSchema(prompt, systemPrompt, jsonSchema);
    }

    @NotNull
    @Override
    public LLMProviderType getType() {
        return delegate.getType();
    }

    /**
     * Store prompt with specific step (convenience method)
     */
    public void storePrompt(@NotNull ReasoningStep step,
                           @NotNull String systemPrompt,
                           @NotNull String userPrompt,
                           @NotNull String response,
                           long responseTimeMs,
                           boolean success) {
        if (promptHistoryService != null) {
            promptHistoryService.storePrompt(
                step,
                systemPrompt,
                userPrompt,
                response,
                model,
                responseTimeMs,
                success
            );
        }
    }
}
