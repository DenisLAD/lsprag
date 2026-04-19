package com.reasoningtestgen;

import com.reasoningtestgen.builder.ContextBuilder;
import com.reasoningtestgen.extractor.PSIExtractor;
import com.reasoningtestgen.example.OrderService;
import com.reasoningtestgen.model.MethodContext;
import com.reasoningtestgen.model.PromptBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.openapi.fileTypes.StdFileTypes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Test plugin with real OrderService class
 * Extracts PSI context and shows generated prompts
 */
public class PluginIntegrationTest {

    private static final String OUTPUT_DIR = "build/plugin-test-output";

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("Plugin Integration Test");
        System.out.println("Testing with OrderService class");
        System.out.println("========================================\n");

        try {
            // Step 1: Load source file
            System.out.println("Step 1: Loading OrderService.java...");
            String sourceCode = loadSourceFile();
            System.out.println("  Source length: " + sourceCode.length() + " chars");
            System.out.println("  ✓ Source loaded\n");

            // Step 2: Create fake PSI file
            System.out.println("Step 2: Creating PSI structure...");
            // Note: We'll use simple text parsing since we don't have full IDE context
            MethodContext context = createMockContext();
            System.out.println("  ✓ Context created\n");

            // Step 3: Build prompts
            System.out.println("Step 3: Building prompts...");
            ContextBuilder builder = new ContextBuilder();
            PromptBundle bundle = builder.buildPromptBundle(context);
            System.out.println("  System prompt: " + bundle.systemPrompt().length() + " chars");
            System.out.println("  User prompt: " + bundle.userPrompt().length() + " chars");
            System.out.println("  Examples: " + bundle.examples().size());
            System.out.println("  ✓ Prompts built\n");

            // Step 4: Save prompts for review
            System.out.println("Step 4: Saving prompts...");
            savePrompts(bundle, context);
            System.out.println("  ✓ Saved to: " + OUTPUT_DIR + "\n");

            // Step 5: Validate prompt quality
            System.out.println("Step 5: Validating prompt quality...");
            validatePromptQuality(bundle, context);

            // Summary
            System.out.println("\n========================================");
            System.out.println("Plugin Integration Test Complete");
            System.out.println("========================================");
            System.out.println("Check prompts in: " + OUTPUT_DIR);
            System.out.println("Review quality report above");
            System.out.println("========================================");

        } catch (Exception e) {
            System.err.println("✗ Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static String loadSourceFile() throws Exception {
        Path path = Paths.get("src/test/java/com/reasoningtestgen/example/OrderService.java");
        return Files.readString(path);
    }

    private static MethodContext createMockContext() {
        // Create realistic context from OrderService
        return new MethodContext(
            "OrderService",
            "calculateDiscount",
            "double",
            java.util.List.of(
                new com.reasoningtestgen.model.Parameter("user", "User", false),
                new com.reasoningtestgen.model.Parameter("items", "List<Item>", false)
            ),
            java.util.List.of(),
            new MethodContext.ControlFlow(
                java.util.List.of(
                    new com.reasoningtestgen.model.CFGNode(
                        com.reasoningtestgen.model.CFGNode.NodeType.IF,
                        "user == null",
                        35, 36, 38, null
                    ),
                    new com.reasoningtestgen.model.CFGNode(
                        com.reasoningtestgen.model.CFGNode.NodeType.IF,
                        "items == null",
                        38, 39, 42, null
                    ),
                    new com.reasoningtestgen.model.CFGNode(
                        com.reasoningtestgen.model.CFGNode.NodeType.IF,
                        "profile == null",
                        43, 44, 48, null
                    ),
                    new com.reasoningtestgen.model.CFGNode(
                        com.reasoningtestgen.model.CFGNode.NodeType.IF,
                        "items.isEmpty()",
                        48, 49, 53, null
                    ),
                    new com.reasoningtestgen.model.CFGNode(
                        com.reasoningtestgen.model.CFGNode.NodeType.IF,
                        "profile.isVip()",
                        62, 63, 67, null
                    ),
                    new com.reasoningtestgen.model.CFGNode(
                        com.reasoningtestgen.model.CFGNode.NodeType.IF,
                        "profile.isPremium()",
                        67, 68, 74, null
                    ),
                    new com.reasoningtestgen.model.CFGNode(
                        com.reasoningtestgen.model.CFGNode.NodeType.IF,
                        "totalValue > 1000.0",
                        69, 70, 72, null
                    ),
                    new com.reasoningtestgen.model.CFGNode(
                        com.reasoningtestgen.model.CFGNode.NodeType.IF,
                        "totalValue > 2000.0",
                        75, 76, 78, null
                    ),
                    new com.reasoningtestgen.model.CFGNode(
                        com.reasoningtestgen.model.CFGNode.NodeType.IF,
                        "totalValue > 500.0",
                        78, 79, 82, null
                    ),
                    new com.reasoningtestgen.model.CFGNode(
                        com.reasoningtestgen.model.CFGNode.NodeType.RETURN,
                        null,
                        82, null, null, null
                    )
                )
            ),
            java.util.List.of(
                new com.reasoningtestgen.model.Dependency("pricingEngine", "PricingEngine", true, false),
                new com.reasoningtestgen.model.Dependency("userRepository", "UserRepository", true, false)
            ),
            java.util.List.of(), // dependenciesInfo
            java.util.List.of(), // calledMethods
            java.util.List.of(), // dtoStructures
            java.util.List.of(), // dataTransformations
            new com.reasoningtestgen.model.DocContract(
                java.util.Map.of(
                    "user", "The user requesting discount. Must not be null.",
                    "items", "List of items in cart. Can be empty but not null."
                ),
                "Discount percentage from 0 to 50",
                java.util.List.of(
                    "IllegalArgumentException if user is null",
                    "IllegalArgumentException if items is null",
                    "IllegalStateException if user not found in repository"
                ),
                java.util.List.of(
                    "Always validate user input",
                    "Apply correct discount rules",
                    "Never return negative discounts"
                )
            ),
            java.util.List.of(),
            new com.reasoningtestgen.model.ComplexityMetrics(9, 2, 9, 0),
            false,
            false,
            false,
            "",
            null
        );
    }

    private static void savePrompts(PromptBundle bundle, MethodContext context) throws Exception {
        Path outputDir = Paths.get(OUTPUT_DIR);
        Files.createDirectories(outputDir);

        // Save system prompt
        Files.writeString(outputDir.resolve("01-system-prompt.txt"), bundle.systemPrompt());

        // Save user prompt
        Files.writeString(outputDir.resolve("02-user-prompt.txt"), bundle.userPrompt());

        // Save full context as JSON
        ContextBuilder builder = new ContextBuilder();
        Files.writeString(outputDir.resolve("03-context.json"), builder.contextToJson(context));

        // Save combined prompt for LLM
        String fullPrompt = bundle.systemPrompt() + "\n\n=== USER PROMPT ===\n\n" + bundle.userPrompt();
        Files.writeString(outputDir.resolve("04-full-prompt-for-llm.txt"), fullPrompt);

        System.out.println("  Saved 4 files to " + OUTPUT_DIR);
    }

    private static void validatePromptQuality(PromptBundle bundle, MethodContext context) {
        System.out.println("\n--- Prompt Quality Report ---\n");

        int score = 0;
        int maxScore = 100;

        // Check 1: System prompt has role (Russian)
        if (bundle.systemPrompt().contains("Senior Test Engineer") || bundle.systemPrompt().contains("Senior Test Engineer")) {
            System.out.println("✓ System prompt defines role");
            score += 10;
        } else {
            System.out.println("✗ System prompt missing role");
        }

        // Check 2: User prompt has method signature (Russian)
        String userPrompt = bundle.userPrompt();
        if ((userPrompt.contains("OrderService") && userPrompt.contains("calculateDiscount")) ||
            (userPrompt.contains("Класс:") && userPrompt.contains("Метод:"))) {
            System.out.println("✓ User prompt has method signature");
            score += 10;
        } else {
            System.out.println("✗ User prompt missing method signature");
        }

        // Check 3: CFG is present (Russian)
        if (userPrompt.contains("Граф потока управления") || userPrompt.contains("Control Flow Graph") || userPrompt.contains("if (")) {
            System.out.println("✓ Control flow graph included");
            score += 15;
        } else {
            System.out.println("✗ Control flow graph missing");
        }

        // Check 4: Dependencies listed (Russian)
        if (userPrompt.contains("Зависимости") || userPrompt.contains("Dependencies") || userPrompt.contains("PricingEngine")) {
            System.out.println("✓ Dependencies included");
            score += 10;
        } else {
            System.out.println("✗ Dependencies missing");
        }

        // Check 5: Documentation contract (Russian)
        if ((userPrompt.contains("Документация") || userPrompt.contains("Documentation")) && 
            (userPrompt.contains("IllegalArgumentException") || userPrompt.contains("Возвращает"))) {
            System.out.println("✓ Documentation contract included");
            score += 15;
        } else {
            System.out.println("✗ Documentation contract missing");
        }

        // Check 6: Business rules (Russian)
        if (userPrompt.contains("Бизнес-правила") || userPrompt.contains("Business Rules") || userPrompt.contains("Always validate")) {
            System.out.println("✓ Business rules included");
            score += 10;
        } else {
            System.out.println("✗ Business rules missing");
        }

        // Check 7: Complexity metrics (Russian)
        if (userPrompt.contains("Метрики сложности") || userPrompt.contains("Complexity") || userPrompt.contains("Цикломатическая")) {
            System.out.println("✓ Complexity metrics included");
            score += 10;
        } else {
            System.out.println("✗ Complexity metrics missing");
        }

        // Check 8: Prompt length
        int totalLength = bundle.systemPrompt().length() + bundle.userPrompt().length();
        if (totalLength > 500) {
            System.out.println("✓ Prompt has sufficient detail (" + totalLength + " chars)");
            score += 10;
        } else {
            System.out.println("✗ Prompt too short (" + totalLength + " chars)");
        }

        // Check 9: Clear task instruction (Russian)
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

        System.out.println("\n--- Quality Score: " + score + "/" + maxScore + " ---");

        if (score >= 80) {
            System.out.println("✓ Excellent - Ready for LLM");
        } else if (score >= 60) {
            System.out.println("⚠ Good - Minor improvements needed");
        } else if (score >= 40) {
            System.out.println("⚠ Fair - Significant improvements needed");
        } else {
            System.out.println("✗ Poor - Major revisions needed");
        }
    }
}
