package com.reasoningtestgen.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellij.openapi.progress.ProgressIndicator;
import com.reasoningtestgen.llm.LLMProvider;
import com.reasoningtestgen.model.CFGNode;
import com.reasoningtestgen.model.MethodContext;
import com.reasoningtestgen.model.Parameter;
import com.reasoningtestgen.model.TestPlan;
import com.reasoningtestgen.model.TestPlan.TestMethodPlan;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Uses LLM to analyze prompt and create a detailed test plan before code generation
 * This intermediate step improves code quality by forcing LLM to think before coding
 */
public class TestPlanningService {

    private static final Logger LOG = LoggerFactory.getLogger(TestPlanningService.class);
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Generate test plan using LLM reasoning
     * @param context Method context with CFG, dependencies, etc.
     * @param llmProvider LLM provider to use
     * @param indicator Progress indicator (can be null)
     * @return Detailed test plan
     */
    @NotNull
    public TestPlan generatePlan(@NotNull MethodContext context,
                                   @NotNull LLMProvider llmProvider,
                                   @Nullable ProgressIndicator indicator) {
        try {
            if (indicator != null) {
                indicator.setText("Generating test plan...");
            }

            String prompt = buildPlanningPrompt(context);
            String systemPrompt = buildPlanningSystemPrompt();
            
            String response = llmProvider.chat(prompt, systemPrompt);
            
            if (indicator != null) {
                indicator.setText("Parsing test plan...");
            }

            return parsePlanFromResponse(response, context);
            
        } catch (Exception e) {
            LOG.error("Failed to generate test plan", e);
            // Fallback to basic plan
            return createFallbackPlan(context);
        }
    }

    /**
     * Generate plan directly from prompt text (when MethodContext not available)
     * Extracts info from the prompt to create a plan
     */
    @NotNull
    public TestPlan generatePlanFromPrompt(@NotNull String prompt,
                                             @NotNull LLMProvider llmProvider,
                                             @Nullable ProgressIndicator indicator) {
        try {
            if (indicator != null) {
                indicator.setText("Analyzing prompt for planning...");
            }

            String planningPrompt = "Based on the following method context, create a detailed test plan.\n\n" + prompt;
            String response = llmProvider.chat(planningPrompt, buildPlanningSystemPrompt());
            
            // Create minimal context for parsing
            MethodContext minimalContext = extractContextFromPrompt(prompt);
            return parsePlanFromResponse(response, minimalContext);
            
        } catch (Exception e) {
            LOG.error("Failed to generate test plan from prompt", e);
            MethodContext minimalContext = extractContextFromPrompt(prompt);
            return createFallbackPlan(minimalContext);
        }
    }

    /**
     * Extract minimal context information from prompt for fallback planning
     */
    @NotNull
    private MethodContext extractContextFromPrompt(@NotNull String prompt) {
        // Extract class and method name if present
        String className = "UnknownClass";
        String methodName = "unknownMethod";
        
        if (prompt.contains("Класс:")) {
            int start = prompt.indexOf("Класс:") + 6;
            int end = prompt.indexOf("\n", start);
            if (end > start) className = prompt.substring(start, end).trim();
        }
        if (prompt.contains("Метод:")) {
            int start = prompt.indexOf("Метод:") + 6;
            int end = prompt.indexOf("\n", start);
            if (end > start) methodName = prompt.substring(start, end).trim();
        }
        
        // Create minimal context with empty lists
        return new MethodContext(
            className, methodName, "void", List.of(), List.of(),
            new MethodContext.ControlFlow(List.of()),
            List.of(), List.of(), List.of(), List.of(), List.of(),
            null, List.of(), null, false, false, false, null, null
        );
    }

    /**
     * Build system prompt for planning
     */
    @NotNull
    private String buildPlanningSystemPrompt() {
        return """
            Вы — Senior Test Architect. Ваша задача — создать детальный план тестирования.
            
            ВЫ ДОЛЖНЫ вернуть план в формате JSON со следующей структурой:
            {
              "testMethods": [
                {
                  "name": "should_X_when_Y",
                  "description": "русское описание",
                  "branch": "какую ветку CFG проверяет",
                  "givenSetup": "что подготовить",
                  "whenAction": "что вызвать",
                  "thenAssertions": ["утверждение 1", "утверждение 2"],
                  "isParameterized": false,
                  "parameterizedData": ""
                }
              ],
              "requiredMocks": ["DependencyType dependencyName"],
              "requiredImports": ["org.junit...", "static org.assertj..."],
              "testStructure": "NESTED_CLASSES или FLAT",
              "notes": "дополнительные заметки"
            }
            
            НЕ добавляйте markdown или ```json. ТОЛЬКО чистый JSON.
            """;
    }

    /**
     * Build user prompt with context
     */
    @NotNull
    private String buildPlanningPrompt(@NotNull MethodContext context) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("Создай детальный план тестирования для метода:\n\n");
        
        prompt.append("## Сигнатура\n");
        prompt.append("Класс: ").append(context.className()).append("\n");
        prompt.append("Метод: ").append(context.methodName()).append("\n");
        prompt.append("Возврат: ").append(context.returnType()).append("\n");
        prompt.append("Параметры: ").append(formatParameters(context.parameters())).append("\n\n");
        
        if (context.sourceCode() != null && !context.sourceCode().isEmpty()) {
            prompt.append("## Исходный код\n```java\n");
            prompt.append(context.sourceCode()).append("\n```\n\n");
        }
        
        // CFG branches that need coverage
        List<CFGNode> branchNodes = context.controlFlow().nodes().stream()
            .filter(n -> n.type() == CFGNode.NodeType.IF || 
                        n.type() == CFGNode.NodeType.SWITCH ||
                        n.type() == CFGNode.NodeType.CATCH)
            .collect(Collectors.toList());
        
        if (!branchNodes.isEmpty()) {
            prompt.append("## Ветки требующие покрытия\n");
            int i = 1;
            for (CFGNode node : branchNodes) {
                switch (node.type()) {
                    case IF:
                        prompt.append(i++).append(". if (").append(node.condition()).append(") → TRUE\n");
                        prompt.append(i++).append(". if (").append(node.condition()).append(") → FALSE\n");
                        break;
                    case SWITCH:
                        prompt.append(i++).append(". switch (").append(node.condition()).append(") → each case\n");
                        break;
                    case CATCH:
                        prompt.append(i++).append(". catch (").append(node.condition()).append(")\n");
                        break;
                }
            }
            prompt.append("\n");
        }
        
        // Dependencies
        if (!context.dependencies().isEmpty()) {
            prompt.append("## Зависимости (нужно замокать)\n");
            for (var dep : context.dependencies()) {
                prompt.append("- ").append(dep.name()).append(": ").append(dep.type());
                if (dep.isExternal()) prompt.append(" [external]");
                prompt.append("\n");
            }
            prompt.append("\n");
        }
        
        prompt.append("## Требования\n");
        prompt.append("- Создай тест для КАЖДОЙ ветки\n");
        prompt.append("- Используй @ParameterizedTest если есть повторяющаяся логика\n");
        prompt.append("- Добавь @DisplayName на русском\n");
        prompt.append("- Верни ТОЛЬКО JSON план\n");
        
        return prompt.toString();
    }

    /**
     * Parse JSON response into TestPlan
     */
    @NotNull
    private TestPlan parsePlanFromResponse(@NotNull String response, @NotNull MethodContext context) {
        try {
            // Extract JSON from response
            String json = extractJson(response);
            JsonNode root = mapper.readTree(json);
            
            List<TestMethodPlan> methods = new ArrayList<>();
            JsonNode methodsNode = root.get("testMethods");
            if (methodsNode != null && methodsNode.isArray()) {
                for (JsonNode method : methodsNode) {
                    methods.add(new TestMethodPlan(
                        get(method, "name", "test_method"),
                        get(method, "description", ""),
                        get(method, "branch", ""),
                        get(method, "givenSetup", "Given setup"),
                        get(method, "whenAction", "When action"),
                        getArrayAsList(method, "thenAssertions"),
                        getBoolean(method, "isParameterized", false),
                        get(method, "parameterizedData", "")
                    ));
                }
            }
            
            return new TestPlan(
                methods,
                getArrayAsList(root, "requiredMocks"),
                getArrayAsList(root, "requiredImports"),
                get(root, "testStructure", "NESTED_CLASSES"),
                get(root, "notes", "")
            );
            
        } catch (Exception e) {
            LOG.error("Failed to parse test plan JSON", e);
            return createFallbackPlan(context);
        }
    }

    /**
     * Extract JSON from LLM response (handles markdown code blocks)
     */
    @NotNull
    private String extractJson(@NotNull String response) {
        int start = response.indexOf("{");
        int end = response.lastIndexOf("}");
        if (start >= 0 && end > start) {
            return response.substring(start, end + 1);
        }
        return response;
    }

    /**
     * Fallback plan if LLM planning fails
     */
    @NotNull
    private TestPlan createFallbackPlan(@NotNull MethodContext context) {
        List<TestMethodPlan> methods = new ArrayList<>();
        
        // Create basic plan from CFG
        List<CFGNode> branchNodes = context.controlFlow().nodes().stream()
            .filter(n -> n.type() == CFGNode.NodeType.IF || 
                        n.type() == CFGNode.NodeType.SWITCH ||
                        n.type() == CFGNode.NodeType.CATCH)
            .collect(Collectors.toList());
        
        if (branchNodes.isEmpty()) {
            methods.add(new TestMethodPlan(
                "should_execute_successfully",
                "Базовый тест успешного выполнения",
                "Happy path",
                "Подготовить валидные данные",
                "Вызвать " + context.methodName(),
                List.of("Результат не null", "Статус SUCCESS"),
                false,
                ""
            ));
        } else {
            for (CFGNode node : branchNodes) {
                methods.add(new TestMethodPlan(
                    "should_handle_" + node.type().name().toLowerCase(),
                    "Тест ветки: " + node.condition(),
                    node.condition(),
                    "Подготовить данные для ветки",
                    "Вызвать " + context.methodName(),
                    List.of("Проверить результат"),
                    false,
                    ""
                ));
            }
        }
        
        return new TestPlan(methods, List.of(), List.of(), "NESTED_CLASSES", "");
    }

    // Helper methods
    private String get(JsonNode node, String field, String defaultVal) {
        JsonNode f = node.get(field);
        return f != null ? f.asText() : defaultVal;
    }
    
    private boolean getBoolean(JsonNode node, String field, boolean defaultVal) {
        JsonNode f = node.get(field);
        return f != null ? f.asBoolean() : defaultVal;
    }
    
    private List<String> getArrayAsList(JsonNode node, String field) {
        List<String> list = new ArrayList<>();
        JsonNode arr = node.get(field);
        if (arr != null && arr.isArray()) {
            for (JsonNode el : arr) list.add(el.asText());
        }
        return list;
    }

    private String getArray(JsonNode node, String field) {
        List<String> arr = getArrayAsList(node, field);
        return String.join(", ", arr);
    }

    private String formatParameters(List<Parameter> params) {
        if (params == null || params.isEmpty()) return "none";
        return params.stream()
            .map(p -> p.name() + ": " + p.type())
            .collect(Collectors.joining(", "));
    }
}
