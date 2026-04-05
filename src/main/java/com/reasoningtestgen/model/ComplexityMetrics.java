package com.reasoningtestgen.model;

/**
 * Represents complexity metrics for a method
 */
public record ComplexityMetrics(
    int cyclomatic,
    int nestingDepth,
    int branchCount,
    int loopCount
) {
}
