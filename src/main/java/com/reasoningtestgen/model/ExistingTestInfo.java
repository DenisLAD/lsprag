package com.reasoningtestgen.model;

import java.util.List;

/**
 * Represents an existing test method info
 */
public record ExistingTestInfo(
    String name,
    List<String> assertions,
    String framework,
    List<String> imports
) {
}
