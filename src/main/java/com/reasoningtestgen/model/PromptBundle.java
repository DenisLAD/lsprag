package com.reasoningtestgen.model;

import java.util.List;

/**
 * Prompt bundle for LLM
 * According to ANALYTICS.md Section 5.2
 */
public record PromptBundle(
    String systemPrompt,
    String userPrompt,
    List<String> examples
) {
}
