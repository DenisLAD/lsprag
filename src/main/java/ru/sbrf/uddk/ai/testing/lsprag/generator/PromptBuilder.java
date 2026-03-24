package ru.sbrf.uddk.ai.testing.lsprag.generator;

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;
import ru.sbrf.uddk.ai.testing.lsprag.LspragSettingsState;
import ru.sbrf.uddk.ai.testing.lsprag.context.MethodContext;
import ru.sbrf.uddk.ai.testing.lsprag.model.TestCase;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class PromptBuilder {

    private static final String SYSTEM_PROMPT = """
            Ты — опытный QA/AQA, специализирующийся на тестировании Spring Boot приложений.
            Твоя задача: генерировать качественные интеграционные тесты кейсы для REST API.
            
            ### Требования:
            - Для каждого тест-кейса создать отдельное описание с понятным именем
            - Проверять статус-код и при необходимости тело ответа
            - Тест должен быть готов к проверке без дополнительных правок
            - Если используются DTO то необходимо сгенерировать тестовые данные для теста в формате JSON (**ЭТО ОЧЕНЬ ВАЖНО**)
            
            Формат ответа: ТОЛЬКО тест-кейс, с пред условиями и пост условиями, и его описание.
            **Обязательно:** включай структуру запроса и ответа в JSON.
            
            ### Пример тест-кейса:
            
            Название: Создание документа через API.
            Предусловия: Создан пользователь (POST /users, id=123).
            
            Шаги:
            1. POST /createDocument c телом
            ```json
            {...}
            ```
            2. Проверить: code 201, id != null. И проверить тело ответа
            ```json
            {...}
            ```
            3. GET /getDocument/{id}
            4. Проверить: status = "ACTIVE". И проверить тело ответа
            ```json
            {...}
            ```
            
            Ожидаемый результат: Все проверки пройдены.
            
            Постусловия: DELETE /deleteDocument/{id}.
            
            """;

    private static final String SYSTEM_CODE = """
            Ты — опытный Java-разработчик, специализирующийся на тестировании Spring Boot приложений.
            Твоя задача: генерировать качественные интеграционные тесты для REST API.
            
            Требования к коду:
            - Использовать Java 17, JUnit 5, RestAssured
            - Класс должен быть аннотирован @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
            - Использовать @AutoConfigureMockMvc если требуется
            - Для каждого тест-кейса создать отдельный @Test метод с понятным именем
            - Использовать стиль given()-when()-then() из RestAssured
            - Проверять статус-код и при необходимости тело ответа
            - Импортировать все необходимые классы, включая статические методы:
              * import static io.restassured.RestAssured.given;
              * import static org.hamcrest.Matchers.*;
              * import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
            - Использовать @Autowired MockMvc или TestRestTemplate по необходимости
            - Для моков сервисов использовать @MockBean из spring-boot-test
            - Код должен быть готов к запуску без дополнительных правок
            
            Формат ответа: ТОЛЬКО код теста, без пояснений, без markdown-блоков.
            """;

    @NotNull
    public String buildPrompt(@NotNull MethodContext context,
                              @NotNull List<TestCase> testCases,
                              @NotNull LspragSettingsState settings) {

        StringBuilder prompt = new StringBuilder();

        // 1. Системная инструкция
        prompt.append(SYSTEM_PROMPT).append("\n\n");

        // 2. Информация о целевом методе
        prompt.append("### ЦЕЛЕВОЙ МЕТОД\n");
        prompt.append(formatMethodInfo(context.getTargetMethod())).append("\n\n");

        // 3. Контекст вызываемых методов
        if (!context.getCalledMethods().isEmpty()) {
            prompt.append("### ВЫЗЫВАЕМЫЕ МЕТОДЫ (контекст)\n");
            for (MethodContext.MethodInfo info : context.getCalledMethods().values()) {
                prompt.append(formatCalledMethod(info)).append("\n");
            }
            prompt.append("\n");
        }

        // 4. DTO и модели данных (если есть в контексте)
        prompt.append("### МОДЕЛИ ДАННЫХ\n");
        prompt.append(formatDTOs(context)).append("\n\n");


        // 5. Список тест-кейсов
        prompt.append("### ТЕСТ-КЕЙСЫ ДЛЯ ГЕНЕРАЦИИ\n");
        for (TestCase tc : testCases) {
            prompt.append(formatTestCase(tc)).append("\n");
        }
        prompt.append("\n");

        // 6. Дополнительные инструкции
        prompt.append("### ДОПОЛНИТЕЛЬНЫЕ ТРЕБОВАНИЯ\n");
        if (settings.isUseLLMForTestPlanning()) {
            prompt.append("- Проанализируй логику метода и предложи дополнительные пограничные кейсы\n");
        }
        prompt.append("- Если метод возвращает ResponseEntity, проверяй не только статус, но и тело\n");
        prompt.append("- Для POST/PUT методов генерируй валидные тестовые данные\n");
        prompt.append("- Для методов с @PathVariable и @RequestParam генерируй тесты с разными значениями\n");

        // 7. Финальная инструкция
        prompt.append("### ОТВЕТ\n");
        prompt.append("Сгенерируй полный тест-кейс пригодный для регистрации в Zephyr.");

        return prompt.toString();
    }

    @NotNull
    private String formatDTOs(@NotNull MethodContext context) {
        StringBuilder sb = new StringBuilder();

        if (context.getRequestDTOs().isEmpty() && context.getResponseDTOs().isEmpty()) {
            sb.append("// Нет DTO для этого эндпоинта (используются примитивы или JDK типы)\n");
            return sb.toString();
        }

        // Request DTOs
        Map<String, MethodContext.DTOInfo> dtos = new HashMap<>();



        if (!context.getRequestDTOs().isEmpty()) {
            for (MethodContext.DTOInfo dto : context.getRequestDTOs()) {
                sb.append(formatSingleDTO(dto, "REQUEST")).append("\n");
            }
        }

        // Response DTOs
        if (!context.getResponseDTOs().isEmpty()) {
            for (MethodContext.DTOInfo dto : context.getResponseDTOs()) {
                sb.append(formatSingleDTO(dto, "RESPONSE")).append("\n");
            }
        }

        return sb.toString();
    }

    /**
     * Форматирует один DTO
     */
    @NotNull
    private String formatSingleDTO(@NotNull MethodContext.DTOInfo dto, @NotNull String usage) {
        StringBuilder sb = new StringBuilder();

        String type = dto.getClassName().toLowerCase();

        if (type.contains("integer") || type.contains("long")) return "```json\n123\n```";
        if (type.contains("boolean")) return "```json\ntrue\n```";
        if (type.contains("double") || type.contains("float")) return "```json\n123.45\n```";
        if (type.contains("list") || type.contains("array")) return "```json\n[]\n```";
        if (type.contains("map")) return "```json\n{}\n```";
        if (type.contains("optional")) return "```json\nnull";
        if (type.contains("uuid")) return "```json\n\"" + UUID.randomUUID() + "\"\n```";
        if (type.contains("localdate")) return "```json\n\"01-01-2001\"\n```";
        if (type.contains("localdatetime")) return "```json\n\"01-01-2001 00:00:00\"\n```";

        sb.append("##### ").append(dto.getClassName()).append("\n");
        sb.append("Category: ").append(dto.getCategory()).append("\n");
        sb.append("Fields:\n");

        for (MethodContext.DTOInfo.FieldInfo field : dto.getFields()) {
            sb.append("  - ").append(field.getName())
                    .append(": ").append(field.getTypeName());

            if (!field.getAnnotations().isEmpty()) {
                sb.append(" [").append(String.join(", ", field.getAnnotations())).append("]");
            }

            if (field.isNullable()) {
                sb.append(" (nullable)");
            }
            sb.append("\n");
        }

        sb.append("JSON Example:\n```json\n").append(dto.getJsonExample()).append("\n```\n\n");

        return sb.toString();
    }


    @NotNull
    private String formatMethodInfo(PsiMethod method) {
        StringBuilder sb = new StringBuilder();
        sb.append("```java\n");


        ReadAction.run(() -> {
            // Аннотации

            for (var ann : method.getAnnotations()) {
                sb.append(ann.getText()).append("\n");
            }

            // Сигнатура

            sb.append(method.getReturnType().getPresentableText())
                    .append(" ")
                    .append(method.getName())
                    .append("(");
        });

        ReadAction.run(() -> {
            var params = method.getParameterList().getParameters();
            for (int i = 0; i < params.length; i++) {
                if (i > 0) sb.append(", ");
                sb.append(params[i].getType().getPresentableText())
                        .append(" ")
                        .append(params[i].getName());
            }
        });
        sb.append(")");

        // Исключения
        ReadAction.run(() -> {
            var throwsList = method.getThrowsList().getReferencedTypes();
            if (throwsList.length > 0) {
                sb.append(" throws ");
                for (int i = 0; i < throwsList.length; i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(throwsList[i].getPresentableText());
                }
            }

            sb.append(" {\n");

            // Тело (сокращённо)
            if (method.getBody() != null) {
                String body = method.getBody().getText();
                if (body.length() > 1300) {
                    body = body.substring(0, 1300) + "\n    // ... [код сокращён]\n";
                }
                sb.append(body).append("\n");
            } else {
                sb.append("    // abstract or native method\n");
            }
            sb.append("}\n```\n");
        });

        return sb.toString();
    }

    @NotNull
    private String formatCalledMethod(MethodContext.MethodInfo info) {
        if ("interface_implementations".equals(info.getSignature())) {
            return String.format("""
                            Метод: %s
                            Возвращает: %s
                            Параметры: %s
                            Исключения: %s
                            %s
                            """,
                    info.getSignature(),
                    info.getReturnType(),
                    String.join(", ", info.getParameters()),
                    info.getThrownExceptions().isEmpty() ? "нет" : String.join(", ", info.getThrownExceptions()),
                    info.getBodySnippet() != null ? info.getBodySnippet() : "// тело метода недоступно"
            );
        } else {
            return String.format("""
                            Метод: %s
                            Возвращает: %s
                            Параметры: %s
                            Исключения: %s
                            ```java
                            %s
                            ```
                            """,
                    info.getSignature(),
                    info.getReturnType(),
                    String.join(", ", info.getParameters()),
                    info.getThrownExceptions().isEmpty() ? "нет" : String.join(", ", info.getThrownExceptions()),
                    info.getBodySnippet() != null ? info.getBodySnippet() : "// тело метода недоступно"
            );
        }
    }

    @NotNull
    private String formatTestCase(TestCase tc) {
        return String.format("""
                        [%s] %s
                        - Метод: %s %s
                        - Ожидаемый статус: %s
                        - Входные данные: %s
                        """,
                tc.getId(),
                tc.getDescription(),
                tc.getHttpMethod(),
                tc.getEndpoint(),
                tc.getExpectedStatus(),
                tc.getInput().isEmpty() ? "стандартные" : tc.getInput().toString()
        );
    }

    @NotNull
    public String buildFixPrompt(@NotNull String brokenCode, @NotNull List<String> errors) {
        return String.format("""
                        ### ЗАДАЧА: ИСПРАВЛЕНИЕ ОШИБОК В КОДЕ ###
                                    
                        Ты — эксперт по Java и фреймворку Spring. 
                        Исправь следующие ошибки в сгенерированном коде теста.
                                    
                        ### ОШИБКИ ###
                        %s
                                    
                        ### ОШИБОЧНЫЙ КОД ###
                        ```java
                        %s
                        ```
                                    
                        ### ТРЕБОВАНИЯ К ИСПРАВЛЕНИЮ ###
                        - Исправь ТОЛЬКО указанные ошибки, не меняй логику тестов
                        - Сохрани все импорты и структуру класса
                        - Если ошибка в импорте — добавь правильный import
                        - Если ошибка в аннотации — проверь, что класс импортирован
                        - Если метод не найден — предложи альтернативу из RestAssured/JUnit
                        - Верни ПОЛНЫЙ исправленный код, а не только изменённые фрагменты
                                    
                        ### ОТВЕТ ###
                        Верни только исправленный Java-код, без пояснений и markdown.
                        """,
                errors.stream().map(e -> "- " + e).collect(Collectors.joining("\n")),
                brokenCode
        );
    }

    public String buildCodePrompt(MethodContext context, String testCases, LspragSettingsState settings) {
        StringBuilder prompt = new StringBuilder();

        // 1. Системная инструкция
        prompt.append(SYSTEM_CODE).append("\n\n");

        // 2. Информация о целевом методе
        prompt.append("### ЦЕЛЕВОЙ МЕТОД\n");
        prompt.append(formatMethodInfo(context.getTargetMethod())).append("\n\n");

        // 3. Контекст вызываемых методов
        if (!context.getCalledMethods().isEmpty()) {
            prompt.append("### ВЫЗЫВАЕМЫЕ МЕТОДЫ (контекст)\n");
            for (MethodContext.MethodInfo info : context.getCalledMethods().values()) {
                prompt.append(formatCalledMethod(info)).append("\n");
            }
            prompt.append("\n");
        }

        // 4. DTO и модели данных (если есть в контексте)
        prompt.append("### МОДЕЛИ ДАННЫХ\n");
        prompt.append(formatDTOs(context)).append("\n\n");


        // 5. Список тест-кейсов
        prompt.append("### ТЕСТ-КЕЙСЫ ДЛЯ ГЕНЕРАЦИИ\n");
        prompt.append(testCases);
        prompt.append("\n");

        // 6. Дополнительные инструкции
        prompt.append("### ДОПОЛНИТЕЛЬНЫЕ ТРЕБОВАНИЯ\n");
        if (settings.isUseLLMForTestPlanning()) {
            prompt.append("- Проанализируй логику метода и предложи дополнительные пограничные кейсы\n");
        }
        prompt.append("- Если метод возвращает ResponseEntity, проверяй не только статус, но и тело\n");
        prompt.append("- Для POST/PUT методов генерируй валидные тестовые данные\n");
        prompt.append("- Для методов с @PathVariable и @RequestParam генерируй тесты с разными значениями\n");

        // 7. Финальная инструкция
        prompt.append("### ОТВЕТ\n");
        prompt.append("Сгенерируй полный тест-кейс пригодный для регистрации в Zephyr.");

        return prompt.toString();
    }
}