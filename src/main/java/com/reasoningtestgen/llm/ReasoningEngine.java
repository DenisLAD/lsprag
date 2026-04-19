package com.reasoningtestgen.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellij.openapi.project.Project;
import com.reasoningtestgen.builder.ContextBuilder;
import com.reasoningtestgen.generator.TestCaseCodeGenerator;
import com.reasoningtestgen.model.*;
import com.reasoningtestgen.model.PromptEntry.ReasoningStep;
import com.reasoningtestgen.refiner.SelfCorrectionEngine;
import com.reasoningtestgen.service.PromptHistoryService;
import com.reasoningtestgen.settings.PluginSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Multi-step LLM Reasoning Engine
 * Implements the reasoning pipeline: Intent → Scenarios → Design → Code → Validation
 * According to ANALYTICS.md Section 5.3
 */
public class ReasoningEngine {

    private static final Logger LOG = LoggerFactory.getLogger(ReasoningEngine.class);
    private static final int MAX_SELF_CORRECTION_ATTEMPTS = 3;

    private final LLMProvider llmProvider;
    private final ContextBuilder contextBuilder;
    private final ObjectMapper objectMapper;
    private final PluginSettings settings;
    private final PromptHistoryService promptHistoryService;
    @Nullable
    private final Project project;
    @Nullable
    private SelfCorrectionEngine selfCorrectionEngine;

    public ReasoningEngine(@NotNull PluginSettings settings, 
                            @Nullable PromptHistoryService promptHistoryService,
                            @Nullable Project project) {
        this.settings = settings;
        this.llmProvider = LLMProviderFactory.createProvider(settings);
        this.contextBuilder = new ContextBuilder();
        this.objectMapper = new ObjectMapper();
        this.promptHistoryService = promptHistoryService;
        this.project = project;
        
        // Initialize self-correction if project is available
        if (project != null) {
            this.selfCorrectionEngine = new SelfCorrectionEngine(
                llmProvider,
                project,
                settings,
                promptHistoryService
            );
        }
    }
    
    public ReasoningEngine(@NotNull PluginSettings settings) {
        this(settings, null, null);
    }

    /**
     * Execute full reasoning pipeline
     * @param methodContext Extracted method context
     * @return Generated test code
     */
    @NotNull
    public GeneratedCode generateTests(@NotNull MethodContext methodContext) throws LLMProvider.LLMException {
        LOG.info("Starting reasoning pipeline for method: {}.{}", 
            methodContext.className(), methodContext.methodName());

        // Step 1: Intent & Contract Analysis
        LOG.info("Step 1: Intent & Contract Analysis");
        IntentOutput intentOutput = analyzeIntent(methodContext);

        // Step 2: Scenario Mapping
        LOG.info("Step 2: Scenario Mapping");
        ScenarioTree scenarioTree = generateScenarios(methodContext, intentOutput);

        // Step 3: Test Design
        LOG.info("Step 3: Test Design");
        TestDesign testDesign = designTests(methodContext, scenarioTree);

        // Step 4: Code Generation with self-correction
        LOG.info("Step 4: Code Generation");
        GeneratedCode generatedCode = generateWithSelfCorrection(testDesign, scenarioTree);

        LOG.info("Reasoning pipeline completed successfully");
        return generatedCode;
    }

    /**
     * Step 1: Intent & Contract Analysis
     * Public method for use in UI dialog
     */
    @NotNull
    public IntentOutput analyzeIntent(@NotNull MethodContext context) throws LLMProvider.LLMException {
        String systemPrompt = """
            You are a Senior Software Architect analyzing a Java method to extract its complete intent and contract.

            CRITICAL REQUIREMENTS:
            - Be SPECIFIC and DETAILED - avoid generic statements
            - Analyze ACTUAL business logic, not just "method processes data"
            - Identify ALL preconditions from parameters and state
            - List ALL postconditions including return value semantics
            - Document ALL side effects (DB, I/O, state changes, network calls)
            - Specify ALL exceptions with their triggers

            OUTPUT FORMAT:
            Respond with JSON containing:
            - goal: Specific business purpose (2-3 sentences, not generic)
            - preconditions: List ALL conditions that must be true (at least 2-3)
            - postconditions: List ALL guarantees after execution (at least 2-3)
            - sideEffects: ALL side effects (state changes, I/O, DB, network, etc.)
            - exceptions: ALL exception types with specific trigger conditions

            EXAMPLE (for UserService.createUser):
            {
              "goal": "Creates a new user account with validation: checks username uniqueness, validates email format, encrypts password, and persists to database. Returns user DTO with generated ID.",
              "preconditions": [
                "Database connection is available",
                "Username must not be null or empty",
                "Email must be valid format",
                "Password must meet complexity requirements"
              ],
              "postconditions": [
                "New user record exists in database with unique ID",
                "Password is encrypted using BCrypt",
                "Username is unique (no duplicates exist)",
                "User DTO returned with all fields populated"
              ],
              "sideEffects": [
                "INSERT into users table",
                "Email notification sent to user",
                "Audit log entry created",
                "Cache invalidated for user list"
              ],
              "exceptions": [
                "IllegalArgumentException when username is null/empty",
                "IllegalArgumentException when email format is invalid",
                "DuplicateKeyException when username already exists",
                "DataAccessException when database operation fails"
              ]
            }
            """;

        String userPrompt = String.format("""
            Analyze the following method and extract its COMPLETE intent and contract:

            ## Method Signature
            Class: %s
            Method: %s(%s)
            Return Type: %s
            Annotations: %s

            ## Source Code
            ```java
            %s
            ```

            ## Control Flow Graph
            %s

            ## Documentation Contract
            - Parameters: %s
            - Returns: %s
            - Throws: %s
            - Business Rules: %s

            IMPORTANT:
            - Analyze the ACTUAL code implementation, not just the signature
            - Look at IF conditions, loops, exceptions to understand behavior
            - Identify business rules from variable names and logic
            - Consider Spring annotations (@Transactional, @Cacheable, etc.)
            - Check for validation, null checks, error handling

            Respond with COMPLETE JSON (use the example format from system prompt):
            {
              "goal": "...",
              "preconditions": [...],
              "postconditions": [...],
              "sideEffects": [...],
              "exceptions": [...]
            }
            """,
            context.className(),
            context.methodName(),
            formatParams(context.parameters()),
            context.returnType(),
            String.join(", ", context.annotations()),
            context.sourceCode() != null ? context.sourceCode() : "Not available",
            formatCFG(context.controlFlow().nodes()),
            context.docContract() != null ? context.docContract().params() : "{}",
            context.docContract() != null ? context.docContract().returns() : "not specified",
            context.docContract() != null ? context.docContract().throwsList() : "[]",
            context.docContract() != null ? context.docContract().businessRules() : "[]"
        );

        String response = llmProvider.chat(userPrompt, systemPrompt);
        LOG.debug("Intent analysis response received");

        try {
            return objectMapper.readValue(response, IntentOutput.class);
        } catch (JsonProcessingException e) {
            LOG.warn("Failed to parse intent output, using fallback", e);
            return createFallbackIntent(context);
        }
    }

    /**
     * Step 2: Scenario Mapping
     * Generates detailed test scenarios with Given-When-Then specifications
     * Public method for use in UI dialog
     */
    @NotNull
    public ScenarioTree generateScenarios(@NotNull MethodContext context,
                                           @NotNull IntentOutput intentOutput) throws LLMProvider.LLMException {
        String systemPrompt = """
            You are a QA Architect designing comprehensive test scenarios for a Java method.

            CRITICAL REQUIREMENTS:
            - Create ONE TestCaseSpecification for EACH branch in the Control Flow Graph
            - Each specification must be COMPLETE with Given-When-Then details
            - Be SPECIFIC with actual values, not generic descriptions
            - Include ALL mocks with their stubbings
            - Include ALL assertions with expected values

            EACH TestCaseSpecification MUST include:
            - testName: should_{expectedResult}_when_{condition} format
            - description: What is being tested (in Russian for @DisplayName)
            - given: Complete setup with fixtures, mocks, stubbings, preconditions
            - when: Exact method call with arguments
            - then: All assertions with specific expected values

            EXAMPLE (for UserService.createUser with if/else):

            {
              "root": {"id": "S0", "description": "All scenarios", "testCaseSpec": null},
              "children": [
                {
                  "id": "S1",
                  "type": "HAPPY",
                  "description": "Happy path - user created successfully",
                  "inputConditions": "Valid DTO with unique username",
                  "expectedOutcome": "User created and saved to database",
                  "shouldThrow": false,
                  "testCaseSpec": {
                    "testName": "should_createUser_when_dtoValid_and_usernameUnique",
                    "description": "Создает пользователя когда DTO валиден и username уникален",
                    "given": {
                      "fixtures": [
                        {
                          "variableName": "userDto",
                          "className": "UserDTO",
                          "creationCode": "UserDTO.builder().username(\"newuser\").email(\"test@example.com\").password(\"Pass123!\").build()"
                        }
                      ],
                      "mocks": [
                        {
                          "variableName": "userRepository",
                          "className": "UserRepository",
                          "stubbings": [
                            {"method": "existsByUsername", "args": ["newuser"], "returns": false}
                          ]
                        },
                        {
                          "variableName": "passwordEncoder",
                          "className": "PasswordEncoder",
                          "stubbings": [
                            {"method": "encode", "args": ["Pass123!"], "returns": "$2a$10$encoded"}
                          ]
                        }
                      ],
                      "preconditions": ["Database connection available", "Username 'newuser' does not exist"],
                      "testData": ["Valid UserDTO with unique username"]
                    },
                    "when": {
                      "action": "Create user with valid DTO",
                      "methodCall": "userService.createUser(userDto)",
                      "arguments": ["userDto"],
                      "expectsException": false,
                      "expectedExceptionType": null
                    },
                    "then": {
                      "assertions": [
                        {
                          "description": "Returned DTO is not null",
                          "actualExpression": "result",
                          "expectedValue": "not null",
                          "type": "NOT_NULL"
                        },
                        {
                          "description": "Returned DTO has correct username",
                          "actualExpression": "result.getUsername()",
                          "expectedValue": "newuser",
                          "type": "EQUALS"
                        },
                        {
                          "description": "Password is encrypted",
                          "actualExpression": "result.getPassword()",
                          "expectedValue": "$2a$10$...",
                          "type": "STARTS_WITH"
                        }
                      ],
                      "expectedReturnValue": "UserDTO with id, username, email",
                      "stateChanges": ["User record inserted into database"],
                      "sideEffects": ["Email sent to user"]
                    }
                  }
                },
                {
                  "id": "S2",
                  "type": "ERROR",
                  "description": "Error path - username already exists",
                  "inputConditions": "DTO with existing username",
                  "expectedOutcome": "IllegalArgumentException thrown",
                  "shouldThrow": true,
                  "testCaseSpec": {
                    "testName": "should_throwException_when_usernameExists",
                    "description": "Выбрасывает исключение когда username существует",
                    "given": {
                      "fixtures": [
                        {
                          "variableName": "existingUserDto",
                          "className": "UserDTO",
                          "creationCode": "UserDTO.builder().username(\"existing\").email(\"test@example.com\").build()"
                        }
                      ],
                      "mocks": [
                        {
                          "variableName": "userRepository",
                          "className": "UserRepository",
                          "stubbings": [
                            {"method": "existsByUsername", "args": ["existing"], "returns": true}
                          ]
                        }
                      ],
                      "preconditions": ["Username 'existing' already exists in database"],
                      "testData": ["DTO with duplicate username"]
                    },
                    "when": {
                      "action": "Try to create user with existing username",
                      "methodCall": "userService.createUser(existingUserDto)",
                      "arguments": ["existingUserDto"],
                      "expectsException": true,
                      "expectedExceptionType": "IllegalArgumentException"
                    },
                    "then": {
                      "assertions": [
                        {
                          "description": "Exception is thrown",
                          "actualExpression": "thrown exception",
                          "expectedValue": "IllegalArgumentException",
                          "type": "IS_INSTANCE_OF"
                        },
                        {
                          "description": "Exception message contains username",
                          "actualExpression": "exception.getMessage()",
                          "expectedValue": "Username already exists",
                          "type": "CONTAINS"
                        }
                      ],
                      "expectedReturnValue": null,
                      "stateChanges": [],
                      "sideEffects": []
                    }
                  }
                }
              ]
            }
            """;

        String userPrompt = String.format("""
            Generate detailed test scenarios with Given-When-Then specifications for method %s.%s

            ## Method Information
            Class: %s
            Method: %s(%s)
            Return Type: %s
            Annotations: %s

            ## Control Flow Graph
            %s

            ## Intent Analysis
            - Goal: %s
            - Preconditions: %s
            - Postconditions: %s
            - Exceptions: %s

            ## Complexity Metrics
            - Cyclomatic Complexity: %d
            - Branch Count: %d
            - Loop Count: %d

            ## Dependencies
            %s

            ## Documentation Contract
            - Parameters: %s
            - Returns: %s
            - Throws: %s
            - Business Rules: %s

            Create a scenario tree where EACH scenario has a complete testCaseSpec with:
            - testName, description
            - given: { fixtures, mocks, preconditions, testData }
            - when: { action, methodCall, arguments, expectsException, expectedExceptionType }
            - then: { assertions[], expectedReturnValue, stateChanges, sideEffects }

            Respond with valid JSON matching this extended schema:
            {
              "root": {"id": "S0", "description": "All scenarios", "testCaseSpec": null},
              "children": [
                {
                  "id": "S1",
                  "type": "HAPPY|ERROR|BOUNDARY|STATE|PERFORMANCE",
                  "description": "string",
                  "inputConditions": "string",
                  "expectedOutcome": "string",
                  "shouldThrow": boolean,
                  "children": [],
                  "testCaseSpec": {
                    "testName": "should_expectedResult_when_condition",
                    "description": "Detailed description",
                    "given": {
                      "fixtures": [{"variableName": "var", "className": "Type", "creationCode": "..."}],
                      "mocks": [{"variableName": "mock", "className": "Type", "stubbings": [...]}],
                      "preconditions": ["state description"],
                      "testData": ["data description"]
                    },
                    "when": {
                      "action": "Call method with params",
                      "methodCall": "object.method(arg1, arg2)",
                      "arguments": ["arg1", "arg2"],
                      "expectsException": false,
                      "expectedExceptionType": null
                    },
                    "then": {
                      "assertions": [
                        {"description": "Check result", "actualExpression": "result", "expectedValue": "expected", "type": "EQUALS"}
                      ],
                      "expectedReturnValue": "expected value",
                      "stateChanges": [],
                      "sideEffects": []
                    },
                    "tags": ["unit", "feature"],
                    "priority": "P0",
                    "requiresMocking": true,
                    "isParameterized": false,
                    "parameterSets": []
                  }
                }
              ]
            }
            """,
            context.className(),
            context.methodName(),
            context.className(),
            context.methodName(),
            formatParams(context.parameters()),
            context.returnType(),
            String.join(", ", context.annotations()),
            formatCFG(context.controlFlow().nodes()),
            intentOutput.goal(),
            String.join(", ", intentOutput.preconditions()),
            String.join(", ", intentOutput.postconditions()),
            String.join(", ", intentOutput.exceptions()),
            context.complexity().cyclomatic(),
            context.complexity().branchCount(),
            context.complexity().loopCount(),
            formatDependencies(context.dependencies()),
            context.docContract() != null ? context.docContract().params() : "{}",
            context.docContract() != null ? context.docContract().returns() : "not specified",
            context.docContract() != null ? context.docContract().throwsList() : "[]",
            context.docContract() != null ? context.docContract().businessRules() : "[]"
        );

        String response = llmProvider.chat(userPrompt, systemPrompt);
        LOG.debug("Scenario mapping response received");

        try {
            ScenarioTree tree = objectMapper.readValue(response, ScenarioTree.class);
            LOG.info("Generated scenario tree with {} scenarios", 
                tree.children() != null ? tree.children().size() : 0);
            return tree;
        } catch (JsonProcessingException e) {
            LOG.warn("Failed to parse scenario tree, using fallback", e);
            return createFallbackScenarios(context, intentOutput);
        }
    }

    /**
     * Step 3: Test Design
     * Public method for use in UI dialog
     */
    @NotNull
    public TestDesign designTests(@NotNull MethodContext context,
                                   @NotNull ScenarioTree scenarioTree) throws LLMProvider.LLMException {
        String systemPrompt = """
            You are designing the test implementation strategy for a Java method.
            Decide on:
            - Test framework (JUnit 5 preferred, but respect existing tests)
            - Naming convention for test methods
            - Mocking strategy (Mockito, none, etc.)
            - Whether to use parameterized tests
            - Assertion library (AssertJ preferred)
            
            Respond with JSON containing:
            - framework: JUNIT5, JUNIT4, or TESTNG
            - namingConvention: Pattern like "should_{expected}_when_{condition}"
            - mockingStrategy: NONE, MOCKITO_EXTEND_WITH, MOCKITO_RUNNER, POWERMOCK
            - useParameterized: boolean
            - assertionLibrary: JUNIT, ASSERTJ, HAMCREST, TRUTH
            """;

        String userPrompt = String.format("""
            Design test implementation strategy for %s.%s
            
            Existing Tests (for style reference):
            %s
            
            Dependencies:
            %s
            
            Scenarios to cover: %d scenarios
            
            Respond with valid JSON matching this schema:
            {
              "framework": "JUNIT5",
              "namingConvention": "string",
              "mockingStrategy": "MOCKITO_EXTEND_WITH",
              "useParameterized": boolean,
              "assertionLibrary": "ASSERTJ"
            }
            """,
            context.className(),
            context.methodName(),
            formatExistingTests(context.existingTests()),
            formatDependencies(context.dependencies()),
            scenarioTree.children() != null ? scenarioTree.children().size() : 0
        );

        String response = llmProvider.chat(userPrompt, systemPrompt);
        LOG.debug("Test design response received");

        try {
            return objectMapper.readValue(response, TestDesign.class);
        } catch (JsonProcessingException e) {
            LOG.warn("Failed to parse test design, using fallback", e);
            return createFallbackDesign(context);
        }
    }

    /**
     * Step 4: Code Generation
     * Generates test code using TestCaseCodeGenerator from specifications
     * Public method for use in UI dialog
     */
    @NotNull
    public GeneratedCode generateCode(@NotNull TestDesign design,
                                       @NotNull ScenarioTree scenarioTree,
                                       @NotNull MethodContext context) throws LLMProvider.LLMException {
        LOG.info("Generating test code from {} scenarios", 
            scenarioTree.children() != null ? scenarioTree.children().size() : 0);

        // Use TestCaseCodeGenerator for scenarios with specifications
        TestCaseCodeGenerator codeGenerator = new TestCaseCodeGenerator(
            design, context.className(), context.methodName());

        StringBuilder testClassCode = new StringBuilder();
        List<String> testCaseSpecs = new ArrayList<>();

        // Generate test methods from specifications
        if (scenarioTree.children() != null) {
            for (ScenarioTree.ScenarioNode scenario : scenarioTree.children()) {
                if (scenario.testCaseSpec() != null) {
                    LOG.debug("Generating test method: {}", scenario.testCaseSpec().testName());
                    
                    String testMethodCode;
                    if (scenario.testCaseSpec().isParameterized()) {
                        testMethodCode = codeGenerator.generateParameterizedTest(scenario.testCaseSpec());
                    } else {
                        testMethodCode = codeGenerator.generateTestMethod(scenario.testCaseSpec());
                    }
                    
                    testClassCode.append(testMethodCode).append("\n");
                    testCaseSpecs.add(scenario.testCaseSpec().description());
                }
            }
        }

        // If no specifications were generated, fall back to LLM generation
        if (testClassCode.length() == 0) {
            LOG.info("No test case specifications found, using LLM generation");
            return generateCodeViaLLM(design, scenarioTree, context);
        }

        // Generate imports
        List<String> imports = generateTestClassImports(context, design, scenarioTree);

        // Build complete test class
        String fullTestClass = buildTestClass(
            context, design, imports, testClassCode.toString());

        LOG.info("Generated test class with {} test methods", testCaseSpecs.size());

        return new GeneratedCode(
            fullTestClass,
            imports,
            Map.of()
        );
    }

    /**
     * Fallback: Generate code via LLM (original behavior)
     */
    @NotNull
    private GeneratedCode generateCodeViaLLM(@NotNull TestDesign design,
                                              @NotNull ScenarioTree scenarioTree,
                                              @NotNull MethodContext context) throws LLMProvider.LLMException {
        String systemPrompt = """
            You are a Senior Java Developer writing high-quality unit tests.
            Generate complete test class code following the design specifications.

            Guidelines:
            - Use proper imports
            - Follow naming conventions
            - Include setup/teardown if needed
            - Mock external dependencies appropriately
            - Write clear, descriptive test methods
            - Each test should be isolated and repeatable
            - Use realistic test data

            The code must be valid Java that compiles without errors.
            """;

        String userPrompt = String.format("""
            Generate unit tests for %s.%s

            Test Design:
            - Framework: %s
            - Naming Convention: %s
            - Mocking Strategy: %s
            - Use Parameterized: %s
            - Assertion Library: %s

            Scenarios to cover:
            %s

            Method Context:
            - Return Type: %s
            - Parameters: %s
            - Dependencies: %s

            Generate the complete test class code as a single Java file.
            Include all necessary imports, annotations, and helper methods.
            """,
            context.className(),
            context.methodName(),
            design.framework(),
            design.namingConvention(),
            design.mockingStrategy(),
            design.useParameterized(),
            design.assertionLibrary(),
            formatScenarios(scenarioTree),
            context.returnType(),
            formatParams(context.parameters()),
            formatDependencies(context.dependencies())
        );

        String response = llmProvider.chat(userPrompt, systemPrompt);
        LOG.debug("Code generation response received");

        return new GeneratedCode(
            extractCodeFromResponse(response),
            extractImports(response),
            Map.of()
        );
    }

    /**
     * Generate test class imports
     */
    @NotNull
    private List<String> generateTestClassImports(@NotNull MethodContext context,
                                                   @NotNull TestDesign design,
                                                   @NotNull ScenarioTree scenarioTree) {
        List<String> imports = new ArrayList<>();

        // Test framework imports
        if (design.framework() == TestDesign.TestFramework.JUNIT5) {
            imports.add("import org.junit.jupiter.api.Test;");
            imports.add("import org.junit.jupiter.api.BeforeEach;");
            imports.add("import org.junit.jupiter.api.AfterEach;");
            imports.add("import org.junit.jupiter.api.DisplayName;");
            imports.add("import org.junit.jupiter.api.Tag;");

            // Check for parameterized tests
            boolean hasParameterized = scenarioTree.children() != null && 
                scenarioTree.children().stream()
                    .map(ScenarioTree.ScenarioNode::testCaseSpec)
                    .anyMatch(spec -> spec != null && spec.isParameterized());

            if (hasParameterized) {
                imports.add("import org.junit.jupiter.params.ParameterizedTest;");
                imports.add("import org.junit.jupiter.params.provider.MethodSource;");
                imports.add("import org.junit.jupiter.params.provider.Arguments;");
                imports.add("import java.util.stream.Stream;");
            }
        }

        // Mockito imports
        if (design.mockingStrategy() != TestDesign.MockingStrategy.NONE) {
            imports.add("import org.mockito.Mock;");
            imports.add("import org.mockito.MockitoAnnotations;");
            imports.add("import static org.mockito.Mockito.*;");
            imports.add("import org.mockito.junit.jupiter.MockitoExtension;");
            imports.add("import org.junit.jupiter.api.extension.ExtendWith;");
        }

        // AssertJ imports
        if (design.assertionLibrary() == TestDesign.AssertionLibrary.ASSERTJ) {
            imports.add("import static org.assertj.core.api.Assertions.*;");
            imports.add("import org.assertj.core.api.Assertions;");
        }

        // Add imports for dependencies
        for (Dependency dep : context.dependencies()) {
            if (dep.type() != null && !dep.type().startsWith("java.")) {
                imports.add("import " + dep.type() + ";");
            }
        }

        // Import the class under test
        imports.add("import " + context.className() + ";");

        return imports;
    }

    /**
     * Build complete test class structure
     */
    @NotNull
    private String buildTestClass(@NotNull MethodContext context,
                                   @NotNull TestDesign design,
                                   @NotNull List<String> imports,
                                   @NotNull String testMethods) {
        String packageName = "tests"; // Default package for tests
        String testClassName = context.className() + "Test";

        StringBuilder classCode = new StringBuilder();

        // Package declaration
        classCode.append("package ").append(packageName).append(";\n\n");

        // Imports
        for (String imp : imports) {
            classCode.append(imp).append("\n");
        }
        classCode.append("\n");

        // Class declaration
        classCode.append("/**\n");
        classCode.append(" * Unit tests for ").append(context.className()).append("\n");
        classCode.append(" * Generated by Reasoning Test Generator\n");
        classCode.append(" */\n");

        if (design.mockingStrategy() == TestDesign.MockingStrategy.MOCKITO_EXTEND_WITH) {
            classCode.append("@ExtendWith(MockitoExtension.class)\n");
        }

        classCode.append("class ").append(testClassName).append(" {\n\n");

        // Mock fields
        if (context.dependencies() != null && !context.dependencies().isEmpty()) {
            classCode.append("    // ===== Mocks =====\n");
            for (Dependency dep : context.dependencies()) {
                if (!dep.isExternal()) {
                    classCode.append("    @Mock\n");
                    classCode.append("    private ")
                        .append(dep.type())
                        .append(" ")
                        .append(dep.name())
                        .append(";\n\n");
                }
            }
        }

        // Class under test
        classCode.append("    // ===== Class under test =====\n");
        classCode.append("    private ").append(context.className()).append(" classUnderTest;\n\n");

        // Setup method
        classCode.append("    @BeforeEach\n");
        classCode.append("    void setUp() {\n");
        classCode.append("        MockitoAnnotations.openMocks(this);\n");
        classCode.append("        classUnderTest = new ").append(context.className()).append("();\n");
        classCode.append("    }\n\n");

        // Test methods
        classCode.append("    // ===== Test Methods =====\n");
        classCode.append(testMethods);

        // Teardown method
        classCode.append("\n    @AfterEach\n");
        classCode.append("    void tearDown() {\n");
        classCode.append("        // Clean up if needed\n");
        classCode.append("    }\n");

        // Close class
        classCode.append("}\n");

        return classCode.toString();
    }

    /**
     * Self-correction loop for code generation
     */
    @NotNull
    private GeneratedCode generateWithSelfCorrection(@NotNull TestDesign design,
                                                      @NotNull ScenarioTree scenarioTree) throws LLMProvider.LLMException {
        GeneratedCode code = generateCode(design, scenarioTree, null);
        
        // Use self-correction engine if available
        if (selfCorrectionEngine != null && project != null) {
            LOG.info("Using PSI-based self-correction");
            
            SelfCorrectionEngine.CorrectionResult result = selfCorrectionEngine.correctCode(
                code,
                design,
                code.javaCode()
            );
            
            if (result.success()) {
                LOG.info("Self-correction succeeded after {} attempts", result.attemptsCount());
                return new GeneratedCode(
                    result.correctedCode(),
                    code.imports(),
                    code.methodToScenarioMap()
                );
            } else {
                LOG.warn("Self-correction failed after {} attempts with {} errors",
                    result.attemptsCount(), result.remainingErrors().size());
                // Return best attempt even if failed
                return new GeneratedCode(
                    result.correctedCode(),
                    code.imports(),
                    code.methodToScenarioMap()
                );
            }
        }
        
        // Fallback to simple validation
        LOG.info("Using simple self-validation (no PSI)");
        for (int attempt = 1; attempt <= 3; attempt++) {
            LOG.info("Self-validation attempt {}/3", attempt);
            
            ValidationResult validation = validateCode(code, design);
            
            if (validation.isValid()) {
                LOG.info("Code validation passed");
                return code;
            }
            
            LOG.warn("Code validation found {} problems, refining", validation.problems().size());
            code = refineCode(code, validation.problems(), design, scenarioTree);
        }
        
        LOG.warn("Max self-correction attempts reached, returning last version");
        return code;
    }

    /**
     * Validate generated code
     */
    @NotNull
    private ValidationResult validateCode(@NotNull GeneratedCode code, 
                                           @NotNull TestDesign design) throws LLMProvider.LLMException {
        String systemPrompt = """
            You are reviewing generated test code for correctness and completeness.
            Check for:
            - Syntax errors
            - Missing imports
            - Incorrect method calls
            - Wrong assertions
            - Missing scenario coverage
            - Over-mocking or under-mocking
            
            Respond with JSON:
            {
              "isValid": boolean,
              "problems": ["list of problems found"],
              "fixedCode": "corrected code if problems found"
            }
            """;

        String userPrompt = String.format("""
            Review the following test code:
            
            %s
            
            Design specifications:
            - Framework: %s
            - Assertion Library: %s
            - Mocking Strategy: %s
            
            Is the code correct and complete?
            """,
            code.javaCode(),
            design.framework(),
            design.assertionLibrary(),
            design.mockingStrategy()
        );

        String response = llmProvider.chat(userPrompt, systemPrompt);
        LOG.debug("Validation response received");

        try {
            return objectMapper.readValue(response, ValidationResult.class);
        } catch (JsonProcessingException e) {
            LOG.warn("Failed to parse validation result, assuming valid", e);
            return new ValidationResult(true, List.of(), code.javaCode());
        }
    }

    /**
     * Refine code based on validation feedback
     */
    @NotNull
    private GeneratedCode refineCode(@NotNull GeneratedCode code,
                                      @NotNull List<String> problems,
                                      @NotNull TestDesign design,
                                      @NotNull ScenarioTree scenarioTree) throws LLMProvider.LLMException {
        String systemPrompt = """
            You are fixing issues in the generated test code.
            Address all the problems identified in the validation.
            Keep the code structure intact, only fix the specific issues.
            """;

        String userPrompt = String.format("""
            Fix the following issues in the test code:
            
            Problems:
            %s
            
            Current code:
            %s
            
            Return the corrected code.
            """,
            String.join("\n", problems),
            code.javaCode()
        );

        String response = llmProvider.chat(userPrompt, systemPrompt);
        
        return new GeneratedCode(
            extractCodeFromResponse(response),
            extractImports(response),
            code.methodToScenarioMap()
        );
    }

    // ===== Helper Methods =====

    @NotNull
    private String formatParams(@NotNull List<Parameter> params) {
        if (params.isEmpty()) return "none";
        return params.stream()
            .map(p -> p.type() + " " + p.name())
            .collect(Collectors.joining(", "));
    }

    @NotNull
    private String formatCFG(@NotNull List<CFGNode> nodes) {
        if (nodes.isEmpty()) return "No complex control flow";
        return nodes.stream()
            .map(CFGNode::toString)
            .collect(Collectors.joining("\n"));
    }

    @NotNull
    private String formatExistingTests(@NotNull List<ExistingTestInfo> tests) {
        if (tests.isEmpty()) return "No existing tests";
        return tests.stream()
            .map(t -> t.name() + " [" + t.framework() + "]")
            .collect(Collectors.joining(", "));
    }

    @NotNull
    private String formatDependencies(@NotNull List<Dependency> deps) {
        if (deps.isEmpty()) return "No external dependencies";
        return deps.stream()
            .map(Dependency::toString)
            .collect(Collectors.joining("\n"));
    }

    @NotNull
    private String formatScenarios(@NotNull ScenarioTree tree) {
        if (tree.children() == null || tree.children().isEmpty()) {
            return "No scenarios specified";
        }
        return tree.children().stream()
            .map(s -> "- " + s.id() + ": " + s.description() + 
                     " (type: " + s.type() + 
                     ", expect: " + s.expectedOutcome() + ")")
            .collect(Collectors.joining("\n"));
    }

    @NotNull
    private String extractCodeFromResponse(@NotNull String response) {
        // Extract code from markdown code blocks if present
        int startIndex = response.indexOf("```java");
        if (startIndex == -1) {
            startIndex = response.indexOf("```");
        }
        
        if (startIndex != -1) {
            int codeStart = response.indexOf('\n', startIndex) + 1;
            int endIndex = response.indexOf("```", codeStart);
            if (endIndex != -1) {
                return response.substring(codeStart, endIndex).trim();
            }
        }
        
        return response;
    }

    @NotNull
    private List<String> extractImports(@NotNull String code) {
        List<String> imports = new ArrayList<>();
        String[] lines = code.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("import ")) {
                imports.add(trimmed.replaceAll(";$", "").substring(7));
            }
        }
        return imports;
    }

    @NotNull
    private IntentOutput createFallbackIntent(@NotNull MethodContext context) {
        return new IntentOutput(
            "Test " + context.methodName() + " method",
            List.of("Method is called with valid parameters"),
            List.of("Method returns correct result"),
            List.of(),
            context.docContract() != null && context.docContract().throwsList() != emptyList()
                ? context.docContract().throwsList()
                : List.of()
        );
    }

    @NotNull
    private ScenarioTree createFallbackScenarios(@NotNull MethodContext context, 
                                                   @NotNull IntentOutput intent) {
        List<ScenarioTree.ScenarioNode> children = new ArrayList<>();
        
        // Happy path
        children.add(new ScenarioTree.ScenarioNode(
            "S1", ScenarioTree.ScenarioType.HAPPY,
            "Happy path - normal execution",
            "Valid parameters",
            intent.postconditions().get(0),
            false,
            List.of()
        ));
        
        // Error paths if exceptions documented
        if (intent.exceptions() != null && !intent.exceptions().isEmpty()) {
            children.add(new ScenarioTree.ScenarioNode(
                "S2", ScenarioTree.ScenarioType.ERROR,
                "Error path - exception thrown",
                "Invalid parameters or error condition",
                "Throws " + String.join(" or ", intent.exceptions()),
                true,
                List.of()
            ));
        }
        
        return new ScenarioTree(
            new ScenarioTree.ScenarioNode("S0", null, "All scenarios", null, null, false, List.of()),
            children
        );
    }

    @NotNull
    private TestDesign createFallbackDesign(@NotNull MethodContext context) {
        return new TestDesign(
            TestDesign.TestFramework.JUNIT5,
            "should_{expected}_when_{condition}",
            TestDesign.MockingStrategy.MOCKITO_EXTEND_WITH,
            false,
            TestDesign.AssertionLibrary.ASSERTJ
        );
    }

    @NotNull
    private static List<String> emptyList() {
        return List.of();
    }
}
