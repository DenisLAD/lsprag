package com.reasoningtestgen.builder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.reasoningtestgen.model.*;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Builds LLM prompts from MethodContext
 * According to ANALYTICS.md Section 5.2
 */
public class ContextBuilder {

    private static final int MAX_EXAMPLES = 3;
    private final ObjectMapper objectMapper;

    public ContextBuilder() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    /**
     * Build complete prompt bundle for LLM reasoning pipeline
     */
    @NotNull
    public PromptBundle buildPromptBundle(@NotNull MethodContext context) {
        String systemPrompt = buildSystemPrompt();
        String userPrompt = buildUserPrompt(context);
        List<String> examples = extractExamples(context.existingTests());
        
        return new PromptBundle(systemPrompt, userPrompt, examples);
    }

    /**
     * Build system prompt defining the role and expectations
     */
    @NotNull
    private String buildSystemPrompt() {
        return """
            Вы — Senior Test Engineer, специализирующийся на создании высококачественных unit-тестов для Java-приложений.

            ВАЖНО: Вы должны вернуть ТОЛЬКО Java код тестового класса. НЕ JSON, НЕ описание, НЕ markdown.
            
            Ваша задача:
            1. Проанализировать назначение метода, контракт и поведение
            2. Спроектировать комплексные тестовые сценарии, покрывающие все ветки
            3. Сгенерировать готовый к использованию Java код теста

            Формат вывода:
            - Верните ТОЛЬКО Java код, начиная с package declaration
            - НЕ включайте JSON, markdown, или текстовые описания
            - НЕ включайте объяснений или комментариев о том что вы делаете
            - Начните с: package ...
            - Закройте последней }: класса

            Руководство:
            - Всегда покрывайте основной сценарий (happy path), граничные случаи, обработку ошибок и краевые условия
            - Используйте описательные имена тестовых методов по соглашению: should_{ожидание}_when_{условие}
            - Пишите изолированные, повторяемые тесты с правильной подготовкой и очисткой
            - Включайте осмысленные тестовые данные, отражающие реальные сценарии
            - Используйте соответствующие стратегии мокирования без избыточного мокирования
            - Следуйте стилю и соглашениям существующих тестов проекта

            Пример правильного формата:
            ```java
            package com.example;
            
            import org.junit.jupiter.api.Test;
            import static org.assertj.core.api.Assertions.assertThat;
            
            class MyServiceTest {
                @Test
                void should_return_result_when_input_valid() {
                    // test code
                }
            }
            ```
            """;
    }

    /**
     * Build user prompt with method context
     */
    @NotNull
    private String buildUserPrompt(@NotNull MethodContext context) {
        StringBuilder prompt = new StringBuilder();
        
        // Method signature
        prompt.append("## Сигнатура метода\n");
        prompt.append("Класс: ").append(context.className()).append("\n");
        prompt.append("Метод: ").append(context.methodName()).append("\n");
        prompt.append("Возвращаемый тип: ").append(context.returnType()).append("\n");
        prompt.append("Параметры: ").append(formatParameters(context.parameters())).append("\n");
        prompt.append("Аннотации: ").append(String.join(", ", context.annotations())).append("\n\n");

        // Control Flow Graph
        prompt.append("## Граф потока управления (CFG)\n");
        prompt.append(formatCFG(context.controlFlow().nodes())).append("\n\n");

        // Dependencies
        prompt.append("## Зависимости\n");
        prompt.append(formatDependencies(context.dependencies())).append("\n\n");
        
        // Dependencies with source code (if available)
        if (context.dependenciesInfo() != null && !context.dependenciesInfo().isEmpty()) {
            prompt.append("## Детали зависимостей\n\n");
            for (DependencyInfo depInfo : context.dependenciesInfo()) {
                prompt.append("### ").append(depInfo.name()).append(" (").append(depInfo.type()).append(")\n\n");
                
                // Add interface contract
                if (depInfo.interfaceContract() != null && !depInfo.interfaceContract().isEmpty()) {
                    prompt.append("Контракт интерфейса:\n```java\n")
                        .append(depInfo.interfaceContract())
                        .append("\n```\n\n");
                }
                
                // Add method signatures
                if (depInfo.methods() != null && !depInfo.methods().isEmpty()) {
                    prompt.append("Методы:\n");
                    for (DependencyInfo.MethodInfo methodInfo : depInfo.methods()) {
                        prompt.append("- ").append(methodInfo.returnType()).append(" ")
                            .append(methodInfo.name()).append("(")
                            .append(String.join(", ", methodInfo.parameters()))
                            .append(")");
                        if (!methodInfo.exceptions().isEmpty()) {
                            prompt.append(" throws ").append(String.join(", ", methodInfo.exceptions()));
                        }
                        prompt.append("\n");
                        if (methodInfo.javadoc() != null && !methodInfo.javadoc().isEmpty()) {
                            prompt.append("  ").append(methodInfo.javadoc().split("\n")[0]).append("\n");
                        }
                    }
                    prompt.append("\n");
                }
                
                // Add source code if available
                if (depInfo.sourceCode() != null && !depInfo.sourceCode().isEmpty()) {
                    prompt.append("Исходный код:\n```java\n")
                        .append(depInfo.sourceCode())
                        .append("\n```\n\n");
                }
                
                // REQ 2: Spring implementations
                if (depInfo.springImplementations() != null && !depInfo.springImplementations().isEmpty()) {
                    prompt.append("Spring реализации:\n");
                    depInfo.springImplementations().forEach(impl -> {
                        prompt.append("  - ").append(impl.className())
                            .append(" [").append(String.join(", ", impl.annotations())).append("]\n");
                        if (!impl.methods().isEmpty()) {
                            prompt.append("    Методы:\n");
                            impl.methods().forEach(m -> {
                                prompt.append("      - ").append(m.returnType()).append(" ")
                                    .append(m.name()).append("(")
                                    .append(String.join(", ", m.parameters()))
                                    .append(")\n");
                            });
                        }
                        if (impl.hasSourceCode() && impl.sourceSnippet() != null && !impl.sourceSnippet().isEmpty()) {
                            prompt.append("    Фрагмент:\n```java\n")
                                .append(impl.sourceSnippet())
                                .append("\n```\n");
                        }
                    });
                    prompt.append("\n");
                }
                
                // Lombok generated methods
                if (depInfo.lombokGeneratedMethods() != null && !depInfo.lombokGeneratedMethods().isEmpty()) {
                    prompt.append("Lombok генерируемые методы:\n");
                    depInfo.lombokGeneratedMethods().forEach(lm -> {
                        prompt.append("  - ").append(lm.methodName())
                            .append(" (").append(lm.generatedBy()).append(")\n");
                    });
                    prompt.append("\n");
                }
                
                // MapStruct info
                if (depInfo.mapStructInfo() != null) {
                    prompt.append("MapStruct Mapper:\n");
                    prompt.append("  - Mapper: ").append(depInfo.mapStructInfo().mapperClassName()).append("\n");
                    prompt.append("  - Source: ").append(depInfo.mapStructInfo().sourceType()).append("\n");
                    prompt.append("  - Target: ").append(depInfo.mapStructInfo().targetType()).append("\n");
                    if (!depInfo.mapStructInfo().mappings().isEmpty()) {
                        prompt.append("  - Mapping:\n");
                        depInfo.mapStructInfo().mappings().forEach(m -> {
                            prompt.append("    - ").append(m.source()).append(" -> ").append(m.target());
                            if (m.qualifiedByName() != null && !m.qualifiedByName().isEmpty()) {
                                prompt.append(" (").append(m.qualifiedByName()).append(")");
                            }
                            prompt.append("\n");
                        });
                    }
                    prompt.append("\n");
                }
            }
        }
        
        // Documentation Contract
        if (context.docContract() != null) {
            prompt.append("## Документация и контракт\n");
            if (!context.docContract().params().isEmpty()) {
                prompt.append("Параметры:\n");
                context.docContract().params().forEach((name, desc) -> 
                    prompt.append("  - ").append(name).append(": ").append(desc).append("\n"));
            }
            if (context.docContract().returns() != null) {
                prompt.append("Возвращает: ").append(context.docContract().returns()).append("\n");
            }
            if (!context.docContract().throwsList().isEmpty()) {
                prompt.append("Бросает: ").append(String.join(", ", context.docContract().throwsList())).append("\n");
            }
            if (!context.docContract().businessRules().isEmpty()) {
                prompt.append("Бизнес-правила:\n");
                context.docContract().businessRules().forEach(rule -> 
                    prompt.append("  - ").append(rule).append("\n"));
            }
            prompt.append("\n");
        }
        
        // Complexity metrics
        prompt.append("## Метрики сложности\n");
        prompt.append("Цикломатическая сложность: ").append(context.complexity().cyclomatic()).append("\n");
        prompt.append("Максимальная глубина вложенности: ").append(context.complexity().nestingDepth()).append("\n");
        prompt.append("Количество веток: ").append(context.complexity().branchCount()).append("\n");
        prompt.append("Количество циклов: ").append(context.complexity().loopCount()).append("\n\n");
        
        // Existing tests
        if (!context.existingTests().isEmpty()) {
            prompt.append("## Примеры существующих тестов\n");
            prompt.append("Используйте их как образец стиля (НЕ копируйте, просто следуйте шаблону):\n");
            List<ExistingTestInfo> limitedTests = context.existingTests().stream()
                .limit(MAX_EXAMPLES)
                .collect(Collectors.toList());
            limitedTests.forEach(test -> {
                prompt.append("- ").append(test.name())
                    .append(" [Фреймворк: ").append(test.framework())
                    .append(", Ассершены: ").append(String.join(", ", test.assertions()))
                    .append("]\n");
            });
            prompt.append("\n");
        }
        
        // Called methods (REQ 1)
        if (context.calledMethods() != null && !context.calledMethods().isEmpty()) {
            prompt.append("## Вызываемые методы\n");
            prompt.append("Методы которые вызываются в теле анализируемого метода:\n\n");
            context.calledMethods().forEach(cm -> {
                prompt.append("### ").append(cm.className()).append(".").append(cm.methodName()).append("\n");
                prompt.append("- Возвращаемый тип: ").append(cm.returnType()).append("\n");
                if (!cm.parameters().isEmpty()) {
                    prompt.append("- Параметры: ").append(String.join(", ", cm.parameters())).append("\n");
                }
                prompt.append("- Статический: ").append(cm.isStatic() ? "да" : "нет").append("\n");
                prompt.append("- Есть исходный код: ").append(cm.hasSourceCode() ? "да" : "нет").append("\n");
                if (cm.sourceCodeSnippet() != null && !cm.sourceCodeSnippet().isEmpty()) {
                    prompt.append("- Фрагмент кода:\n```java\n")
                        .append(cm.sourceCodeSnippet()).append("\n```\n");
                }
                if (!cm.calledMethods().isEmpty()) {
                    prompt.append("- Вызывает: ").append(String.join(", ", cm.calledMethods())).append("\n");
                }
                prompt.append("\n");
            });
        }
        
        // DTO/POJO structures (REQ 5)
        if (context.dtoStructures() != null && !context.dtoStructures().isEmpty()) {
            prompt.append("## DTO/POJO структуры\n");
            prompt.append("Структуры данных используемые в параметрах и возвращаемом типе:\n\n");
            context.dtoStructures().forEach(dto -> {
                prompt.append("### ").append(dto.className()).append("\n");
                prompt.append("Пакет: ").append(dto.packageName()).append("\n");
                prompt.append("Аннотации: ").append(String.join(", ", dto.annotations())).append("\n");
                prompt.append("Поля:\n");
                dto.fields().forEach(field -> {
                    prompt.append("  - ").append(field.type()).append(" ").append(field.name());
                    if (!field.annotations().isEmpty()) {
                        prompt.append(" [").append(String.join(", ", field.annotations())).append("]");
                    }
                    prompt.append("\n");
                });
                prompt.append("\n");
            });
        }
        
        // Data transformations (REQ 3)
        if (context.dataTransformations() != null && !context.dataTransformations().isEmpty()) {
            prompt.append("## Трансформации данных\n");
            prompt.append("Как изменяются входные параметры в методе:\n\n");
            context.dataTransformations().forEach(dt -> {
                prompt.append("### Параметр: ").append(dt.parameterName()).append("\n");
                prompt.append("- Тип: ").append(dt.originalType()).append("\n");
                prompt.append("- Изменён: ").append(dt.isModified() ? "да" : "нет").append("\n");
                prompt.append("- Итоговое использование: ").append(dt.finalUsage()).append("\n");
                if (!dt.transformations().isEmpty()) {
                    prompt.append("- Шаги трансформации:\n");
                    dt.transformations().forEach(step -> {
                        prompt.append("  ").append(step.lineNumber()).append(": ")
                            .append(step.operation()).append(" - ").append(step.description()).append("\n");
                    });
                }
                if (!dt.intermediateVariables().isEmpty()) {
                    prompt.append("- Промежуточные переменные:\n");
                    dt.intermediateVariables().forEach((var, desc) -> {
                        prompt.append("  - ").append(var).append(": ").append(desc).append("\n");
                    });
                }
                prompt.append("\n");
            });
        }
        
        prompt.append("## Задача\n");
        prompt.append("Сгенерируйте ПРОИЗВОДСТВЕННО-ГОТОВЫЙ Java код unit-тестов для метода выше.\n\n");
        prompt.append("КРИТИЧЕСКИ ВАЖНО:\n");
        prompt.append("- Верните ТОЛЬКО Java код, БЕЗ JSON, БЕЗ markdown, БЕЗ текстовых описаний\n");
        prompt.append("- Начните с: package ...\n");
        prompt.append("- Включите все необходимые imports\n");
        prompt.append("- Создайте полный класс с тестовыми методами\n");
        prompt.append("- Каждый тест должен быть аннотирован @Test\n");
        prompt.append("- Используйте JUnit 5 и AssertJ для ассертов\n");
        prompt.append("- Закройте код последней скобкой }\n\n");
        prompt.append("НЕ возвращайте JSON! НЕ возвращайте описание тестов! ТОЛЬКО Java код!\n");
        
        // Add specific recommendations based on class type
        if (context.isRestController()) {
            prompt.append("\n## Специфика: Spring REST Controller\n");
            prompt.append("Это REST контроллер. Рекомендуется использовать:\n");
            prompt.append("- MockMvc для тестирования HTTP endpoints\n");
            prompt.append("- @WebMvcTest для slice-тестов\n");
            prompt.append("- MockHttpServletResponse для проверки ответов\n");
            prompt.append("- Тестирование status codes, headers, response body\n");
            prompt.append("- @MockBean для зависимостей (сервисы, репозитории)\n\n");
        }
        
        if (context.isSpringService()) {
            prompt.append("\n## Специфика: Spring Service\n");
            prompt.append("Это сервисный слой. Рекомендуется использовать:\n");
            prompt.append("- @ExtendWith(MockitoExtension.class)\n");
            prompt.append("- @Mock для репозиториев и внешних зависимостей\n");
            prompt.append("- @InjectMocks для тестируемого сервиса\n");
            prompt.append("- Тестирование бизнес-логики без HTTP\n\n");
        }
        
        if (context.isSpringRepository()) {
            prompt.append("\n## Специфика: Spring Repository\n");
            prompt.append("Это слой доступа к данным. Рекомендуется использовать:\n");
            prompt.append("- @DataJpaTest для slice-тестов\n");
            prompt.append("- @AutoConfigureTestDatabase для тестовой БД\n");
            prompt.append("- Тестирование CRUD операций\n");
            prompt.append("- Тестирование custom query methods\n\n");
        }
        
        return prompt.toString();
    }

    /**
     * Format method parameters
     */
    @NotNull
    private String formatParameters(@NotNull List<Parameter> parameters) {
        if (parameters.isEmpty()) {
            return "none";
        }
        return parameters.stream()
            .map(p -> p.name() + ": " + p.type() + (p.nullable() ? " (nullable)" : ""))
            .collect(Collectors.joining(", "));
    }

    /**
     * Format Control Flow Graph as readable text
     */
    @NotNull
    private String formatCFG(@NotNull List<CFGNode> nodes) {
        if (nodes.isEmpty()) {
            return "No complex control flow detected (straight-line code)";
        }
        
        StringBuilder sb = new StringBuilder();
        for (CFGNode node : nodes) {
            switch (node.type()) {
                case IF -> {
                    if (node.condition() != null && node.condition().startsWith("ternary:")) {
                        sb.append("ternary (").append(node.condition().replace("ternary: ", ""))
                          .append(") at line ").append(node.line()).append("\n");
                    } else {
                        sb.append("if (").append(node.condition()).append(") at line ");
                        sb.append(node.line());
                        if (node.thenLine() != null) sb.append(" [then: ").append(node.thenLine()).append("]");
                        if (node.elseLine() != null) sb.append(" [else: ").append(node.elseLine()).append("]");
                        sb.append("\n");
                    }
                }
                case CATCH -> {
                    sb.append("catch (").append(node.catchBlock().exceptionType())
                      .append(") at line ").append(node.line())
                      .append(" [handler: ").append(node.catchBlock().line()).append("]\n");
                }
                case SWITCH -> {
                    sb.append("switch ").append(node.condition())
                      .append(" at line ").append(node.line()).append("\n");
                }
                case LOOP -> {
                    sb.append(node.condition())
                      .append(" at line ").append(node.line()).append("\n");
                }
                case RETURN -> {
                    sb.append("return at line ").append(node.line()).append("\n");
                }
                case THROW -> {
                    sb.append("throw ").append(node.condition()).append(" at line ")
                      .append(node.line()).append("\n");
                }
            }
        }
        return sb.toString();
    }

    /**
     * Format dependencies
     */
    @NotNull
    private String formatDependencies(@NotNull List<Dependency> dependencies) {
        if (dependencies.isEmpty()) {
            return "No external dependencies";
        }
        
        return dependencies.stream()
            .map(d -> d.name() + ": " + d.type() + 
                     (d.isExternal() ? " [external]" : "") +
                     (d.nullable() ? " [nullable]" : ""))
            .collect(Collectors.joining("\n"));
    }

    /**
     * Extract few-shot examples from existing tests
     */
    @NotNull
    private List<String> extractExamples(@NotNull List<ExistingTestInfo> existingTests) {
        return existingTests.stream()
            .limit(MAX_EXAMPLES)
            .map(test -> String.format(
                "Test: %s | Framework: %s | Assertions: %s",
                test.name(),
                test.framework(),
                String.join(", ", test.assertions())
            ))
            .collect(Collectors.toList());
    }

    /**
     * Convert MethodContext to JSON for LLM
     */
    @NotNull
    public String contextToJson(@NotNull MethodContext context) {
        try {
            return objectMapper.writeValueAsString(context);
        } catch (JsonProcessingException e) {
            return "{ error: \"Failed to serialize context\" }";
        }
    }
}
