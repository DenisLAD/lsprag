package com.reasoningtestgen.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellij.openapi.project.Project;
import com.reasoningtestgen.builder.ContextBuilder;
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
     */
    @NotNull
    private IntentOutput analyzeIntent(@NotNull MethodContext context) throws LLMProvider.LLMException {
        String systemPrompt = """
            You are analyzing a Java method to understand its intent and contract.
            Respond with JSON containing:
            - goal: Business purpose of the method
            - preconditions: What must be true before calling this method
            - postconditions: What will be true after calling this method
            - sideEffects: Any side effects (state changes, I/O, etc.)
            - exceptions: When and why the method throws exceptions
            """;

        String userPrompt = String.format("""
            Analyze the following method and extract its intent and contract:
            
            Class: %s
            Method: %s(%s)
            Return Type: %s
            Annotations: %s
            
            Documentation Contract:
            - Parameters: %s
            - Returns: %s
            - Throws: %s
            - Business Rules: %s
            
            Respond with valid JSON matching this schema:
            {
              "goal": "string",
              "preconditions": ["string"],
              "postconditions": ["string"],
              "sideEffects": ["string"],
              "exceptions": ["string"]
            }
            """,
            context.className(),
            context.methodName(),
            formatParams(context.parameters()),
            context.returnType(),
            String.join(", ", context.annotations()),
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
     */
    @NotNull
    private ScenarioTree generateScenarios(@NotNull MethodContext context, 
                                           @NotNull IntentOutput intentOutput) throws LLMProvider.LLMException {
        String systemPrompt = """
            You are designing test scenarios for a Java method based on its control flow and intent.
            Create a comprehensive scenario tree covering:
            - Happy path (normal successful execution)
            - Error paths (exceptions, error handling)
            - Boundary conditions (edge cases, limits)
            - State transitions (if applicable)
            
            Respond with JSON containing:
            - root: Root scenario node
            - children: List of scenario nodes
            """;

        String userPrompt = String.format("""
            Generate test scenarios for method %s.%s
            
            Control Flow:
            %s
            
            Intent Analysis:
            - Goal: %s
            - Preconditions: %s
            - Exceptions: %s
            
            Complexity: Cyclomatic=%d, Branches=%d
            
            Create a scenario tree with comprehensive test cases.
            Respond with valid JSON matching this schema:
            {
              "root": {"id": "S0", "description": "All scenarios"},
              "children": [
                {
                  "id": "S1",
                  "type": "HAPPY|ERROR|BOUNDARY|STATE",
                  "description": "string",
                  "inputConditions": "string",
                  "expectedOutcome": "string",
                  "shouldThrow": boolean,
                  "children": []
                }
              ]
            }
            """,
            context.className(),
            context.methodName(),
            formatCFG(context.controlFlow().nodes()),
            intentOutput.goal(),
            intentOutput.preconditions(),
            intentOutput.exceptions(),
            context.complexity().cyclomatic(),
            context.complexity().branchCount()
        );

        String response = llmProvider.chat(userPrompt, systemPrompt);
        LOG.debug("Scenario mapping response received");

        try {
            return objectMapper.readValue(response, ScenarioTree.class);
        } catch (JsonProcessingException e) {
            LOG.warn("Failed to parse scenario tree, using fallback", e);
            return createFallbackScenarios(context, intentOutput);
        }
    }

    /**
     * Step 3: Test Design
     */
    @NotNull
    private TestDesign designTests(@NotNull MethodContext context, 
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
     */
    @NotNull
    private GeneratedCode generateCode(@NotNull TestDesign design, 
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
