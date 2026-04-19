package com.reasoningtestgen.model;

import java.util.List;

/**
 * Scenario tree from Scenario Mapping step
 * According to ANALYTICS.md Section 5.3 - Step 2
 * 
 * Extended with detailed test case specifications in Given-When-Then format
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
        List<ScenarioNode> children,
        
        // ===== Extended test case specification =====
        TestCaseSpecification testCaseSpec
    ) {
        /**
         * Constructor for backward compatibility (no test case spec)
         */
        public ScenarioNode(String id, ScenarioType type, String description,
                           String inputConditions, String expectedOutcome,
                           boolean shouldThrow, List<ScenarioNode> children) {
            this(id, type, description, inputConditions, expectedOutcome, 
                 shouldThrow, children, null);
        }
    }

    public enum ScenarioType {
        HAPPY,      // Happy path - normal successful execution
        ERROR,      // Error paths - exceptions and error handling
        BOUNDARY,   // Boundary conditions - edge cases, limits
        STATE,      // State transitions - state-dependent behavior
        PERFORMANCE // Performance tests - load, stress, timing
    }
    
    /**
     * Detailed test case specification in Given-When-Then format
     * Provides step-by-step description for test generation
     */
    public record TestCaseSpecification(
        // Test metadata
        String testName,              // Suggested test method name
        String description,           // Detailed test description
        
        // Given-When-Then structure
        GivenClause given,            // Test setup/preconditions
        WhenClause when,              // Action under test
        ThenClause then,              // Expected results/assertions
        
        // Additional test configuration
        List<String> tags,            // Test tags (@Tag, categories)
        String priority,              // Test priority (P0, P1, P2)
        boolean requiresMocking,      // Whether test needs mocks
        boolean isParameterized,      // Whether test is parameterized
        List<ParameterSet> parameterSets // Data for parameterized tests
    ) {
        /**
         * Create specification with minimal required data
         */
        public static TestCaseSpecification minimal(
            String testName, String description,
            GivenClause given, WhenClause when, ThenClause then) {
            
            return new TestCaseSpecification(
                testName, description, given, when, then,
                List.of(), "P1", false, false, List.of()
            );
        }
    }
    
    /**
     * GIVEN clause: Test setup and preconditions
     */
    public record GivenClause(
        List<Fixture> fixtures,           // Test fixtures to create
        List<MockSpecification> mocks,    // Mocks to configure
        List<String> preconditions,       // State preconditions
        List<String> testData             // Test data to prepare
    ) {
        public static GivenClause simple(List<String> preconditions) {
            return new GivenClause(List.of(), List.of(), preconditions, List.of());
        }
        
        public static GivenClause empty() {
            return new GivenClause(List.of(), List.of(), List.of(), List.of());
        }
    }
    
    /**
     * Test fixture - object to create for test setup
     */
    public record Fixture(
        String variableName,
        String className,
        String creationCode,           // Code to create fixture
        List<String> properties,        // Properties to set
        boolean isInjected              // Whether injected by framework
    ) {
        public static Fixture simple(String varName, String className) {
            return new Fixture(varName, className, "new " + className + "()", 
                              List.of(), false);
        }
    }
    
    /**
     * Mock specification for dependencies
     */
    public record MockSpecification(
        String variableName,
        String className,
        boolean isStrict,               // Strict mock (verify all calls)
        List<Stubbing> stubbings,       // Method stubbings
        List<Verification> verifications // Expected verifications
    ) {
        public static MockSpecification simple(String varName, String className) {
            return new MockSpecification(varName, className, false, List.of(), List.of());
        }
    }
    
    /**
     * Method stubbing for mocks
     */
    public record Stubbing(
        String methodName,
        List<String> arguments,         // Argument matchers or values
        String returnValue,             // Value to return
        boolean throwsException,        // Whether to throw
        String exceptionType            // Exception type if throws
    ) {
        public static Stubbing returns(String method, String value) {
            return new Stubbing(method, List.of(), value, false, null);
        }
        
        public static Stubbing throwsEx(String method, String exceptionType) {
            return new Stubbing(method, List.of(), null, true, exceptionType);
        }
    }
    
    /**
     * Expected verification on mocks
     */
    public record Verification(
        String methodName,
        List<String> arguments,
        String times,                   // "once", "never", "atLeast(n)", etc.
        String order                    // Order expectation if any
    ) {
        public static Verification once(String method) {
            return new Verification(method, List.of(), "once", null);
        }
        
        public static Verification never(String method) {
            return new Verification(method, List.of(), "never", null);
        }
    }
    
    /**
     * WHEN clause: Action under test
     */
    public record WhenClause(
        String action,                  // Description of action
        String methodCall,              // Actual method call code
        List<String> arguments,         // Arguments to pass
        boolean expectsException,       // Whether exception expected
        String expectedExceptionType    // Expected exception type
    ) {
        public static WhenClause simple(String methodCall) {
            return new WhenClause("Execute method", methodCall, List.of(), false, null);
        }
        
        public static WhenClause withException(String methodCall, String exceptionType) {
            return new WhenClause("Execute method (expects exception)", 
                                 methodCall, List.of(), true, exceptionType);
        }
    }
    
    /**
     * THEN clause: Expected results and assertions
     */
    public record ThenClause(
        List<Assertion> assertions,     // List of assertions
        String expectedReturnValue,     // Expected return value (if any)
        List<StateChange> stateChanges, // Expected state changes
        List<SideEffect> sideEffects    // Expected side effects
    ) {
        public static ThenClause simple(List<Assertion> assertions) {
            return new ThenClause(assertions, null, List.of(), List.of());
        }
        
        public static ThenClause withReturn(List<Assertion> assertions, String returnValue) {
            return new ThenClause(assertions, returnValue, List.of(), List.of());
        }
    }
    
    /**
     * Assertion specification
     */
    public record Assertion(
        String description,             // What is being asserted
        String actualExpression,        // Code expression for actual value
        String expectedValue,           // Expected value
        AssertionType type,             // Type of assertion
        String customMessage            // Custom assertion message
    ) {
        public static Assertion assertEquals(String desc, String actual, String expected) {
            return new Assertion(desc, actual, expected, AssertionType.EQUALS, null);
        }
        
        public static Assertion assertTrue(String desc, String expression) {
            return new Assertion(desc, expression, "true", AssertionType.TRUE, null);
        }
        
        public static Assertion assertFalse(String desc, String expression) {
            return new Assertion(desc, expression, "false", AssertionType.FALSE, null);
        }
        
        public static Assertion assertNotNull(String desc, String expression) {
            return new Assertion(desc, expression, null, AssertionType.NOT_NULL, null);
        }
        
        public static Assertion assertThrows(String desc, String expression, String exceptionType) {
            return new Assertion(desc, expression, exceptionType, AssertionType.THROWS, null);
        }
    }
    
    public enum AssertionType {
        EQUALS,         // assertEquals(expected, actual)
        NOT_EQUALS,     // assertNotEquals(expected, actual)
        TRUE,           // assertTrue(condition)
        FALSE,          // assertFalse(condition)
        NULL,           // assertNull(value)
        NOT_NULL,       // assertNotNull(value)
        SAME,           // assertSame(expected, actual)
        NOT_SAME,       // assertNotSame(expected, actual)
        THROWS,         // assertThrows(exceptionType, executable)
        CONTAINS,       // assertThat(collection).contains(element)
        EMPTY,          // assertThat(collection).isEmpty()
        NOT_EMPTY,      // assertThat(collection).isNotEmpty()
        HAS_SIZE,       // assertThat(collection).hasSize(n)
        CUSTOM          // Custom assertion
    }
    
    /**
     * Expected state change
     */
    public record StateChange(
        String object,                    // Object whose state changes
        String property,                  // Property that changes
        String expectedValue,             // Expected new value
        String verificationCode           // Code to verify change
    ) {
        public static StateChange simple(String obj, String prop, String value) {
            return new StateChange(obj, prop, value, 
                                  "assertThat(" + obj + "." + prop + "()).isEqualTo(\"" + value + "\")");
        }
    }
    
    /**
     * Expected side effect
     */
    public record SideEffect(
        String type,                      // Type: DATABASE, FILE, NETWORK, etc.
        String description,               // Description of side effect
        String verification               // How to verify
    ) {
        public static SideEffect database(String desc, String verification) {
            return new SideEffect("DATABASE", desc, verification);
        }
        
        public static SideEffect file(String desc, String verification) {
            return new SideEffect("FILE", desc, verification);
        }
        
        public static SideEffect network(String desc, String verification) {
            return new SideEffect("NETWORK", desc, verification);
        }
    }
    
    /**
     * Parameter set for parameterized tests
     */
    public record ParameterSet(
        String name,                      // Name for this parameter set
        List<String> arguments,           // Argument values
        String expectedResult,            // Expected result
        boolean shouldThrow,              // Whether should throw
        String description                // Description of this case
    ) {
        public static ParameterSet simple(String name, List<String> args, String result) {
            return new ParameterSet(name, args, result, false, 
                                   "Test with " + name);
        }
        
        public static ParameterSet withException(String name, List<String> args, String exceptionType) {
            return new ParameterSet(name, args, exceptionType, true,
                                   "Test with " + name + " (expects " + exceptionType + ")");
        }
    }
}
