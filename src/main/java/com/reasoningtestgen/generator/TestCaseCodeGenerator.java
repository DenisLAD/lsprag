package com.reasoningtestgen.generator;

import com.reasoningtestgen.model.ScenarioTree.*;
import com.reasoningtestgen.model.TestDesign;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Generates unit test code from TestCaseSpecification
 * Converts Given-When-Then specifications into actual test code
 */
public class TestCaseCodeGenerator {

    private final TestDesign testDesign;
    private final String className;
    private final String methodName;

    public TestCaseCodeGenerator(@NotNull TestDesign testDesign,
                                  @NotNull String className,
                                  @NotNull String methodName) {
        this.testDesign = testDesign;
        this.className = className;
        this.methodName = methodName;
    }

    /**
     * Generate complete test method code from specification
     */
    @NotNull
    public String generateTestMethod(@NotNull TestCaseSpecification spec) {
        StringBuilder code = new StringBuilder();

        // Add test annotation
        code.append("    @Test\n");

        // Add test method name
        code.append("    void ").append(spec.testName()).append("() {\n");

        // Generate GIVEN section (setup)
        code.append(generateGivenSection(spec.given()));
        code.append("\n");

        // Generate WHEN section (action)
        code.append(generateWhenSection(spec.when()));
        code.append("\n");

        // Generate THEN section (assertions)
        code.append(generateThenSection(spec.then()));

        // Close method
        code.append("    }\n");

        return code.toString();
    }

    /**
     * Generate @ParameterizedTest with multiple parameter sets
     */
    @NotNull
    public String generateParameterizedTest(@NotNull TestCaseSpecification spec) {
        StringBuilder code = new StringBuilder();

        // Add parameterized test annotation
        code.append("    @ParameterizedTest(name = \"{index}: {0}\")\n");
        
        // Add method source for parameters
        code.append("    @MethodSource(\"").append(spec.testName()).append("Params\")\n");
        
        // Generate method signature with parameters
        code.append("    void ").append(spec.testName())
            .append("(String testName, ");
        
        // Extract parameter types from first parameter set
        if (!spec.parameterSets().isEmpty() && !spec.parameterSets().get(0).arguments().isEmpty()) {
            // Simplified - assumes all args are Object type
            code.append("Object... args");
        }
        
        code.append(") {\n");

        // Generate GIVEN section
        code.append(generateGivenSection(spec.given()));
        code.append("\n");

        // Generate WHEN section with parameters
        code.append(generateParameterizedWhenSection(spec.when(), spec.parameterSets()));
        code.append("\n");

        // Generate THEN section
        code.append(generateThenSection(spec.then()));

        // Close method
        code.append("    }\n");

        // Generate parameter provider method
        code.append("\n").append(generateParameterProvider(spec));

        return code.toString();
    }

    /**
     * Generate GIVEN section: fixtures, mocks, preconditions
     */
    @NotNull
    private String generateGivenSection(@NotNull GivenClause given) {
        StringBuilder code = new StringBuilder();
        code.append("        // Given\n");

        // Create fixtures
        for (Fixture fixture : given.fixtures()) {
            code.append("        ")
                .append(fixture.className())
                .append(" ")
                .append(fixture.variableName())
                .append(" = ")
                .append(fixture.creationCode())
                .append(";\n");

            // Set properties if any
            for (String property : fixture.properties()) {
                // Simplified property setting
                code.append("        // Set property: ").append(property).append("\n");
            }
        }

        // Create mocks
        for (MockSpecification mock : given.mocks()) {
            code.append("        ")
                .append(mock.className())
                .append(" ")
                .append(mock.variableName())
                .append(" = mock(")
                .append(mock.className())
                .append(".class);\n");

            // Apply stubbings
            for (Stubbing stubbing : mock.stubbings()) {
                code.append(generateStubbing(mock.variableName(), stubbing));
            }
        }

        // Add preconditions as comments
        for (String precondition : given.preconditions()) {
            code.append("        // Preconditions: ").append(precondition).append("\n");
        }

        // Add test data setup
        for (String data : given.testData()) {
            code.append("        // Test data: ").append(data).append("\n");
        }

        return code.toString();
    }

    /**
     * Generate stubbing code for mock
     */
    @NotNull
    private String generateStubbing(@NotNull String mockName, @NotNull Stubbing stubbing) {
        StringBuilder code = new StringBuilder();

        if (stubbing.throwsException()) {
            code.append("        when(")
                .append(mockName)
                .append(".")
                .append(stubbing.methodName())
                .append("(")
                .append(String.join(", ", stubbing.arguments()))
                .append("))")
                .append(".thenThrow(new ")
                .append(stubbing.exceptionType())
                .append("());\n");
        } else {
            code.append("        when(")
                .append(mockName)
                .append(".")
                .append(stubbing.methodName())
                .append("(")
                .append(String.join(", ", stubbing.arguments()))
                .append("))")
                .append(".thenReturn(")
                .append(stubbing.returnValue())
                .append(");\n");
        }

        return code.toString();
    }

    /**
     * Generate WHEN section: action under test
     */
    @NotNull
    private String generateWhenSection(@NotNull WhenClause when) {
        StringBuilder code = new StringBuilder();
        code.append("        // When\n");

        if (when.expectsException()) {
            // Exception expected - use assertThrows
            code.append("        ")
                .append(when.expectedExceptionType())
                .append(" exception = assertThrows(")
                .append(when.expectedExceptionType())
                .append(".class, () -> ")
                .append(when.methodCall())
                .append(");\n");
        } else {
            // Normal execution - capture result
            code.append("        var result = ")
                .append(when.methodCall())
                .append(";\n");
        }

        return code.toString();
    }

    /**
     * Generate WHEN section for parameterized test
     */
    @NotNull
    private String generateParameterizedWhenSection(@NotNull WhenClause when,
                                                     @NotNull List<ParameterSet> parameterSets) {
        StringBuilder code = new StringBuilder();
        code.append("        // When\n");

        if (when.expectsException()) {
            code.append("        // Exception test with parameters\n");
            code.append("        assertThrows(")
                .append(when.expectedExceptionType())
                .append(".class, () -> ")
                .append(when.methodCall())
                .append(");\n");
        } else {
            code.append("        var result = ")
                .append(when.methodCall())
                .append(";\n");
        }

        return code.toString();
    }

    /**
     * Generate THEN section: assertions
     */
    @NotNull
    private String generateThenSection(@NotNull ThenClause then) {
        StringBuilder code = new StringBuilder();
        code.append("        // Then\n");

        // Generate assertions
        for (Assertion assertion : then.assertions()) {
            code.append(generateAssertion(assertion));
        }

        // Verify state changes
        for (StateChange change : then.stateChanges()) {
            code.append("        // Verify state change: ")
                .append(change.verificationCode())
                .append("\n");
        }

        // Verify side effects
        for (SideEffect effect : then.sideEffects()) {
            code.append("        // Verify side effect: ")
                .append(effect.verification())
                .append("\n");
        }

        // Verify mock interactions
        return code.toString();
    }

    /**
     * Generate assertion code
     */
    @NotNull
    private String generateAssertion(@NotNull Assertion assertion) {
        StringBuilder code = new StringBuilder();

        if (assertion.customMessage() != null && !assertion.customMessage().isEmpty()) {
            code.append("        // ").append(assertion.customMessage()).append("\n");
        }

        switch (assertion.type()) {
            case EQUALS:
                code.append("        assertThat(")
                    .append(assertion.actualExpression())
                    .append(")")
                    .append(".isEqualTo(")
                    .append(assertion.expectedValue())
                    .append(");\n");
                break;

            case NOT_EQUALS:
                code.append("        assertThat(")
                    .append(assertion.actualExpression())
                    .append(")")
                    .append(".isNotEqualTo(")
                    .append(assertion.expectedValue())
                    .append(");\n");
                break;

            case TRUE:
                code.append("        assertThat(")
                    .append(assertion.actualExpression())
                    .append(")")
                    .append(".isTrue();\n");
                break;

            case FALSE:
                code.append("        assertThat(")
                    .append(assertion.actualExpression())
                    .append(")")
                    .append(".isFalse();\n");
                break;

            case NULL:
                code.append("        assertThat(")
                    .append(assertion.actualExpression())
                    .append(")")
                    .append(".isNull();\n");
                break;

            case NOT_NULL:
                code.append("        assertThat(")
                    .append(assertion.actualExpression())
                    .append(")")
                    .append(".isNotNull();\n");
                break;

            case THROWS:
                // Already handled in WHEN section
                break;

            case CONTAINS:
                code.append("        assertThat(")
                    .append(assertion.actualExpression())
                    .append(")")
                    .append(".contains(")
                    .append(assertion.expectedValue())
                    .append(");\n");
                break;

            case EMPTY:
                code.append("        assertThat(")
                    .append(assertion.actualExpression())
                    .append(")")
                    .append(".isEmpty();\n");
                break;

            case NOT_EMPTY:
                code.append("        assertThat(")
                    .append(assertion.actualExpression())
                    .append(")")
                    .append(".isNotEmpty();\n");
                break;

            case HAS_SIZE:
                code.append("        assertThat(")
                    .append(assertion.actualExpression())
                    .append(")")
                    .append(".hasSize(")
                    .append(assertion.expectedValue())
                    .append(");\n");
                break;

            default:
                code.append("        // Custom assertion: ")
                    .append(assertion.description())
                    .append("\n");
        }

        return code.toString();
    }

    /**
     * Generate parameter provider method for parameterized tests
     */
    @NotNull
    private String generateParameterProvider(@NotNull TestCaseSpecification spec) {
        StringBuilder code = new StringBuilder();
        code.append("    static Stream<Arguments> ").append(spec.testName()).append("Params() {\n");
        code.append("        return Stream.of(\n");

        List<String> arguments = new ArrayList<>();
        for (ParameterSet paramSet : spec.parameterSets()) {
            StringBuilder args = new StringBuilder();
            args.append("            Arguments.of(");
            args.append("\"").append(paramSet.name()).append("\", ");
            args.append(String.join(", ", paramSet.arguments()));
            args.append(") // ").append(paramSet.description());
            arguments.add(args.toString());
        }

        code.append(String.join(",\n", arguments));
        code.append("\n        );\n");
        code.append("    }\n");

        return code.toString();
    }

    /**
     * Generate mock verification code
     */
    @NotNull
    public String generateMockVerifications(@NotNull MockSpecification mock) {
        StringBuilder code = new StringBuilder();

        for (Verification verification : mock.verifications()) {
            code.append("        verify(")
                .append(mock.variableName())
                .append(", ")
                .append(getVerificationMode(verification.times()))
                .append(").")
                .append(verification.methodName())
                .append("(")
                .append(String.join(", ", verification.arguments()))
                .append(");\n");
        }

        return code.toString();
    }

    @NotNull
    private String getVerificationMode(@NotNull String times) {
        return switch (times.toLowerCase()) {
            case "once" -> "times(1)";
            case "never" -> "never()";
            case "atleast" -> "atLeast(1)";
            case "atmost" -> "atMost(1)";
            default -> "times(" + times + ")";
        };
    }

    /**
     * Generate test class imports based on specifications
     */
    @NotNull
    public List<String> generateRequiredImports(@NotNull List<TestCaseSpecification> specifications) {
        List<String> imports = new ArrayList<>();

        // Core test imports
        imports.add("import org.junit.jupiter.api.Test;");
        imports.add("import org.junit.jupiter.api.BeforeEach;");
        imports.add("import org.junit.jupiter.api.AfterEach;");

        // Mockito imports
        imports.add("import org.mockito.Mock;");
        imports.add("import org.mockito.MockitoAnnotations;");
        imports.add("import static org.mockito.Mockito.*;");

        // AssertJ imports
        imports.add("import static org.assertj.core.api.Assertions.*;");
        imports.add("import org.assertj.core.api.Assertions;");

        // Check for parameterized tests
        boolean hasParameterized = specifications.stream()
            .anyMatch(TestCaseSpecification::isParameterized);

        if (hasParameterized) {
            imports.add("import org.junit.jupiter.params.ParameterizedTest;");
            imports.add("import org.junit.jupiter.params.provider.MethodSource;");
            imports.add("import org.junit.jupiter.params.provider.Arguments;");
            imports.add("import java.util.stream.Stream;");
        }

        // Add imports for used exception types
        specifications.stream()
            .flatMap(spec -> spec.parameterSets().stream())
            .filter(ParameterSet::shouldThrow)
            .map(ParameterSet::expectedResult)
            .distinct()
            .forEach(exceptionType -> {
                if (!exceptionType.startsWith("java.lang.")) {
                    imports.add("import " + exceptionType + ";");
                }
            });

        return imports;
    }
}
