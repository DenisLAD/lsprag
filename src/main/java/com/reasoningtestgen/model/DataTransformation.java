package com.reasoningtestgen.model;

import java.util.List;
import java.util.Map;

/**
 * Data transformation information - tracks how input parameters change
 */
public record DataTransformation(
    String parameterName,
    String originalType,
    List<TransformationStep> transformations,
    String finalUsage,
    boolean isModified,
    Map<String, String> intermediateVariables // variable -> transformation description
) {
    /**
     * Single transformation step
     */
    public record TransformationStep(
        int lineNumber,
        String operation, // "assignment", "method_call", "stream_operation", etc.
        String description,
        String resultVariable
    ) {
    }
}
