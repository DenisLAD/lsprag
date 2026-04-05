package com.reasoningtestgen.model;

import java.util.List;

/**
 * Information about a called method found during deep analysis
 */
public record CalledMethodInfo(
    String className,
    String methodName,
    String returnType,
    List<String> parameters,
    boolean isStatic,
    boolean hasSourceCode,
    String sourceCodeSnippet,
    List<String> calledMethods // methods this method calls (for depth tracking)
) {
}
