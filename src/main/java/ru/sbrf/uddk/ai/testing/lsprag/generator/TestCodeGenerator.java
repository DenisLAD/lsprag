package ru.sbrf.uddk.ai.testing.lsprag.generator;

import com.intellij.openapi.project.Project;
import org.apache.commons.collections.CollectionUtils;
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
import java.util.stream.Collectors;

public class TestCodeGenerator implements ITestCodeGenerator {

    private final LLMGateway llmGateway;
    private final LspragSettingsState settings;

    private static final String SYSTEM_PROMPT = """
            Ты — опытный QA инженер, специализирующийся на тестировании REST API в Spring Boot приложениях.
                        
            ### Твоя задача:
            Создать детальные тест-кейсы для интеграционного тестирования REST эндпоинтов.
                        
            ### Требования к тест-кейсам:
            1. Каждый тест-кейс должен содержать:
               - Уникальное название (формат: [HTTP_METHOD] Название_сущности - Сценарий)
               - Предусловия (необходимые данные и состояние системы)
               - Шаги с конкретными HTTP запросами и ожидаемыми ответами
               - Ожидаемый результат (все проверки должны быть явно перечислены)
               - Постусловия (очистка тестовых данных)
                        
            2. Для каждого запроса обязательно указывать:
               - HTTP метод и URL
               - Тело запроса в JSON формате (если применимо)
               - Заголовки (Content-Type, Authorization и т.д.)
                        
            3. Для каждого ответа проверять:
               - HTTP статус код
               - Структуру тела ответа в JSON
               - Ключевые поля и их значения
                        
            4. Генерировать реалистичные тестовые данные:
               - Использовать осмысленные значения (не "test", "123")
               - Для дат использовать формат ISO 8601
               - Для ID использовать UUID или числовые значения
               - Учитывать бизнес-логику (например, email должен быть валидным)
                        
            ### Формат ответа:
            Предоставь тест-кейс в структурированном виде с использованием markdown.
            Не добавляй пояснений вне структуры тест-кейса.
                        
            ### Пример качественного тест-кейса:
                        
            **Название:** [POST] Создание пользователя - Успешное создание с валидными данными
                        
            **Предусловия:**
            - База данных очищена от тестовых данных
            - Сервис аутентификации доступен
                        
            **Шаги:**
            1. Отправить POST запрос на `/api/users`
               ```json
               {
                 "email": "john.doe@example.com",
                 "firstName": "John",
                 "lastName": "Doe",
                 "age": 30,
                 "role": "USER"
               }
               ```
                        
            2. Проверить ответ:
               - HTTP статус: 201 Created
               - Заголовок Location: `/api/users/{id}`
               - Тело ответа:
               ```json
               {
                 "id": "[не пустое значение]",
                 "email": "john.doe@example.com",
                 "firstName": "John",
                 "lastName": "Doe",
                 "age": 30,
                 "status": "ACTIVE",
                 "createdAt": "[валидная дата]"
               }
               ```
                        
            3. Выполнить GET запрос на `/api/users/{id}` из заголовка Location
                        
            4. Проверить, что данные соответствуют созданным
                        
            **Ожидаемый результат:**
            - Пользователь успешно создан
            - Все поля сохранены корректно
            - Пользователь имеет статус ACTIVE
                        
            **Постусловия:**
            - Удалить созданного пользователя: DELETE `/api/users/{id}`
            - Очистить кэш аутентификации
                        
            """;

    private static final String SYSTEM_CODE = """
            Ты — опытный Java разработчик, специализирующийся на написании интеграционных тестов для Spring Boot приложений.
                        
            ### Стек технологий:
            - Java 17
            - Spring Boot 2.x/3.x
            - JUnit 5 (Jupiter)
            - RestAssured 5.x
            - AssertJ для fluent assertions
                        
            ### Структура тестового класса:
            ```java
            @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
            @AutoConfigureMockMvc
            @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
            class ResourceNameTest {
                        
                @LocalServerPort
                private int port;
                        
                @Autowired
                private TestRestTemplate restTemplate;
                        
                @MockBean
                private ExternalService externalService;
                
                @BeforeEach
                void setUp() {
                    // подготовка тестовых данных
                }
                
                @Test
                @Order(1)
                @DisplayName("POST /api/resource - Should create resource successfully")
                void createResource_ShouldReturnCreated() {
                    // given
                    CreateRequest request = CreateRequest.builder()
                        .field1("value1")
                        .field2(123)
                        .build();
                    
                    // when
                    Response response = given()
                        .contentType(ContentType.JSON)
                        .body(request)
                        .when()
                        .post("/api/resource");
                    
                    // then
                    response.then()
                        .statusCode(HttpStatus.CREATED.value())
                        .body("id", notNullValue())
                        .body("field1", equalTo("value1"));
                }
            }
            ```
                        
            ### Требования к коду:
            1. **Импорты:** Все необходимые импорты должны быть включены, включая статические:
               - `import static io.restassured.RestAssured.given;`
               - `import static org.assertj.core.api.Assertions.assertThat;`
               - `import static org.hamcrest.Matchers.*;`
               - `import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;`
                        
            2. **Аннотации:**
               - Использовать `@SpringBootTest` с `webEnvironment = RANDOM_PORT`
               - Добавить `@AutoConfigureMockMvc` для тестирования контроллеров
               - Использовать `@MockBean` для мокирования внешних зависимостей
               - Добавить `@TestPropertySource` при необходимости переопределения свойств
                        
            3. **Методы тестов:**
               - Каждый тест должен быть изолирован и независим
               - Использовать понятные имена в формате `methodName_Scenario_ExpectedResult`
               - Добавлять `@DisplayName` с описанием на русском или английском
               - Использовать `@Order` для управления порядком выполнения при необходимости
                        
            4. **Валидация ответов:**
               - Проверять статус код с использованием `HttpStatus` enum
               - Использовать JSONPath для проверки полей
               - Для сложных проверок применять `assertThat()` из AssertJ
                        
            5. **Работа с данными:**
               - Генерировать тестовые данные через билдеры или фабричные методы
               - Очищать тестовые данные в `@AfterEach` или использовать `@Transactional`
               - Для UUID использовать `UUID.randomUUID().toString()`
                        
            ### Формат ответа:
            Верни только Java код тестового класса, без markdown-обертки (без ```java), без пояснений.
            Код должен быть готов к компиляции и запуску.
            """;

    @NotNull
    public String buildPrompt(@NotNull MethodContext context,
                              @Nullable List<TestCase> testCases,
                              @NotNull LspragSettingsState settings) {

        StringBuilder prompt = new StringBuilder();

        // 1. Системная инструкция
        prompt.append(SYSTEM_PROMPT).append("\n\n");

        prompt.append(PromptContextBuilder.buildPrompt(context));

        if (CollectionUtils.isNotEmpty(testCases)) {
            // 5. Список тест-кейсов для генерации
            prompt.append("## ТЕСТ-КЕЙСЫ\n");
            prompt.append("Создай тест-кейсы для следующих сценариев:\n\n");
            for (TestCase tc : testCases) {
                prompt.append(formatTestCase(tc)).append("\n");
            }
            prompt.append("\n");
        }

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
                ### Проверки ответов
                - Для методов, возвращающих `ResponseEntity`, проверяй:
                  * HTTP статус
                  * Заголовки (Location, Content-Type)
                  * Тело ответа с проверкой всех значимых полей
                        
                ### Генерация тестовых данных
                - POST/PUT/PATCH методы: создавай реалистичные DTO с валидными значениями
                - Для полей с аннотациями валидации (@NotNull, @Size, @Pattern) учитывай ограничения
                - Для @PathVariable и @RequestParam создавай тесты с:
                  * Валидными значениями
                  * Невалидными значениями (отсутствующие, некорректные)
                  * Граничными значениями
                        
                ### Очистка данных
                - Всегда предусматривай постусловия для удаления созданных данных
                - Используй @AfterEach для гарантированной очистки
                """);

        // 7. Финальная инструкция
        prompt.append("## ТРЕБОВАНИЯ К ФОРМАТУ ОТВЕТА\n");
        prompt.append("Предоставь тест-кейсы в формате, описанном в системном промпте. ");
        prompt.append("Каждый тест-кейс должен быть готов к регистрации в Zephyr.\n");
        prompt.append("Не добавляй пояснений вне структуры тест-кейсов.\n");

        return prompt.toString();
    }

    @NotNull
    private String formatTestCase(TestCase tc) {
        return String.format("""
                        **ID:** %s
                        **Описание:** %s
                        **Метод:** %s %s
                        **Ожидаемый статус:** %s
                        **Входные данные:** %s
                        """,
                tc.getId(),
                tc.getDescription(),
                tc.getHttpMethod(),
                tc.getEndpoint(),
                tc.getExpectedStatus(),
                tc.getInput().isEmpty() ? "стандартные (см. контекст)" : "```json\n" + tc.getInput().toString() + "\n```"
        );
    }

    @NotNull
    public static String buildFixPrompt(@NotNull String brokenCode, @NotNull List<String> errors) {
        return String.format("""
                        ## ЗАДАЧА: ИСПРАВЛЕНИЕ ОШИБОК В ТЕСТОВОМ КОДЕ
                                        
                        ### ИНСТРУКЦИЯ
                        Ты — эксперт по Java и Spring Boot тестированию.
                        Исправь ошибки в предоставленном коде теста.
                                        
                        ### ОБНАРУЖЕННЫЕ ОШИБКИ
                        %s
                                        
                        ### КОД С ОШИБКАМИ
                        ```java
                        %s
                        ```
                                        
                        ### ПРАВИЛА ИСПРАВЛЕНИЯ
                        1. Исправь ТОЛЬКО указанные ошибки
                        2. Не меняй логику тестов и названия методов
                        3. Сохрани все корректные импорты и структуру класса
                        4. При исправлении импортов добавляй правильные, удаляй неправильные
                        5. Если ошибка в аннотации — проверь правильность импорта и атрибутов
                        6. Если метод не найден — используй альтернативу из RestAssured/JUnit/AssertJ
                        7. Проверь, что все переменные и методы доступны в контексте теста
                                        
                        ### ФОРМАТ ОТВЕТА
                        Верни полный исправленный Java код.
                        Не добавляй пояснений, не оборачивай в markdown блоки.
                        """,
                errors.stream().map(e -> "- " + e).collect(Collectors.joining("\n")),
                brokenCode
        );
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

    public TestCodeGenerator(@NotNull LLMGateway llmGateway,
                             @NotNull LspragSettingsState settings) {
        this.llmGateway = llmGateway;
        this.settings = settings;
    }

    @Override
    public GeneratedTestData generateTestData(@NotNull Project project,
                                              @NotNull MethodContext context,
                                              @Nullable List<TestCase> testCases,
                                              @NotNull String prompt) {
        String rawResponse = null;
        try {
            rawResponse = llmGateway.generate(prompt);
        } catch (IOException e) {
            throw new LLMGateway.LLMException("Ошибка генерации", e);
        }

        return new GeneratedTestData(rawResponse, generateTestClass(project, context, rawResponse));
    }

    @Override
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

    @Override
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
    @NotNull
    public String fixCode(@NotNull String brokenCode,
                          @NotNull List<String> errors)
            throws IOException, LLMGateway.LLMException {

        String fixPrompt = buildFixPrompt(brokenCode, errors);
        String rawResponse = llmGateway.generate(fixPrompt);

        return LLMResponseParser.extractJavaCode(rawResponse);
    }
}