package com.reasoningtestgen.model;

/**
 * Represents a dependency in the MethodContext
 */
public record Dependency(
    String name,
    String type,
    boolean isExternal,
    boolean nullable
) {
}
