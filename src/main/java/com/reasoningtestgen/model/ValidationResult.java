package com.reasoningtestgen.model;

import java.util.List;

/**
 * Validation result from Self-Validation step
 * According to ANALYTICS.md Section 5.3 - Step 5
 */
public record ValidationResult(
    boolean isValid,
    List<String> problems,
    String fixedCode
) {
}
