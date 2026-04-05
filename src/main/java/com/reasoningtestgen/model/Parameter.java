package com.reasoningtestgen.model;

/**
 * Represents a method parameter in the MethodContext
 */
public record Parameter(
    String name,
    String type,
    boolean nullable
) {
}
