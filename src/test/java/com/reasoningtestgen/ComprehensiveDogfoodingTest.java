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
 * Comprehensive dogfooding test: Generate tests for multiple plugin classes
 * This validates the plugin works across different code patterns
 */
public class ComprehensiveDogfoodingTest {

    private static final String ENDPOINT = "http://localhost:1234/v1/chat/completions";
    private static final String MODEL = "qwen/qwen3.5-9b";
    private static final String OUTPUT_DIR = "build/comprehensive-dogfooding-output";

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("Comprehensive Plugin Dogfooding Test");
        System.out.println("Generating tests for 5 plugin methods");
        System.out.println("========================================\n");

        int totalPromptScore = 0;
        int totalTestScore = 0;
        int methodsTested = 0;

        try {
            Path outputDir = Paths.get(OUTPUT_DIR);
            Files.createDirectories(outputDir);

            // Test 1: ContextBuilder.buildPromptBundle()
            int score1 = testMethod(
                "ContextBuilder.buildPromptBundle()",
                createContextForContextBuilder(),
                outputDir.resolve("01-context-builder")
            );
            totalPromptScore += score1 / 2;
            totalTestScore += score1 / 2;
            methodsTested++;

            // Test 2: PSIExtractor.extractDependencies()
            int score2 = testMethod(
                "PSIExtractor.extractDependencies()",
                createContextForPSIExtractor(),
                outputDir.resolve("02-psi-extractor")
            );
            totalPromptScore += score2 / 2;
            totalTestScore += score2 / 2;
            methodsTested++;

            // Test 3: LLMProviderFactory.createProvider()
            int score3 = testMethod(
                "LLMProviderFactory.createProvider()",
                createContextForLLMFactory(),
                outputDir.resolve("03-llm-factory")
            );
            totalPromptScore += score3 / 2;
            totalTestScore += score3 / 2;
            methodsTested++;

            // Test 4: SelfCorrectionEngine.correctCode()
            int score4 = testMethod(
                "SelfCorrectionEngine.correctCode()",
                createContextForSelfCorrection(),
                outputDir.resolve("04-self-correction")
            );
            totalPromptScore += score4 / 2;
            totalTestScore += score4 / 2;
            methodsTested++;

            // Test 5: CompilationValidator.validateCompilation()
            int score5 = testMethod(
                "CompilationValidator.validateCompilation()",
                createContextForValidator(),
                outputDir.resolve("05-validator")
            );
            totalPromptScore += score5 / 2;
            totalTestScore += score5 / 2;
            methodsTested++;

            // Summary
            int avgPrompt = methodsTested > 0 ? totalPromptScore / methodsTested : 0;
            int avgTest = methodsTested > 0 ? totalTestScore / methodsTested : 0;
            
            System.out.println("\n========================================");
            System.out.println("Comprehensive Dogfooding Results");
            System.out.println("========================================");
            System.out.println("Methods tested:      " + methodsTested);
            System.out.println("Avg Prompt Quality:  " + avgPrompt + "/100");
            System.out.println("Avg Test Quality:    " + avgTest + "/100");
            System.out.println("Total Score:         " + (totalPromptScore + totalTestScore) + "/" + (methodsTested * 200));
            System.out.println("========================================");
            
            if (avgPrompt >= 80 && avgTest >= 70) {
                System.out.println("✓ Comprehensive dogfooding PASSED!");
                System.out.println("\nFiles saved to: " + outputDir.toAbsolutePath());
                System.exit(0);
            } else {
                System.out.println("⚠ Partial success - review generated tests");
                System.out.println("\nFiles saved to: " + outputDir.toAbsolutePath());
                System.exit(1);
            }

        } catch (Exception e) {
            System.err.println("✗ Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static int testMethod(String methodName, MethodContext context, Path outputDir) throws Exception {
        System.out.println("========== Test: " + methodName + " ==========\n");

        try {
            // Build prompt
            System.out.println("Step 1: Building prompt...");
            ContextBuilder contextBuilder = new ContextBuilder();
            PromptBundle bundle = contextBuilder.buildPromptBundle(context);
            String fullPrompt = bundle.systemPrompt() + "\n\n=== USER PROMPT ===\n\n" + bundle.userPrompt();
            
            Files.createDirectories(outputDir);
            Files.writeString(outputDir.resolve("01-context.json"), contextBuilder.contextToJson(context));
            Files.writeString(outputDir.resolve("02-full-prompt.txt"), fullPrompt);
            System.out.println("  Prompt: " + fullPrompt.length() + " chars");
            System.out.println("  ✓ Saved to: " + outputDir + "\n");

            // Validate prompt
            System.out.println("Step 2: Validating prompt...");
            int promptScore = validatePromptQuality(bundle, context);
            System.out.println("  Score: " + promptScore + "/100\n");

            // Generate test
            System.out.println("Step 3: Generating test...");
            String generatedTest = sendToLLMForTestGeneration(fullPrompt, context);
            
            Files.writeString(outputDir.resolve("03-generated-test.java"), generatedTest);
            System.out.println("  Test: " + generatedTest.length() + " chars, " + generatedTest.split("\n").length + " lines");
            System.out.println("  ✓ Saved\n");

            // Validate test
            System.out.println("Step 4: Validating test...");
            int testScore = validateGeneratedTestQuality(generatedTest, context);
            System.out.println("  Score: " + testScore + "/100\n");

            int totalScore = promptScore + testScore;
            System.out.println("✓ Test complete: Prompt=" + promptScore + ", Test=" + testScore + ", Total=" + totalScore + "\n");
            
            return totalScore;
            
        } catch (Exception e) {
            System.err.println("✗ Test failed: " + e.getMessage());
            e.printStackTrace();
            return 0;
        }
    }

    // ===== Context Creation Methods =====

    private static MethodContext createContextForContextBuilder() {
        return new MethodContext(
            "ContextBuilder",
            "buildPromptBundle",
            "PromptBundle",
            List.of(new Parameter("context", "MethodContext", false)),
            List.of(),
            new MethodContext.ControlFlow(List.of(
                new CFGNode(CFGNode.NodeType.IF, "context.docContract() != null", 90, 91, 105, null),
                new CFGNode(CFGNode.NodeType.IF, "!context.docContract().params().isEmpty()", 92, 93, 97, null),
                new CFGNode(CFGNode.NodeType.IF, "context.docContract().returns() != null", 97, 98, 100, null),
                new CFGNode(CFGNode.NodeType.IF, "!context.existingTests().isEmpty()", 135, 136, 148, null)
            )),
            List.of(
                new Dependency("objectMapper", "ObjectMapper", true, false)
            ),
            List.of(),
            List.of(
                new CalledMethodInfo("MethodContext", "docContract", "DocContract", List.of(), false, true, "", List.of()),
                new CalledMethodInfo("StringBuilder", "append", "StringBuilder", List.of("String str"), false, false, "", List.of()),
                new CalledMethodInfo("List", "forEach", "void", List.of("Consumer<? super T> action"), false, false, "", List.of())
            ),
            List.of(
                new DTOInfo("PromptBundle", "com.reasoningtestgen.model.PromptBundle",
                    List.of(
                        new DTOInfo.FieldInfo("systemPrompt", "String", List.of(), true, "getSystemPrompt()", "N/A"),
                        new DTOInfo.FieldInfo("userPrompt", "String", List.of(), true, "getUserPrompt()", "N/A"),
                        new DTOInfo.FieldInfo("examples", "List<String>", List.of(), true, "getExamples()", "N/A")
                    ),
                    List.of(), false, null)
            ),
            List.of(),
            new DocContract(
                Map.of("context", "The method context to build prompt from"),
                "PromptBundle containing system and user prompts",
                List.of(),
                List.of("Must include all context sections in user prompt")
            ),
            List.of(),
            new ComplexityMetrics(8, 2, 8, 0),
            false,
            false,
            false,
            "",
            null
        );
    }

    private static MethodContext createContextForPSIExtractor() {
        return new MethodContext(
            "PSIExtractor",
            "extractDependencies",
            "List<Dependency>",
            List.of(
                new Parameter("method", "PsiMethod", false),
                new Parameter("containingClass", "PsiClass", true)
            ),
            List.of("@NotNull"),
            new MethodContext.ControlFlow(List.of(
                new CFGNode(CFGNode.NodeType.IF, "containingClass != null", 85, 86, 95, null),
                new CFGNode(CFGNode.NodeType.LOOP, "foreach (field : containingClass.getFields())", 87, null, null, null),
                new CFGNode(CFGNode.NodeType.LOOP, "foreach (param : method.getParameterList().getParameters())", 105, null, null, null)
            )),
            List.of(),
            List.of(),
            List.of(
                new CalledMethodInfo("PsiClass", "getFields", "PsiField[]", List.of(), false, true, "PsiField[] getFields()", List.of()),
                new CalledMethodInfo("PsiMethod", "getParameterList", "PsiParameterList", List.of(), false, true, "", List.of()),
                new CalledMethodInfo("ArrayList", "add", "boolean", List.of("Dependency d"), false, false, "", List.of())
            ),
            List.of(
                new DTOInfo("Dependency", "com.reasoningtestgen.model.Dependency",
                    List.of(
                        new DTOInfo.FieldInfo("name", "String", List.of(), true, "name()", "N/A"),
                        new DTOInfo.FieldInfo("type", "String", List.of(), true, "type()", "N/A"),
                        new DTOInfo.FieldInfo("isExternal", "boolean", List.of(), true, "isExternal()", "N/A"),
                        new DTOInfo.FieldInfo("nullable", "boolean", List.of(), true, "nullable()", "N/A")
                    ),
                    List.of("record"), false, null)
            ),
            List.of(
                new DataTransformation("dependencies", "List<Dependency>",
                    List.of(new DataTransformation.TransformationStep(1, "add", "Adding dependencies from fields and params", "dependencies")),
                    "returned as List<Dependency>",
                    true,
                    Map.of("field", "from class fields", "param", "from method parameters")
                )
            ),
            new DocContract(
                Map.of(
                    "method", "The method to extract dependencies from",
                    "containingClass", "The class containing the method (may be null)"
                ),
                "List of Dependency objects representing method and class dependencies",
                List.of(),
                List.of(
                    "Should not return duplicate dependencies",
                    "Must handle null containingClass gracefully"
                )
            ),
            List.of(),
            new ComplexityMetrics(5, 1, 4, 2),
            false,
            false,
            false,
            "",
            null
        );
    }

    private static MethodContext createContextForLLMFactory() {
        return new MethodContext(
            "LLMProviderFactory",
            "createProvider",
            "LLMProvider",
            List.of(new Parameter("settings", "PluginSettings", false)),
            List.of("@NotNull"),
            new MethodContext.ControlFlow(List.of(
                new CFGNode(CFGNode.NodeType.SWITCH, "settings.getProviderType() -> case OPENAI", 25, null, null, null),
                new CFGNode(CFGNode.NodeType.SWITCH, "settings.getProviderType() -> case ANTHROPIC", 30, null, null, null),
                new CFGNode(CFGNode.NodeType.SWITCH, "settings.getProviderType() -> case OLLAMA", 33, null, null, null),
                new CFGNode(CFGNode.NodeType.SWITCH, "settings.getProviderType() -> case LM_STUDIO", 40, null, null, null),
                new CFGNode(CFGNode.NodeType.SWITCH, "settings.getProviderType() -> case CUSTOM", 51, null, null, null),
                new CFGNode(CFGNode.NodeType.IF, "endpoint.isEmpty()", 42, 43, 45, null),
                new CFGNode(CFGNode.NodeType.IF, "model.isEmpty()", 46, 47, 49, null)
            )),
            List.of(),
            List.of(),
            List.of(
                new CalledMethodInfo("PluginSettings", "getProviderType", "LLMProviderType", List.of(), false, true, "", List.of()),
                new CalledMethodInfo("PluginSettings", "getApiKey", "String", List.of(), false, true, "", List.of()),
                new CalledMethodInfo("PluginSettings", "getModel", "String", List.of(), false, true, "", List.of()),
                new CalledMethodInfo("PluginSettings", "getEndpoint", "String", List.of(), false, true, "", List.of()),
                new CalledMethodInfo("String", "isEmpty", "boolean", List.of(), false, false, "", List.of())
            ),
            List.of(),
            List.of(
                new DataTransformation("endpoint", "String",
                    List.of(new DataTransformation.TransformationStep(1, "check", "Checking if empty, using default", "endpoint")),
                    "default or from settings",
                    true,
                    Map.of("default", "http://localhost:1234/v1/chat/completions")
                )
            ),
            new DocContract(
                Map.of("settings", "Plugin settings containing provider configuration"),
                "LLMProvider instance configured according to settings",
                List.of("UnsupportedOperationException if provider not implemented"),
                List.of(
                    "Must handle all LLMProviderType enum values",
                    "Should use default values if not configured"
                )
            ),
            List.of(),
            new ComplexityMetrics(12, 2, 12, 0),
            false,
            false,
            false,
            "",
            null
        );
    }

    private static MethodContext createContextForSelfCorrection() {
        return new MethodContext(
            "SelfCorrectionEngine",
            "correctCode",
            "CorrectionResult",
            List.of(
                new Parameter("code", "GeneratedCode", false),
                new Parameter("design", "TestDesign", false),
                new Parameter("originalCode", "String", false)
            ),
            List.of("@NotNull"),
            new MethodContext.ControlFlow(List.of(
                new CFGNode(CFGNode.NodeType.LOOP, "for (attempt = 1; attempt <= maxAttempts; attempt++)", 60, null, null, null),
                new CFGNode(CFGNode.NodeType.IF, "errors.isEmpty()", 75, 76, 95, null),
                new CFGNode(CFGNode.NodeType.IF, "success", 120, 121, 126, null),
                new CFGNode(CFGNode.NodeType.TRY, "try { generateFix... }", 100, null, null, 
                    new CFGNode.CatchInfo("Exception", 115)),
                new CFGNode(CFGNode.NodeType.IF, "attempt == maxAttempts", 126, 127, 138, null)
            )),
            List.of(
                new Dependency("llmProvider", "LLMProvider", true, false),
                new Dependency("project", "Project", true, false),
                new Dependency("settings", "PluginSettings", true, false),
                new Dependency("promptHistoryService", "PromptHistoryService", true, true)
            ),
            List.of(),
            List.of(
                new CalledMethodInfo("CompilationValidator", "validateCompilation", "ValidationResult", List.of("String code"), false, true, "", List.of()),
                new CalledMethodInfo("LLMProvider", "chat", "String", List.of("String prompt", "String systemPrompt"), false, false, "", List.of()),
                new CalledMethodInfo("SelfCorrectionEngine", "analyzeErrors", "String", List.of("String code", "List<String> errors"), false, true, "", List.of()),
                new CalledMethodInfo("SelfCorrectionEngine", "generateFix", "String", List.of("String code", "String analysis"), false, true, "", List.of()),
                new CalledMethodInfo("PromptHistoryService", "storePrompt", "void", List.of("ReasoningStep", "String", "..."), false, true, "", List.of())
            ),
            List.of(
                new DTOInfo("CorrectionResult", "com.reasoningtestgen.refiner.SelfCorrectionEngine.CorrectionResult",
                    List.of(
                        new DTOInfo.FieldInfo("originalCode", "GeneratedCode", List.of(), true, "originalCode()", "N/A"),
                        new DTOInfo.FieldInfo("correctedCode", "String", List.of(), true, "correctedCode()", "N/A"),
                        new DTOInfo.FieldInfo("success", "boolean", List.of(), true, "success()", "N/A"),
                        new DTOInfo.FieldInfo("attemptsCount", "int", List.of(), true, "attemptsCount()", "N/A"),
                        new DTOInfo.FieldInfo("attempts", "List<CorrectionAttempt>", List.of(), true, "attempts()", "N/A"),
                        new DTOInfo.FieldInfo("remainingErrors", "List<String>", List.of(), true, "remainingErrors()", "N/A")
                    ),
                    List.of("record"), false, null)
            ),
            List.of(
                new DataTransformation("currentCode", "String",
                    List.of(
                        new DataTransformation.TransformationStep(1, "init", "Initialized with originalCode", "currentCode"),
                        new DataTransformation.TransformationStep(2, "reassign", "Updated with fixed code from LLM", "currentCode")
                    ),
                    "updated after each fix attempt",
                    true,
                    Map.of("fixedCode", "from generateFix()")
                )
            ),
            new DocContract(
                Map.of(
                    "code", "The generated code to correct",
                    "design", "Test design specifications",
                    "originalCode", "Original code before any corrections"
                ),
                "CorrectionResult with corrected code and success status",
                List.of(),
                List.of(
                    "Should attempt correction up to maxAttempts times",
                    "Must store all attempts for analysis",
                    "Should return best attempt even if all fail"
                )
            ),
            List.of(),
            new ComplexityMetrics(10, 3, 10, 1),
            false,
            false,
            false,
            "",
            null
        );
    }

    private static MethodContext createContextForValidator() {
        return new MethodContext(
            "CompilationValidator",
            "validateCompilation",
            "ValidationResult",
            List.of(new Parameter("code", "String", false)),
            List.of("@NotNull"),
            new MethodContext.ControlFlow(List.of(
                new CFGNode(CFGNode.NodeType.IF, "errors.isEmpty()", 45, 46, 50, null),
                new CFGNode(CFGNode.NodeType.TRY, "try { createPsiFile... }", 50, null, null, 
                    new CFGNode.CatchInfo("Exception", 65)),
                new CFGNode(CFGNode.NodeType.LOOP, "foreach (error : compilationErrors)", 70, null, null, null),
                new CFGNode(CFGNode.NodeType.IF, "validationResult.isValid()", 85, 86, 90, null)
            )),
            List.of(
                new Dependency("project", "Project", true, false)
            ),
            List.of(),
            List.of(
                new CalledMethodInfo("PsiFileFactory", "createFileFromText", "PsiFile", List.of("String", "FileType", "String"), true, false, "", List.of()),
                new CalledMethodInfo("CompilerManager", "compile", "void", List.of("PsiFile", "Callback"), false, false, "", List.of()),
                new CalledMethodInfo("ValidationResult", "isValid", "boolean", List.of(), false, true, "", List.of())
            ),
            List.of(
                new DTOInfo("ValidationResult", "com.reasoningtestgen.validator.CompilationValidator.ValidationResult",
                    List.of(
                        new DTOInfo.FieldInfo("isValid", "boolean", List.of(), true, "isValid()", "N/A"),
                        new DTOInfo.FieldInfo("errors", "List<CompilationError>", List.of(), true, "errors()", "N/A")
                    ),
                    List.of("record"), false, null)
            ),
            List.of(
                new DataTransformation("errors", "List<String>",
                    List.of(new DataTransformation.TransformationStep(1, "collect", "Collecting compilation errors", "errors")),
                    "returned in ValidationResult",
                    true,
                    Map.of("compilationError", "from compiler")
                )
            ),
            new DocContract(
                Map.of("code", "The Java code to validate"),
                "ValidationResult indicating if code compiles and any errors",
                List.of(),
                List.of(
                    "Should handle invalid Java code gracefully",
                    "Must return all compilation errors, not just first"
                )
            ),
            List.of(),
            new ComplexityMetrics(7, 2, 7, 1),
            false,
            false,
            false,
            "",
            null
        );
    }

    // ===== Validation Methods =====

    private static int validatePromptQuality(PromptBundle bundle, MethodContext context) {
        System.out.println("\n--- Prompt Quality Report ---\n");
        int score = 0;

        if (bundle.systemPrompt().contains("Senior Test Engineer")) {
            System.out.println("✓ System prompt defines role"); score += 10;
        } else { System.out.println("✗ System prompt missing role"); }

        String userPrompt = bundle.userPrompt();
        if (userPrompt.contains(context.className()) && userPrompt.contains(context.methodName())) {
            System.out.println("✓ User prompt has method signature"); score += 10;
        } else { System.out.println("✗ User prompt missing method signature"); }

        if (userPrompt.contains("Граф потока") || userPrompt.contains("Control Flow") || userPrompt.contains("if (") || userPrompt.contains("foreach")) {
            System.out.println("✓ Control flow graph included"); score += 15;
        } else { System.out.println("✗ Control flow graph missing"); }

        if (userPrompt.contains("Зависимости") || userPrompt.contains("Dependencies")) {
            System.out.println("✓ Dependencies included"); score += 10;
        } else { System.out.println("✗ Dependencies missing"); }

        if (userPrompt.contains("Документация") || userPrompt.contains("Documentation") || userPrompt.contains("Параметры")) {
            System.out.println("✓ Documentation contract included"); score += 15;
        } else { System.out.println("✗ Documentation contract missing"); }

        if (context.calledMethods() != null && !context.calledMethods().isEmpty()) {
            if (userPrompt.contains("Вызываемые") || userPrompt.contains("Called")) {
                System.out.println("✓ Called methods included"); score += 10;
            } else { System.out.println("✗ Called methods missing"); }
        } else { score += 10; System.out.println("✓ Called methods N/A"); }

        if (context.dtoStructures() != null && !context.dtoStructures().isEmpty()) {
            if (userPrompt.contains("DTO") || userPrompt.contains("POJO")) {
                System.out.println("✓ DTO structures included"); score += 10;
            } else { System.out.println("✗ DTO structures missing"); }
        } else { score += 10; System.out.println("✓ DTO structures N/A"); }

        int totalLength = bundle.systemPrompt().length() + bundle.userPrompt().length();
        if (totalLength > 500) {
            System.out.println("✓ Prompt has sufficient detail (" + totalLength + " chars)"); score += 10;
        } else { System.out.println("✗ Prompt too short"); }

        if (userPrompt.contains("Задача") || userPrompt.contains("Task") || userPrompt.contains("Сгенерируйте")) {
            System.out.println("✓ Clear task instruction"); score += 10;
        } else { System.out.println("✗ Task instruction unclear"); }

        if (userPrompt.contains("##") && userPrompt.contains("\n\n")) {
            System.out.println("✓ Well-formatted prompt"); score += 10;
        } else { System.out.println("✗ Poor formatting"); }

        System.out.println("\n--- Quality Score: " + score + "/100 ---");
        return score;
    }

    private static String sendToLLMForTestGeneration(String prompt, MethodContext context) throws Exception {
        String enhancedPrompt = prompt + "\n\n" + """
            Please generate a complete JUnit 5 test class for this method.
            
            Requirements:
            - Use JUnit 5 (@Test, @BeforeEach, @DisplayName, @Nested)
            - Use AssertJ for assertions (assertThat, assertThatThrownBy)
            - Use Mockito for mocking (mock, when, verify)
            - Test all scenarios based on the CFG
            - Test success, failure, and edge cases
            - Follow naming: should_{expected}_when_{condition}
            
            Return ONLY the Java code, no explanations or markdown.
            Start with package and imports, end with closing brace.
            """;

        String systemPrompt = "Вы — Senior Test Engineer. Сгенерируйте ПРОИЗВОДСТВЕННО-ГОТОВЫЕ unit-тесты для Java метода. Используйте JUnit 5, Mockito, AssertJ.";

        return sendToLLM(enhancedPrompt, systemPrompt);
    }

    private static String sendToLLM(String userPrompt, String systemPrompt) throws Exception {
        RequestConfig requestConfig = RequestConfig.custom()
            .setConnectionRequestTimeout(Timeout.ofSeconds(120))
            .setResponseTimeout(Timeout.ofSeconds(120))
            .build();

        String requestBody = String.format(
            "{\"model\":\"%s\",\"messages\":[{\"role\":\"system\",\"content\":\"%s\"},{\"role\":\"user\",\"content\":\"%s\"}],\"temperature\":0.3,\"max_tokens\":4096}",
            MODEL, escapeJson(systemPrompt), escapeJson(userPrompt)
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
                        return extractCode(message.get("content").asText());
                    }
                }
                
                throw new RuntimeException("Invalid response format");
            });
        }
    }

    private static String extractCode(String response) {
        int startIndex = response.indexOf("```java");
        if (startIndex == -1) startIndex = response.indexOf("```");
        
        if (startIndex != -1) {
            int codeStart = response.indexOf('\n', startIndex) + 1;
            int endIndex = response.indexOf("```", codeStart);
            if (endIndex != -1) {
                return response.substring(codeStart, endIndex).trim();
            }
        }
        return response;
    }

    private static String escapeJson(String text) {
        return text.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
    }

    private static int validateGeneratedTestQuality(String testCode, MethodContext context) {
        System.out.println("\n--- Generated Test Quality Report ---\n");
        int score = 0;

        if (testCode.contains("import ")) { System.out.println("✓ Has imports"); score += 10;
        } else { System.out.println("✗ Missing imports"); }

        if (testCode.contains("@Test") && (testCode.contains("org.junit.jupiter") || testCode.contains("junit.jupiter"))) {
            System.out.println("✓ Uses JUnit 5"); score += 15;
        } else { System.out.println("✗ Not using JUnit 5"); }

        if (testCode.contains("assertThat") || testCode.contains("Assertions")) {
            System.out.println("✓ Uses assertions"); score += 10;
        } else { System.out.println("✗ Missing assertions"); }

        if (testCode.toLowerCase().contains("success") || testCode.contains("should_")) {
            System.out.println("✓ Tests success case"); score += 10;
        } else { System.out.println("✗ Missing success case"); }

        if (testCode.toLowerCase().contains("exception") || testCode.toLowerCase().contains("throw") || 
            testCode.toLowerCase().contains("fail") || testCode.toLowerCase().contains("error")) {
            System.out.println("✓ Tests failure case"); score += 10;
        } else { System.out.println("✗ Missing failure case"); }

        if (testCode.contains("@BeforeEach") || testCode.contains("@Before") || testCode.contains("setUp")) {
            System.out.println("✓ Has setup method"); score += 10;
        } else { System.out.println("✗ Missing setup method"); }

        if (testCode.contains("mock") || testCode.contains("Mock") || testCode.contains("when(")) {
            System.out.println("✓ Mocks dependencies"); score += 10;
        } else { System.out.println("✗ Missing mocking"); }

        long testCount = testCode.split("@Test").length - 1;
        if (testCount >= 3) { System.out.println("✓ Has " + testCount + " test methods"); score += 15;
        } else { System.out.println("⚠ Only " + testCount + " test methods"); score += 5; }

        if (testCode.contains("should_") || testCode.contains("void should")) {
            System.out.println("✓ Proper test naming"); score += 10;
        } else { System.out.println("⚠ Non-standard test naming"); score += 5; }

        if (testCode.contains("class") && testCode.contains("{") && testCode.contains("}")) {
            System.out.println("✓ Valid code structure"); score += 10;
        } else { System.out.println("✗ Invalid code structure"); }

        System.out.println("\n--- Quality Score: " + score + "/100 ---");
        return score;
    }
}
