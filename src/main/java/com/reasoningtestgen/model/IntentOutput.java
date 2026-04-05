package com.reasoningtestgen.model;

import java.util.List;

/**
 * Output from Intent & Contract Analysis step
 * According to ANALYTICS.md Section 5.3 - Step 1
 */
public record IntentOutput(
    String goal,
    List<String> preconditions,
    List<String> postconditions,
    List<String> sideEffects,
    List<String> exceptions
) {
}
