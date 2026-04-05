package com.reasoningtestgen.model;

import java.util.List;
import java.util.Map;

/**
 * Represents documentation contract extracted from JavaDoc
 */
public record DocContract(
    Map<String, String> params,
    String returns,
    List<String> throwsList,
    List<String> businessRules
) {
}
