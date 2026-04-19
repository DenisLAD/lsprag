package com.reasoningtestgen;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reasoningtestgen.builder.ContextBuilder;
import com.reasoningtestgen.model.*;
import com.reasoningtestgen.service.PromptHistoryService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

/**
 * Simple test runner for all unit tests
 * Does not require JUnit - can be run standalone
 */
public class SimpleTestRunner {
    
    private static int passed = 0;
    private static int failed = 0;
    private static int totalTests = 0;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("Running Simple Unit Tests");
        System.out.println("========================================\n");

        try {
            // Test models
            testMethodContextCreation();
            testMethodContextSerialization();
            testParameterRecord();
            testCFGNodeCreation();
            testDependencyRecord();
            testDocContractRecord();
            testComplexityMetrics();
            
            // Test ContextBuilder
            testContextBuilderPromptGeneration();
            testContextBuilderSystemPrompt();
            testContextBuilderUserPrompt();
            testContextBuilderJSONSerialization();
            testContextBuilderWithEmptyContext();
            
            // Test PromptHistoryService
            testPromptHistoryServiceStorage();
            testPromptHistoryServicePersistence();
            testPromptHistoryServiceStatistics();
            
            // Summary
            System.out.println("\n========================================");
            System.out.println("Test Results");
            System.out.println("========================================");
            System.out.println("Total:   " + totalTests);
            System.out.println("Passed:  " + passed);
            System.out.println("Failed:  " + failed);
            System.out.println("========================================");
            
            if (failed > 0) {
                System.exit(1);
            } else {
                System.out.println("\n✓ All tests passed!");
                System.exit(0);
            }
        } catch (Exception e) {
            System.err.println("\n✗ Unexpected error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    // ===== Model Tests =====

    private static void testMethodContextCreation() {
        System.out.println("Test 1: MethodContext creation");
        try {
            MethodContext context = createSampleContext();
            
            assertEqual("OrderService", context.className(), "className");
            assertEqual("calculateDiscount", context.methodName(), "methodName");
            assertEqual("double", context.returnType(), "returnType");
            assertTrue(context.parameters().size() == 2, "parameters size == 2");
            assertTrue(context.dependencies().size() == 1, "dependencies size == 1");
            assertNotNull(context.docContract(), "docContract not null");
            assertEqual(4, context.complexity().cyclomatic(), "cyclomatic complexity");
            
            passTest();
        } catch (Exception e) {
            failTest(e);
        }
    }

    private static void testMethodContextSerialization() throws Exception {
        System.out.println("\nTest 2: MethodContext serialization");
        try {
            MethodContext context = createSampleContext();
            
            String json = objectMapper.writeValueAsString(context);
            assertTrue(json.length() > 0, "JSON not empty");
            assertTrue(json.contains("OrderService"), "JSON contains className");
            assertTrue(json.contains("calculateDiscount"), "JSON contains methodName");
            
            MethodContext deserialized = objectMapper.readValue(json, MethodContext.class);
            assertEqual(context.className(), deserialized.className(), "deserialized className");
            assertEqual(context.methodName(), deserialized.methodName(), "deserialized methodName");
            
            passTest();
        } catch (Exception e) {
            failTest(e);
        }
    }

    private static void testParameterRecord() {
        System.out.println("\nTest 3: Parameter record");
        try {
            Parameter param = new Parameter("user", "User", true);
            
            assertEqual("user", param.name(), "name");
            assertEqual("User", param.type(), "type");
            assertTrue(param.nullable(), "nullable");
            
            passTest();
        } catch (Exception e) {
            failTest(e);
        }
    }

    private static void testCFGNodeCreation() {
        System.out.println("\nTest 4: CFGNode creation");
        try {
            CFGNode ifNode = new CFGNode(CFGNode.NodeType.IF, "user == null", 35, 36, 38, null);
            
            assertEqual(CFGNode.NodeType.IF, ifNode.type(), "type");
            assertEqual("user == null", ifNode.condition(), "condition");
            assertEqual(Integer.valueOf(35), ifNode.line(), "line");
            assertEqual(Integer.valueOf(36), ifNode.thenLine(), "thenLine");
            assertEqual(Integer.valueOf(38), ifNode.elseLine(), "elseLine");
            
            // Test CATCH node
            CFGNode.CatchInfo catchInfo = new CFGNode.CatchInfo("Exception", 42);
            CFGNode catchNode = new CFGNode(CFGNode.NodeType.CATCH, null, 40, null, null, catchInfo);
            
            assertEqual(CFGNode.NodeType.CATCH, catchNode.type(), "catch type");
            assertNotNull(catchNode.catchBlock(), "catchBlock not null");
            assertEqual("Exception", catchNode.catchBlock().exceptionType(), "exception type");
            
            passTest();
        } catch (Exception e) {
            failTest(e);
        }
    }

    private static void testDependencyRecord() {
        System.out.println("\nTest 5: Dependency record");
        try {
            Dependency external = new Dependency("service", "ExternalService", true, false);
            Dependency internal = new Dependency("field", "String", false, false);
            
            assertTrue(external.isExternal(), "external isExternal");
            assertTrue(!internal.isExternal(), "internal not isExternal");
            
            passTest();
        } catch (Exception e) {
            failTest(e);
        }
    }

    private static void testDocContractRecord() {
        System.out.println("\nTest 6: DocContract record");
        try {
            DocContract contract = new DocContract(
                Map.of("user", "Description"),
                "Returns discount",
                List.of("IllegalArgumentException"),
                List.of("Must validate")
            );
            
            assertEqual(1, contract.params().size(), "params size");
            assertEqual("Returns discount", contract.returns(), "returns");
            assertEqual(1, contract.throwsList().size(), "throwsList size");
            assertEqual(1, contract.businessRules().size(), "businessRules size");
            
            passTest();
        } catch (Exception e) {
            failTest(e);
        }
    }

    private static void testComplexityMetrics() {
        System.out.println("\nTest 7: ComplexityMetrics record");
        try {
            ComplexityMetrics metrics = new ComplexityMetrics(9, 2, 9, 1);
            
            assertEqual(9, metrics.cyclomatic(), "cyclomatic");
            assertEqual(2, metrics.nestingDepth(), "nestingDepth");
            assertEqual(9, metrics.branchCount(), "branchCount");
            assertEqual(1, metrics.loopCount(), "loopCount");
            
            passTest();
        } catch (Exception e) {
            failTest(e);
        }
    }

    // ===== ContextBuilder Tests =====

    private static void testContextBuilderPromptGeneration() {
        System.out.println("\nTest 8: ContextBuilder prompt generation");
        try {
            MethodContext context = createSampleContext();
            ContextBuilder builder = new ContextBuilder();
            
            PromptBundle bundle = builder.buildPromptBundle(context);
            
            assertNotNull(bundle, "bundle not null");
            assertTrue(bundle.systemPrompt().length() > 0, "systemPrompt not empty");
            assertTrue(bundle.userPrompt().length() > 0, "userPrompt not empty");
            assertTrue(bundle.systemPrompt().contains("Senior Test Engineer"), "systemPrompt contains role");
            assertTrue(bundle.userPrompt().contains("OrderService"), "userPrompt contains className");
            assertTrue(bundle.userPrompt().contains("calculateDiscount"), "userPrompt contains methodName");
            assertTrue(bundle.userPrompt().contains("Зависимости") || bundle.userPrompt().contains("Dependencies"), "userPrompt contains dependencies");
            assertTrue(bundle.userPrompt().contains("Задача") || bundle.userPrompt().contains("Task"), "userPrompt contains task");
            
            passTest();
        } catch (Exception e) {
            failTest(e);
        }
    }

    private static void testContextBuilderSystemPrompt() {
        System.out.println("\nTest 9: ContextBuilder system prompt");
        try {
            MethodContext context = createSampleContext();
            ContextBuilder builder = new ContextBuilder();
            
            PromptBundle bundle = builder.buildPromptBundle(context);
            
            assertTrue(bundle.systemPrompt().contains("Senior Test Engineer"), "contains role");
            assertTrue(bundle.systemPrompt().contains("happy path") || bundle.systemPrompt().contains("основной сценарий"), "contains happy path");
            assertTrue(bundle.systemPrompt().contains("edge cases") || bundle.systemPrompt().contains("краевые"), "contains edge cases");
            
            passTest();
        } catch (Exception e) {
            failTest(e);
        }
    }

    private static void testContextBuilderUserPrompt() {
        System.out.println("\nTest 10: ContextBuilder user prompt");
        try {
            MethodContext context = createSampleContext();
            ContextBuilder builder = new ContextBuilder();
            
            PromptBundle bundle = builder.buildPromptBundle(context);
            
            assertTrue(bundle.userPrompt().contains("Сигнатура метода") || bundle.userPrompt().contains("Method Signature"), "contains signature section");
            assertTrue(bundle.userPrompt().contains("Граф потока") || bundle.userPrompt().contains("Control Flow"), "contains CFG section");
            assertTrue(bundle.userPrompt().contains("Метрики сложности") || bundle.userPrompt().contains("Complexity"), "contains complexity");
            
            passTest();
        } catch (Exception e) {
            failTest(e);
        }
    }

    private static void testContextBuilderJSONSerialization() {
        System.out.println("\nTest 11: ContextBuilder JSON serialization");
        try {
            MethodContext context = createSampleContext();
            ContextBuilder builder = new ContextBuilder();
            
            String json = builder.contextToJson(context);
            assertTrue(json.length() > 100, "JSON has content");
            assertTrue(json.contains("OrderService"), "JSON contains className");
            
            passTest();
        } catch (Exception e) {
            failTest(e);
        }
    }

    private static void testContextBuilderWithEmptyContext() {
        System.out.println("\nTest 12: ContextBuilder with empty context");
        try {
            MethodContext context = createEmptyContext();
            ContextBuilder builder = new ContextBuilder();
            
            PromptBundle bundle = builder.buildPromptBundle(context);
            
            assertNotNull(bundle, "bundle not null");
            assertTrue(bundle.systemPrompt().length() > 0, "systemPrompt not empty");
            assertTrue(bundle.examples().isEmpty(), "examples empty");
            
            passTest();
        } catch (Exception e) {
            failTest(e);
        }
    }

    // ===== PromptHistoryService Tests =====

    private static void testPromptHistoryServiceStorage() {
        System.out.println("\nTest 13: PromptHistoryService storage");
        try {
            Path tempDir = Files.createTempDirectory("prompt-history-test");
            PromptHistoryService service = new PromptHistoryService(tempDir.toString());
            
            // Store prompt
            service.storePrompt(
                PromptEntry.ReasoningStep.INTENT_ANALYSIS,
                "System prompt",
                "User prompt",
                "LLM response",
                "qwen/qwen3.5-9b",
                1500,
                true
            );
            
            List<PromptEntry> entries = service.getAllEntries();
            assertEqual(1, entries.size(), "entries count");
            
            PromptEntry entry = entries.get(0);
            assertEqual("System prompt", entry.systemPrompt(), "systemPrompt");
            assertEqual("User prompt", entry.userPrompt(), "userPrompt");
            assertEqual("LLM response", entry.llmResponse(), "llmResponse");
            assertEqual(1500L, entry.responseTimeMs(), "responseTimeMs");
            assertTrue(entry.success(), "success");
            
            // Test statistics
            PromptHistoryService.PromptHistoryStats stats = service.getStats();
            assertEqual(1, stats.totalEntries(), "stats totalEntries");
            assertEqual(1, stats.successfulResponses(), "stats successfulResponses");
            
            // Test persistence
            List<PromptEntry> loaded = service.loadFromDisk();
            assertTrue(loaded.size() >= 1, "loaded from disk");
            
            // Cleanup
            service.clearHistory();
            assertEqual(0, service.getAllEntries().size(), "entries after clear");
            
            passTest();
        } catch (Exception e) {
            failTest(e);
        }
    }

    private static void testPromptHistoryServicePersistence() {
        System.out.println("\nTest 14: PromptHistoryService persistence");
        try {
            Path tempDir = Files.createTempDirectory("prompt-persistence-test");
            PromptHistoryService service = new PromptHistoryService(tempDir.toString());
            
            // Store multiple entries
            for (int i = 0; i < 5; i++) {
                service.storePrompt(
                    PromptEntry.ReasoningStep.CODE_GENERATION,
                    "System " + i,
                    "User " + i,
                    "Response " + i,
                    "model",
                    1000L + i,
                    i % 2 == 0
                );
            }
            
            List<PromptEntry> entries = service.getAllEntries();
            assertEqual(5, entries.size(), "entries count");
            
            // Test filtering by step
            List<PromptEntry> filtered = service.getEntriesByStep(PromptEntry.ReasoningStep.CODE_GENERATION);
            assertEqual(5, filtered.size(), "filtered count");
            
            // Test recent entries
            List<PromptEntry> recent = service.getRecentEntries(3);
            assertTrue(recent.size() <= 3, "recent count <= 3");
            
            passTest();
        } catch (Exception e) {
            failTest(e);
        }
    }

    private static void testPromptHistoryServiceStatistics() {
        System.out.println("\nTest 15: PromptHistoryService statistics");
        try {
            Path tempDir = Files.createTempDirectory("prompt-stats-test");
            PromptHistoryService service = new PromptHistoryService(tempDir.toString());
            
            // Store mixed entries
            service.storePrompt(PromptEntry.ReasoningStep.INTENT_ANALYSIS, "sys", "user", "resp", "model", 1000, true);
            service.storePrompt(PromptEntry.ReasoningStep.SCENARIO_MAPPING, "sys", "user", "resp", "model", 2000, true);
            service.storePrompt(PromptEntry.ReasoningStep.CODE_GENERATION, "sys", "user", "resp", "model", 3000, false);
            
            PromptHistoryService.PromptHistoryStats stats = service.getStats();
            
            assertEqual(3, stats.totalEntries(), "totalEntries");
            assertEqual(2, stats.successfulResponses(), "successfulResponses");
            assertEqual(1, stats.failedResponses(), "failedResponses");
            assertTrue(stats.avgResponseTimeMs() > 0, "avgResponseTimeMs > 0");
            
            passTest();
        } catch (Exception e) {
            failTest(e);
        }
    }

    // ===== Helper Methods =====

    private static MethodContext createSampleContext() {
        List<Parameter> parameters = List.of(
            new Parameter("user", "User", true),
            new Parameter("items", "List<Item>", false)
        );

        List<Dependency> dependencies = List.of(
            new Dependency("pricingEngine", "PricingEngine", true, false)
        );

        DocContract docContract = new DocContract(
            Map.of("user", "The user"),
            "Discount percentage",
            List.of("IllegalArgumentException"),
            List.of("Must validate")
        );

        ComplexityMetrics complexity = new ComplexityMetrics(4, 2, 3, 1);

        List<CFGNode> cfgNodes = List.of(
            new CFGNode(CFGNode.NodeType.IF, "user == null", 10, 11, 14, null)
        );

        List<ExistingTestInfo> existingTests = List.of(
            new ExistingTestInfo("shouldApplyDiscount", List.of("assertEquals"), "JUNIT5", List.of())
        );

        return new MethodContext(
            "OrderService",
            "calculateDiscount",
            "double",
            parameters,
            List.of("@Transactional"),
            new MethodContext.ControlFlow(cfgNodes),
            dependencies,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            docContract,
            existingTests,
            complexity,
            false,
            false,
            false,
            "",
            null
        );
    }

    private static MethodContext createEmptyContext() {
        return new MethodContext(
            "EmptyService",
            "emptyMethod",
            "void",
            List.of(),
            List.of(),
            new MethodContext.ControlFlow(List.of()),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            new DocContract(Map.of(), null, List.of(), List.of()),
            List.of(),
            new ComplexityMetrics(1, 0, 0, 0),
            false,
            false,
            false,
            "",
            null
        );
    }

    // ===== Assertion Helpers =====

    private static void assertEqual(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(String.format(
                "Expected '%s' but got '%s' for %s", expected, actual, message
            ));
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("Condition not true: " + message);
        }
    }

    private static void assertNotNull(Object obj, String message) {
        if (obj == null) {
            throw new AssertionError("Object is null: " + message);
        }
    }

    private static void passTest() {
        passed++;
        totalTests++;
        System.out.println("  ✓ PASSED");
    }

    private static void failTest(Exception e) {
        failed++;
        totalTests++;
        System.out.println("  ✗ FAILED: " + e.getMessage());
        e.printStackTrace();
    }
}
