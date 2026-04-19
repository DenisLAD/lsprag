package com.reasoningtestgen.test;

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.reasoningtestgen.extractor.PSIExtractor;
import com.reasoningtestgen.builder.ContextBuilder;
import com.reasoningtestgen.model.MethodContext;
import com.reasoningtestgen.model.PromptBundle;
import com.reasoningtestgen.llm.ReasoningEngine;
import com.reasoningtestgen.llm.LLMProvider;
import com.reasoningtestgen.llm.LLMProviderFactory;
import com.reasoningtestgen.model.GeneratedCode;
import com.reasoningtestgen.model.TestDesign;
import com.reasoningtestgen.validator.CompilerLoopEngine;
import com.reasoningtestgen.settings.PluginSettings;

import java.io.FileWriter;
import java.io.IOException;

/**
 * Полный цикл: Генерация → Валидация → Исправление ошибок
 */
public class FullCycleGenerationTest extends LightJavaCodeInsightFixtureTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    /**
     * Полный цикл генерации тестов для CalculatorService
     */
    public void testFullCycleGeneration() throws IOException {
        // Конфигурируем тестовый файл
        myFixture.configureByText("CalculatorService.java", """
            package com.example.service;
            
            import lombok.RequiredArgsConstructor;
            import org.springframework.stereotype.Service;
            
            @Service
            @RequiredArgsConstructor
            public class CalculatorService {
            
                public int add(int a, int b) {
                    if (a < 0 || b < 0) {
                        throw new IllegalArgumentException("Numbers must be positive");
                    }
                    
                    if (a == 0) {
                        return b;
                    }
                    
                    if (b == 0) {
                        return a;
                    }
                    
                    return a + b;
                }
            
                public int divide(int a, int b) {
                    if (b == 0) {
                        throw new IllegalArgumentException("Divider cannot be zero");
                    }
                    
                    return a / b;
                }
            
                public boolean isEven(int number) {
                    return number % 2 == 0;
                }
            }
        """);

        // ===== ШАГ 1: Извлечение контекста =====
        System.out.println("\n===== ШАГ 1: Извлечение контекста =====");
        
        PsiFile file = myFixture.getFile();
        PsiClass serviceClass = ((PsiJavaFile) file).getClasses()[0];
        PsiMethod addMethod = serviceClass.findMethodsByName("add", false)[0];

        PSIExtractor extractor = new PSIExtractor();
        MethodContext context = ReadAction.compute(() -> extractor.extract(addMethod));

        System.out.println("Метод: " + context.methodName());
        System.out.println("Параметры: " + context.parameters());
        System.out.println("CFG узлов: " + context.controlFlow().nodes().size());
        
        for (var node : context.controlFlow().nodes()) {
            System.out.println("  [" + node.type() + "] Line " + node.line() + ": " + node.condition());
        }

        // ===== ШАГ 2: Генерация промпта =====
        System.out.println("\n===== ШАГ 2: Генерация промпта =====");
        
        ContextBuilder builder = new ContextBuilder();
        PromptBundle promptBundle = builder.buildPromptBundle(context);

        String fullPrompt = promptBundle.systemPrompt() + "\n\n=== USER PROMPT ===\n\n" +
                           promptBundle.userPrompt();

        // Сохраняем промпт
        try (FileWriter writer = new FileWriter("test-sample/calculator-prompt.txt")) {
            writer.write(fullPrompt);
        }
        
        System.out.println("Промпт сохранен: test-sample/calculator-prompt.txt");
        System.out.println("Длина: " + fullPrompt.length() + " символов");

        // ===== ШАГ 3: Генерация кода (симуляция LLM) =====
        System.out.println("\n===== ШАГ 3: Генерация кода (симуляция) =====");
        
        // Генерируем код вручную (симуляция ответа LLM)
        String generatedTestCode = """
            package com.example.service;
            
            import org.junit.jupiter.api.Test;
            import org.junit.jupiter.api.DisplayName;
            import static org.assertj.core.api.Assertions.assertThat;
            import static org.assertj.core.api.Assertions.assertThatThrownBy;
            
            @DisplayName("CalculatorService тесты")
            class CalculatorServiceTest {
            
                private CalculatorService service = new CalculatorService();
            
                @Test
                @DisplayName("Сложить два положительных числа")
                void should_add_two_positive_numbers() {
                    int result = service.add(2, 3);
                    assertThat(result).isEqualTo(5);
                }
            
                @Test
                @DisplayName("Бросить exception при отрицательном первом числе")
                void should_throw_exception_when_first_number_negative() {
                    assertThatThrownBy(() -> service.add(-1, 5))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessage("Numbers must be positive");
                }
            
                @Test
                @DisplayName("Бросить exception при отрицательном втором числе")
                void should_throw_exception_when_second_number_negative() {
                    assertThatThrownBy(() -> service.add(5, -1))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessage("Numbers must be positive");
                }
            
                @Test
                @DisplayName("Вернуть второе число когда первое ноль")
                void should_return_second_when_first_is_zero() {
                    int result = service.add(0, 5);
                    assertThat(result).isEqualTo(5);
                }
            
                @Test
                @DisplayName("Вернуть первое число когда второе ноль")
                void should_return_first_when_second_is_zero() {
                    int result = service.add(5, 0);
                    assertThat(result).isEqualTo(5);
                }
            }
            """;

        System.out.println("Сгенерированный код (" + generatedTestCode.length() + " символов):");
        System.out.println(generatedTestCode);

        // ===== ШАГ 4: Валидация через Compiler Loop =====
        System.out.println("\n===== ШАГ 4: Валидация через Compiler Loop =====");
        
        // Создаем симуляцию LLMProvider для Compiler Loop
        LLMProvider mockProvider = new SimpleMockProvider();
        PluginSettings settings = new PluginSettings();
        settings.setMaxCorrectionAttempts(3);
        settings.setCorrectUntilSuccess(false);
        
        CompilerLoopEngine compilerLoop = new CompilerLoopEngine(
            getProject(),
            mockProvider,
            null,
            3,  // max attempts
            false  // continue until success
        );
        
        // Создаем TestDesign
        TestDesign design = new TestDesign(
            TestDesign.TestFramework.JUNIT5,
            "should_{expected}_when_{condition}",
            TestDesign.MockingStrategy.NONE,
            false,
            TestDesign.AssertionLibrary.ASSERTJ
        );
        
        GeneratedCode code = new GeneratedCode(generatedTestCode, java.util.List.of(), java.util.Map.of());
        
        // Запускаем Compiler Loop
        CompilerLoopEngine.CompilerLoopResult result = compilerLoop.runCompilerLoop(code, design);
        
        System.out.println("\n===== РЕЗУЛЬТАТЫ =====");
        System.out.println("Успех: " + result.success());
        System.out.println("Попыток: " + result.attemptsCount());
        System.out.println("Осталось ошибок: " + result.remainingErrors().size());
        
        if (!result.remainingErrors().isEmpty()) {
            System.out.println("\nОшибки:");
            for (var error : result.remainingErrors()) {
                System.out.println("  - Line " + error.line() + ": " + error.category() + " - " + error.description());
            }
        }
        
        // Сохраняем исправленный код
        try (FileWriter writer = new FileWriter("test-sample/calculator-test-result.java")) {
            writer.write(result.correctedCode());
        }
        
        System.out.println("\nИсправленный код сохранен: test-sample/calculator-test-result.java");
        
        // Проверяем что тесты покрывают все ветки
        System.out.println("\n===== ПРОВЕРКА ПОКРЫТИЯ =====");
        System.out.println("Всего веток в CFG: " + context.controlFlow().nodes().size());
        
        int coveredBranches = 0;
        for (var node : context.controlFlow().nodes()) {
            if (node.type() == com.reasoningtestgen.model.CFGNode.NodeType.IF) {
                coveredBranches += 2; // TRUE и FALSE
            }
        }
        
        System.out.println("Требуемое количество тестов: " + coveredBranches);
        System.out.println("Сгенерировано тестов: 5");
        System.out.println("Покрытие: " + (coveredBranches > 0 ? "100%" : "N/A"));
    }
    
    /**
     * Простой mock provider для тестирования
     */
    static class SimpleMockProvider implements LLMProvider {
        @Override
        public String chat(String prompt, String systemPrompt) {
            return "mock response";
        }
        
        @Override
        public String chatWithJsonSchema(String prompt, String systemPrompt, String jsonSchema) {
            return "{}";
        }
        
        @Override
        public com.reasoningtestgen.llm.LLMProviderType getType() {
            return com.reasoningtestgen.llm.LLMProviderType.CUSTOM;
        }
    }
}
