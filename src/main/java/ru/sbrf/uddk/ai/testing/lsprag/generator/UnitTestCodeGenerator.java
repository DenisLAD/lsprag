package ru.sbrf.uddk.ai.testing.lsprag.generator;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.sbrf.uddk.ai.testing.lsprag.LspragSettingsState;
import ru.sbrf.uddk.ai.testing.lsprag.context.MethodContext;
import ru.sbrf.uddk.ai.testing.lsprag.llm.LLMGateway;
import ru.sbrf.uddk.ai.testing.lsprag.model.GeneratedTestData;
import ru.sbrf.uddk.ai.testing.lsprag.model.TestCase;
import ru.sbrf.uddk.ai.testing.lsprag.repair.DiagnosticFixer;
import ru.sbrf.uddk.ai.testing.lsprag.utils.LLMResponseParser;

import java.io.IOException;
import java.util.List;

import static ru.sbrf.uddk.ai.testing.lsprag.generator.TestCodeGenerator.buildFixPrompt;

public class UnitTestCodeGenerator implements ITestCodeGenerator {

    private final LLMGateway llmGateway;
    private final LspragSettingsState settings;

    private static final String SYSTEM_CODE = """
            Ты — опытный Java разработчик, специализирующийся на написании UnitTestов для Spring Boot приложений.
                        
            ### Стек технологий:
            - Java 17
            - Spring Boot 2.x/3.x
            - JUnit 5 (Jupiter)
            - AssertJ для fluent assertions
                        
            ### Требования к коду:
            1. **Импорты:** Все необходимые импорты должны быть включены, включая статические:
               - `import static org.assertj.core.api.Assertions.assertThat;`
               - `import static org.hamcrest.Matchers.*;`
                        
            2. **Аннотации:**
               - Использовать `@SpringBootTest`
               - Добавить `@AutoConfigureMockMvc` для тестирования контроллеров если требуется
               - Использовать `@MockBean` для мокирования внешних зависимостей, а также подготовить ожидаемые моки методов
               - Добавить `@TestPropertySource` при необходимости переопределения свойств
                        
            3. **Методы тестов:**
               - Каждый тест должен быть изолирован и независим
               - Использовать понятные имена в формате `methodName_Scenario_ExpectedResult`
               - Добавлять `@DisplayName` с описанием на русском или английском
               - Использовать `@Order` для управления порядком выполнения при необходимости
                        
            4. **Валидация ответов:**
               - Для сложных проверок применять `assertThat()` из AssertJ
                        
            5. **Работа с данными:**
               - Генерировать тестовые данные через билдеры или фабричные методы
               - Очищать тестовые данные в `@AfterEach` или использовать `@Transactional`
               - Для UUID использовать `UUID.randomUUID().toString()`
                        
            ### Формат ответа:
            Верни только Java код тестового класса, без markdown-обертки (без ```java), без пояснений.
            Код должен быть готов к компиляции и запуску.
            """;

    private static final String SYSTEM_PROMPT = """
            Ты — опытный UnitTest инженер, специализирующийся на тестировании Spring Boot приложениях.
                        
            ### Твоя задача:
            Создать детальные тест-кейсы для конкретного кода.
                        
            ### Требования к тест-кейсам:
            1. Каждый тест-кейс должен содержать:
               - Уникальное название
               - Предусловия (необходимые данные и состояние системы)
               - Шаги с конкретными проверками
               - Ожидаемый результат (все проверки должны быть явно перечислены)
               - Постусловия (пример: очистка тестовых данных)
                        
            2. Для случая проверять:
               - Покрытие ветвления с разными данными
               - Структуру тела ответа и результат
               - Ключевые поля и их значения
                        
            4. Генерировать реалистичные тестовые данные:
               - Использовать осмысленные значения (не "test", "123")
               - Для дат использовать формат ISO 8601
               - Для ID использовать UUID или числовые значения
               - Учитывать бизнес-логику
                        
            ### Формат ответа:
            Предоставь тест-кейс в структурированном виде с использованием markdown.
            Не добавляй пояснений вне структуры тест-кейса.
                        
            """;

    public UnitTestCodeGenerator(@NotNull LLMGateway llmGateway,
                                 @NotNull LspragSettingsState settings) {
        this.llmGateway = llmGateway;
        this.settings = settings;
    }

    public GeneratedTestData generateTestData(Project project, @NotNull MethodContext context,
                                              @Nullable List<TestCase> testCases)
            throws LLMGateway.LLMException {
        String prompt = buildPrompt(context, testCases, settings);
        String rawResponse = null;
        try {
            rawResponse = llmGateway.generate(prompt);
        } catch (IOException e) {
            throw new LLMGateway.LLMException("Ошибка генерации", e);
        }

        return new GeneratedTestData(rawResponse, generateTestClass(project, context, rawResponse));
    }

    @NotNull
    public String generateTestClass(@NotNull Project project, @NotNull MethodContext context,
                                    @NotNull String generatedTestCases)
            throws LLMGateway.LLMException {

        // 1. Формируем промпт
        String prompt = buildCodePrompt(context, generatedTestCases, settings);

        // 2. Запрашиваем генерацию у LLM
        String fixedCode = null;
        try {
            String rawResponse = llmGateway.generate(prompt);
            DiagnosticFixer fixer = new DiagnosticFixer(project, llmGateway, settings.getMaxRepairAttempts());
            fixedCode = fixer.fixErrors(rawResponse);
        } catch (IOException e) {
            throw new LLMGateway.LLMException("Ошибка генерации", e);
        }

        return LLMResponseParser.extractJavaCode(fixedCode);
    }

    @Override
    public GeneratedTestData generateTestData(@NotNull Project project, @NotNull MethodContext context, @Nullable List<TestCase> testCases, @NotNull String prompt) {
        String rawResponse = null;
        try {
            rawResponse = llmGateway.generate(prompt);
        } catch (IOException e) {
            throw new LLMGateway.LLMException("Ошибка генерации", e);
        }

        return new GeneratedTestData(rawResponse, generateTestClass(project, context, rawResponse));
    }

    @NotNull
    public String buildCodePrompt(@NotNull MethodContext context,
                                  @NotNull String testCases,
                                  @NotNull LspragSettingsState settings) {
        StringBuilder prompt = new StringBuilder();

        // 1. Системная инструкция
        prompt.append(SYSTEM_CODE).append("\n\n");

        prompt.append(PromptContextBuilder.buildPrompt(context));

        // 5. Тест-кейсы для реализации
        prompt.append("## ТЕСТ-КЕЙСЫ ДЛЯ РЕАЛИЗАЦИИ\n");
        prompt.append(testCases);
        prompt.append("\n");

        // 6. Дополнительные требования по коду
        prompt.append("## ТЕХНИЧЕСКИЕ ТРЕБОВАНИЯ К КОДУ\n");

        if (settings.isUseLLMForTestPlanning()) {
            prompt.append("""
                    ### Расширенное тестирование
                    - Добавь тесты для граничных случаев на основе анализа метода
                    - Включи проверку обработки исключений
                    - Реализуй тесты с некорректными входными данными
                    """);
        }

        prompt.append("""
                ### Работа с ответами
                - Для `ResponseEntity`: проверяй статус, заголовки и тело
                - Для коллекций: проверяй размер, содержимое и порядок (если важен)
                        
                ### Генерация тестовых данных
                - Создавай тестовые данные через билдеры или фабричные методы
                - Учитывай аннотации валидации (@NotNull, @Size, @Pattern, @Email)
                - Для POST/PUT/PATCH всегда генерируй валидные DTO
                        
                ### Обработка параметров
                - @PathVariable: проверяй валидные и невалидные значения
                - @RequestParam: проверяй обязательные и опциональные параметры
                - @RequestBody: проверяй валидные и невалидные DTO
                        
                ### Очистка данных
                - Используй `@AfterEach` для удаления тестовых данных
                - Для интеграционных тестов предпочтительнее `@Transactional`
                        
                ### Структура кода
                - Группируй тесты по функциональности
                - Используй вспомогательные методы для создания тестовых данных
                - Добавляй JavaDoc к сложным проверкам
                """);

        // 7. Финальная инструкция
        prompt.append("## ФОРМАТ ОТВЕТА\n");
        prompt.append("Верни полный Java код тестового класса, готовый к компиляции и запуску.\n");
        prompt.append("Не добавляй пояснений, не оборачивай код в markdown-блоки.\n");

        return prompt.toString();
    }

    @NotNull
    public String fixCode(@NotNull String brokenCode,
                          @NotNull List<String> errors)
            throws IOException, LLMGateway.LLMException {

        String fixPrompt = buildFixPrompt(brokenCode, errors);
        String rawResponse = llmGateway.generate(fixPrompt);

        return LLMResponseParser.extractJavaCode(rawResponse);
    }

    @NotNull
    public String buildPrompt(@NotNull MethodContext context,
                              @Nullable List<TestCase> testCases,
                              @NotNull LspragSettingsState settings) {

        StringBuilder prompt = new StringBuilder();

        // 1. Системная инструкция
        prompt.append(SYSTEM_PROMPT).append("\n\n");

        prompt.append(PromptContextBuilder.buildPrompt(context));

        // 6. Дополнительные инструкции на основе настроек
        prompt.append("## ДОПОЛНИТЕЛЬНЫЕ ТРЕБОВАНИЯ\n");

        if (settings.isUseLLMForTestPlanning()) {
            prompt.append("### Анализ граничных случаев\n");
            prompt.append("- Проанализируй бизнес-логику метода\n");
            prompt.append("- Добавь тест-кейсы для граничных значений\n");
            prompt.append("- Включи проверку обработки исключительных ситуаций\n");
            prompt.append("- Рассмотри сценарии с некорректными входными данными\n");
        }

        prompt.append("""
                ### Генерация тестовых данных
                - Для полей с аннотациями валидации (@NotNull, @Size, @Pattern) учитывай ограничения
                                
                ### Очистка данных
                - Всегда предусматривай постусловия для удаления созданных данных
                - Используй @AfterEach для гарантированной очистки
                """);

        // 7. Финальная инструкция
        prompt.append("## ТРЕБОВАНИЯ К ФОРМАТУ ОТВЕТА\n");
        prompt.append("Предоставь тест-кейсы в формате, описанном в системном промпте. ");
        prompt.append("Не добавляй пояснений вне структуры тест-кейсов.\n");

        return prompt.toString();
    }
}