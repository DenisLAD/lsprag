package com.reasoningtestgen.model;

import java.time.LocalDateTime;

/**
 * Represents a prompt entry in history for analysis
 */
public record PromptEntry(
    String id,
    LocalDateTime timestamp,
    ReasoningStep step,
    String systemPrompt,
    String userPrompt,
    String llmResponse,
    String model,
    long responseTimeMs,
    boolean success
) {
    public enum ReasoningStep {
        INTENT_ANALYSIS,
        SCENARIO_MAPPING,
        TEST_DESIGN,
        CODE_GENERATION,
        SELF_VALIDATION,
        CODE_REFINEMENT
    }
}
