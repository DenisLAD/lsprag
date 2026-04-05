package com.reasoningtestgen.model;

/**
 * Test design from Test Design step
 * According to ANALYTICS.md Section 5.3 - Step 3
 */
public record TestDesign(
    TestFramework framework,
    String namingConvention,
    MockingStrategy mockingStrategy,
    boolean useParameterized,
    AssertionLibrary assertionLibrary
) {
    public enum TestFramework {
        JUNIT5, JUNIT4, TESTNG
    }

    public enum MockingStrategy {
        NONE, MOCKITO_EXTEND_WITH, MOCKITO_RUNNER, POWERMOCK
    }

    public enum AssertionLibrary {
        JUNIT, ASSERTJ, HAMCREST, TRUTH
    }
}
