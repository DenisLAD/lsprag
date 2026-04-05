package com.reasoningtestgen;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reasoningtestgen.builder.ContextBuilder;
import com.reasoningtestgen.model.*;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.util.Timeout;
import org.apache.hc.client5.http.config.RequestConfig;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

/**
 * Dogfooding test: Use the plugin to generate tests for itself
 * This validates the complete plugin workflow end-to-end
 */
public class PluginDogfoodingTest {

    private static final String ENDPOINT = "http://localhost:1234/v1/chat/completions";
    private static final String MODEL = "qwen/qwen3.5-9b";
    private static final String OUTPUT_DIR = "build/dogfooding-output";

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("Plugin Dogfooding Test");
        System.out.println("Using plugin to generate tests for itself");
        System.out.println("========================================\n");

        try {
            // Step 1: Create context for PromptHistoryService.storePrompt()
            System.out.println("Step 1: Creating context for PromptHistoryService.storePrompt()...");
            MethodContext context = createContextForPromptHistoryService();
            System.out.println("  Context created for: PromptHistoryService.storePrompt()");
            System.out.println("  Cyclomatic complexity: " + context.complexity().cyclomatic());
            System.out.println("  Dependencies: " + context.dependencies().size());
            System.out.println("  CFG nodes: " + context.controlFlow().nodes().size());
            System.out.println("  ✓ Context ready\n");

            // Step 2: Build prompt using ContextBuilder
            System.out.println("Step 2: Building prompt with ContextBuilder...");
            ContextBuilder contextBuilder = new ContextBuilder();
            PromptBundle bundle = contextBuilder.buildPromptBundle(context);
            String fullPrompt = bundle.systemPrompt() + "\n\n=== USER PROMPT ===\n\n" + bundle.userPrompt();
            
            System.out.println("  System prompt: " + bundle.systemPrompt().length() + " chars");
            System.out.println("  User prompt: " + bundle.userPrompt().length() + " chars");
            System.out.println("  Total prompt: " + fullPrompt.length() + " chars");
            
            // Save prompt for review
            Path outputDir = Paths.get(OUTPUT_DIR);
            Files.createDirectories(outputDir);
            Files.writeString(outputDir.resolve("01-context.json"), contextBuilder.contextToJson(context));
            Files.writeString(outputDir.resolve("02-full-prompt.txt"), fullPrompt);
            System.out.println("  ✓ Prompt saved to: " + outputDir.toAbsolutePath());
            System.out.println();

            // Step 3: Validate prompt quality
            System.out.println("Step 3: Validating prompt quality...");
            int qualityScore = validatePromptQuality(bundle, context);
            System.out.println("  Quality Score: " + qualityScore + "/100");
            System.out.println();

            // Step 4: Send to LM Studio for test generation
            System.out.println("Step 4: Sending to LM Studio for test generation...");
            System.out.println("  Model: " + MODEL);
            System.out.println("  This may take 30-60 seconds...\n");
            
            String generatedTest = sendToLLMForTestGeneration(fullPrompt, context);
            
            // Save generated test
            Files.writeString(outputDir.resolve("03-generated-test.java"), generatedTest);
            System.out.println("  Generated test: " + generatedTest.length() + " chars");
            System.out.println("  Lines: " + generatedTest.split("\n").length);
            System.out.println("  ✓ Test saved to: " + outputDir.resolve("03-generated-test.java"));
            System.out.println();

            // Step 5: Validate generated test quality
            System.out.println("Step 5: Validating generated test quality...");
            int testQualityScore = validateGeneratedTestQuality(generatedTest, context);
            System.out.println("  Test Quality Score: " + testQualityScore + "/100");
            System.out.println();

            // Step 6: Summary
            System.out.println("========================================");
            System.out.println("Dogfooding Test Results");
            System.out.println("========================================");
            System.out.println("Prompt Quality:      " + qualityScore + "/100");
            System.out.println("Generated Test:      " + testQualityScore + "/100");
            System.out.println("Total Score:         " + (qualityScore + testQualityScore) + "/200");
            System.out.println("========================================");
            
            if (qualityScore >= 80 && testQualityScore >= 70) {
                System.out.println("✓ Plugin dogfooding PASSED!");
                System.out.println("\nFiles saved to: " + outputDir.toAbsolutePath());
                System.exit(0);
            } else {
                System.out.println("⚠ Partial success - review generated test");
                System.out.println("\nFiles saved to: " + outputDir.toAbsolutePath());
                System.exit(1);
            }
            
        } catch (Exception e) {
            System.err.println("✗ Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Create MethodContext for PromptHistoryService.storePrompt() method
     * This simulates what PSIExtractor would do
     */
    private static MethodContext createContextForPromptHistoryService() {
        // Parameters of storePrompt() method
        List<Parameter> parameters = List.of(
            new Parameter("step", "PromptEntry.ReasoningStep", false),
            new Parameter("systemPrompt", "String", false),
            new Parameter("userPrompt", "String", false),
            new Parameter("llmResponse", "String", false),
            new Parameter("model", "String", false),
            new Parameter("responseTimeMs", "long", false),
            new Parameter("success", "boolean", false)
        );

        // Dependencies
        List<Dependency> dependencies = List.of(
            new Dependency("memoryCache", "List<PromptEntry>", false, false),
            new Dependency("objectMapper", "ObjectMapper", false, false),
            new Dependency("historyDir", "Path", false, false)
        );

        // Dependency info with Spring/Lombok/MapStruct (not applicable here, but showing structure)
        List<DependencyInfo> dependenciesInfo = List.of();

        // Called methods
        List<CalledMethodInfo> calledMethods = List.of(
            new CalledMethodInfo(
                "com.reasoningtestgen.model.PromptEntry",
                "PromptEntry",
                "PromptEntry",
                List.of("String id", "LocalDateTime timestamp", "ReasoningStep step", "..."),
                false,
                true,
                "public record PromptEntry(...)",
                List.of()
            ),
            new CalledMethodInfo(
                "com.fasterxml.jackson.databind.ObjectMapper",
                "writeValueAsString",
                "String",
                List.of("Object value"),
                false,
                false,
                "",
                List.of()
            ),
            new CalledMethodInfo(
                "java.nio.file.Files",
                "writeString",
                "Path",
                List.of("Path path", "String content"),
                true,
                true,
                "public static Path writeString(Path path, String content)",
                List.of()
            )
        );

        // DTO structures
        List<DTOInfo> dtoStructures = List.of(
            new DTOInfo(
                "PromptEntry",
                "com.reasoningtestgen.model.PromptEntry",
                List.of(
                    new DTOInfo.FieldInfo("id", "String", List.of(), true, "getId()", "N/A"),
                    new DTOInfo.FieldInfo("timestamp", "LocalDateTime", List.of(), true, "getTimestamp()", "N/A"),
                    new DTOInfo.FieldInfo("step", "ReasoningStep", List.of(), true, "getStep()", "N/A"),
                    new DTOInfo.FieldInfo("systemPrompt", "String", List.of(), true, "getSystemPrompt()", "N/A"),
                    new DTOInfo.FieldInfo("userPrompt", "String", List.of(), true, "getUserPrompt()", "N/A"),
                    new DTOInfo.FieldInfo("llmResponse", "String", List.of(), true, "getLlmResponse()", "N/A"),
                    new DTOInfo.FieldInfo("model", "String", List.of(), true, "getModel()", "N/A"),
                    new DTOInfo.FieldInfo("responseTimeMs", "long", List.of(), true, "getResponseTimeMs()", "N/A"),
                    new DTOInfo.FieldInfo("success", "boolean", List.of(), true, "isSuccess()", "N/A")
                ),
                List.of(),
                false,
                null
            )
        );

        // Data transformations
        List<DataTransformation> dataTransformations = List.of(
            new DataTransformation(
                "systemPrompt",
                "String",
                List.of(
                    new DataTransformation.TransformationStep(1, "assignment", "Used to create PromptEntry", "entry.systemPrompt")
                ),
                "used in PromptEntry creation",
                false,
                Map.of()
            ),
            new DataTransformation(
                "responseTimeMs",
                "long",
                List.of(
                    new DataTransformation.TransformationStep(1, "assignment", "Used to create PromptEntry", "entry.responseTimeMs")
                ),
                "used in PromptEntry creation",
                false,
                Map.of()
            )
        );

        // CFG - simplified control flow for storePrompt method
        List<CFGNode> cfgNodes = List.of(
            new CFGNode(CFGNode.NodeType.IF, "!enabled", 54, 55, 58, null),
            new CFGNode(CFGNode.NodeType.IF, "promptHistoryService != null", 58, 59, 78, null),
            new CFGNode(CFGNode.NodeType.IF, "historyDir != null", 65, 66, 72, null),
            new CFGNode(CFGNode.NodeType.TRY, "Files.createDirectories", 68, null, null, 
                new CFGNode.CatchInfo("IOException", 70)),
            new CFGNode(CFGNode.NodeType.IF, "fileName != null", 72, 73, 76, null),
            new CFGNode(CFGNode.NodeType.RETURN, null, 78, null, null, null)
        );

        // Doc contract
        DocContract docContract = new DocContract(
            Map.of(
                "step", "The reasoning step this prompt belongs to",
                "systemPrompt", "The system prompt sent to LLM",
                "userPrompt", "The user prompt sent to LLM",
                "llmResponse", "The response received from LLM",
                "model", "The model used for generation",
                "responseTimeMs", "Time taken for LLM response in milliseconds",
                "success", "Whether the generation was successful"
            ),
            "void - stores prompt to memory cache and optionally to disk",
            List.of("IOException if writing to disk fails"),
            List.of(
                "All prompts should be stored for analysis",
                "Failed generations should still be recorded with success=false",
                "Disk storage is optional - memory cache is always used"
            )
        );

        // Existing tests (none for this method)
        List<ExistingTestInfo> existingTests = List.of();

        // Complexity metrics
        ComplexityMetrics complexity = new ComplexityMetrics(6, 2, 6, 0);

        return new MethodContext(
            "PromptHistoryService",
            "storePrompt",
            "void",
            parameters,
            List.of(),
            new MethodContext.ControlFlow(cfgNodes),
            dependencies,
            dependenciesInfo,
            calledMethods,
            dtoStructures,
            dataTransformations,
            docContract,
            existingTests,
            complexity
        );
    }

    /**
     * Validate prompt quality
     */
    private static int validatePromptQuality(PromptBundle bundle, MethodContext context) {
        System.out.println("\n--- Prompt Quality Report ---\n");

        int score = 0;

        // Check 1: System prompt has role
        if (bundle.systemPrompt().contains("Senior Test Engineer")) {
            System.out.println("✓ System prompt defines role");
            score += 10;
        } else {
            System.out.println("✗ System prompt missing role");
        }

        // Check 2: User prompt has method signature
        String userPrompt = bundle.userPrompt();
        if (userPrompt.contains("PromptHistoryService") && userPrompt.contains("storePrompt")) {
            System.out.println("✓ User prompt has method signature");
            score += 10;
        } else {
            System.out.println("✗ User prompt missing method signature");
        }

        // Check 3: CFG is present
        if (userPrompt.contains("Граф потока управления") || userPrompt.contains("Control Flow") || userPrompt.contains("if (")) {
            System.out.println("✓ Control flow graph included");
            score += 15;
        } else {
            System.out.println("✗ Control flow graph missing");
        }

        // Check 4: Dependencies listed
        if (userPrompt.contains("Зависимости") || userPrompt.contains("Dependencies") || userPrompt.contains("memoryCache")) {
            System.out.println("✓ Dependencies included");
            score += 10;
        } else {
            System.out.println("✗ Dependencies missing");
        }

        // Check 5: Documentation contract
        if (userPrompt.contains("Документация") || userPrompt.contains("Documentation") || userPrompt.contains("Параметры")) {
            System.out.println("✓ Documentation contract included");
            score += 15;
        } else {
            System.out.println("✗ Documentation contract missing");
        }

        // Check 6: Called methods
        if (userPrompt.contains("Вызываемые методы") || userPrompt.contains("Called methods") || userPrompt.contains("PromptEntry")) {
            System.out.println("✓ Called methods included");
            score += 10;
        } else {
            System.out.println("✗ Called methods missing");
        }

        // Check 7: DTO structures
        if (userPrompt.contains("DTO/POJO") || userPrompt.contains("PromptEntry") || userPrompt.contains("FieldInfo")) {
            System.out.println("✓ DTO structures included");
            score += 10;
        } else {
            System.out.println("✗ DTO structures missing");
        }

        // Check 8: Prompt length
        int totalLength = bundle.systemPrompt().length() + bundle.userPrompt().length();
        if (totalLength > 500) {
            System.out.println("✓ Prompt has sufficient detail (" + totalLength + " chars)");
            score += 10;
        } else {
            System.out.println("✗ Prompt too short (" + totalLength + " chars)");
        }

        // Check 9: Clear task instruction
        if (userPrompt.contains("Задача") || userPrompt.contains("Сгенерируйте") || userPrompt.contains("Task")) {
            System.out.println("✓ Clear task instruction");
            score += 10;
        } else {
            System.out.println("✗ Task instruction unclear");
        }

        // Check 10: Formatting
        if (userPrompt.contains("##") && userPrompt.contains("\n\n")) {
            System.out.println("✓ Well-formatted prompt");
            score += 10;
        } else {
            System.out.println("✗ Poor formatting");
        }

        System.out.println("\n--- Quality Score: " + score + "/100 ---");
        return score;
    }

    /**
     * Send prompt to LLM for test generation
     */
    private static String sendToLLMForTestGeneration(String prompt, MethodContext context) throws Exception {
        String enhancedPrompt = prompt + "\n\n" + """
            Please generate a complete JUnit 5 test class for this method.
            
            Requirements:
            - Use JUnit 5 (@Test, @BeforeEach, @DisplayName)
            - Use AssertJ for assertions
            - Test all scenarios based on the CFG
            - Mock external dependencies (ObjectMapper, Files)
            - Test both success and failure cases
            - Use meaningful test data
            - Follow naming: should_{expected}_when_{condition}
            
            Return ONLY the Java code, no explanations or markdown.
            Start with package and imports, end with closing brace.
            """;

        String systemPrompt = """
            Вы — Senior Test Engineer, специализирующийся на создании высококачественных unit-тестов для Java-приложений.
            Сгенерируйте ПРОИЗВОДСТВЕННО-ГОТОВЫЕ unit-тесты для указанного метода.
            Используйте JUnit 5 и AssertJ.
            """;

        return sendToLLM(enhancedPrompt, systemPrompt);
    }

    /**
     * Send request to LM Studio
     */
    private static String sendToLLM(String userPrompt, String systemPrompt) throws Exception {
        RequestConfig requestConfig = RequestConfig.custom()
            .setConnectionRequestTimeout(Timeout.ofSeconds(120))
            .setResponseTimeout(Timeout.ofSeconds(120))
            .build();

        String requestBody = String.format(
            "{\"model\":\"%s\",\"messages\":[{\"role\":\"system\",\"content\":\"%s\"},{\"role\":\"user\",\"content\":\"%s\"}],\"temperature\":0.3,\"max_tokens\":4096}",
            MODEL,
            escapeJson(systemPrompt),
            escapeJson(userPrompt)
        );

        try (CloseableHttpClient client = HttpClients.custom()
                .setDefaultRequestConfig(requestConfig)
                .build()) {
            
            HttpPost post = new HttpPost(ENDPOINT);
            post.setEntity(new StringEntity(requestBody, ContentType.APPLICATION_JSON));
            post.setHeader("Content-Type", "application/json");
            
            return client.execute(post, response -> {
                int statusCode = response.getCode();
                String responseBody = EntityUtils.toString(response.getEntity());
                
                if (statusCode != 200) {
                    throw new RuntimeException("HTTP " + statusCode + ": " + responseBody);
                }
                
                ObjectMapper mapper = new ObjectMapper();
                var json = mapper.readTree(responseBody);
                var choices = json.get("choices");
                
                if (choices != null && choices.isArray() && choices.size() > 0) {
                    var message = choices.get(0).get("message");
                    if (message != null) {
                        String content = message.get("content").asText();
                        return extractCode(content);
                    }
                }
                
                throw new RuntimeException("Invalid response format");
            });
        }
    }

    /**
     * Extract code from response
     */
    private static String extractCode(String response) {
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

    /**
     * Escape JSON string
     */
    private static String escapeJson(String text) {
        return text.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
    }

    /**
     * Validate generated test quality
     */
    private static int validateGeneratedTestQuality(String testCode, MethodContext context) {
        System.out.println("\n--- Generated Test Quality Report ---\n");

        int score = 0;

        // Check 1: Has imports
        if (testCode.contains("import ")) {
            System.out.println("✓ Has imports");
            score += 10;
        } else {
            System.out.println("✗ Missing imports");
        }

        // Check 2: Uses JUnit 5
        if (testCode.contains("@Test") && (testCode.contains("org.junit.jupiter") || testCode.contains("junit.jupiter"))) {
            System.out.println("✓ Uses JUnit 5");
            score += 15;
        } else {
            System.out.println("✗ Not using JUnit 5");
        }

        // Check 3: Uses AssertJ or assertions
        if (testCode.contains("assertThat") || testCode.contains("Assertions")) {
            System.out.println("✓ Uses assertions");
            score += 10;
        } else {
            System.out.println("✗ Missing assertions");
        }

        // Check 4: Tests success case
        if (testCode.toLowerCase().contains("success") || testCode.toLowerCase().contains("should_store")) {
            System.out.println("✓ Tests success case");
            score += 10;
        } else {
            System.out.println("✗ Missing success case");
        }

        // Check 5: Tests failure/error case
        if (testCode.toLowerCase().contains("exception") || testCode.toLowerCase().contains("throw") || 
            testCode.toLowerCase().contains("fail") || testCode.toLowerCase().contains("disabled")) {
            System.out.println("✓ Tests failure case");
            score += 10;
        } else {
            System.out.println("✗ Missing failure case");
        }

        // Check 6: Has @BeforeEach or setup
        if (testCode.contains("@BeforeEach") || testCode.contains("@Before") || testCode.contains("setUp")) {
            System.out.println("✓ Has setup method");
            score += 10;
        } else {
            System.out.println("✗ Missing setup method");
        }

        // Check 7: Mocks external dependencies
        if (testCode.contains("mock") || testCode.contains("Mock") || testCode.contains("when(")) {
            System.out.println("✓ Mocks dependencies");
            score += 10;
        } else {
            System.out.println("✗ Missing mocking");
        }

        // Check 8: Multiple test methods
        long testCount = testCode.split("@Test").length - 1;
        if (testCount >= 3) {
            System.out.println("✓ Has " + testCount + " test methods");
            score += 15;
        } else {
            System.out.println("⚠ Only " + testCount + " test methods (need 3+)");
            score += 5;
        }

        // Check 9: Proper test naming
        if (testCode.contains("should_") || testCode.contains("void should")) {
            System.out.println("✓ Proper test naming");
            score += 10;
        } else {
            System.out.println("⚠ Non-standard test naming");
            score += 5;
        }

        // Check 10: Code structure
        if (testCode.contains("class") && testCode.contains("{") && testCode.contains("}")) {
            System.out.println("✓ Valid code structure");
            score += 10;
        } else {
            System.out.println("✗ Invalid code structure");
        }

        System.out.println("\n--- Quality Score: " + score + "/100 ---");
        return score;
    }
}
