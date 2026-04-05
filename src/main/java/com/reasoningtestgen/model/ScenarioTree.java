package com.reasoningtestgen.model;

import java.util.List;

/**
 * Scenario tree from Scenario Mapping step
 * According to ANALYTICS.md Section 5.3 - Step 2
 */
public record ScenarioTree(
    ScenarioNode root,
    List<ScenarioNode> children
) {
    public record ScenarioNode(
        String id,
        ScenarioType type,
        String description,
        String inputConditions,
        String expectedOutcome,
        boolean shouldThrow,
        List<ScenarioNode> children
    ) {
    }

    public enum ScenarioType {
        HAPPY, ERROR, BOUNDARY, STATE
    }
}
