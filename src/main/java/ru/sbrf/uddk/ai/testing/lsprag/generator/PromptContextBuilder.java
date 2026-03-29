package ru.sbrf.uddk.ai.testing.lsprag.generator;

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;
import ru.sbrf.uddk.ai.testing.lsprag.context.MethodContext;

import java.util.UUID;

public class PromptContextBuilder {

    public static String buildPrompt(@NotNull MethodContext context) {

        StringBuilder prompt = new StringBuilder();

        prompt.append("## ИСХОДНЫЙ КОД МЕТОДА ДЛЯ ТЕСТА\n");
        prompt.append(formatMethodInfo(context.getTargetMethod())).append("\n\n");

        // 3. Контекст вызываемых методов
        if (!context.getCalledMethods().isEmpty()) {
            prompt.append("## ЗАВИСИМОСТИ МЕТОДА\n");
            prompt.append("Метод использует следующие зависимости:\n");
            for (MethodContext.MethodInfo info : context.getCalledMethods().values()) {
                prompt.append(formatCalledMethod(info)).append("\n");
            }
            prompt.append("\n");
        }

        // 4. DTO и модели данных
        prompt.append("## МОДЕЛИ ДАННЫХ\n");
        prompt.append(formatDTOs(context)).append("\n\n");


        return prompt.toString();
    }

    @NotNull
    private static String formatDTOs(@NotNull MethodContext context) {
        StringBuilder sb = new StringBuilder();

        if (context.getRequestDTOs().isEmpty() && context.getResponseDTOs().isEmpty()) {
            sb.append("**Примечание:** Метод использует примитивные типы или JDK-классы без кастомных DTO.\n");
            return sb.toString();
        }

        // Request DTOs
        if (!context.getRequestDTOs().isEmpty()) {
            sb.append("### Входные данные\n");
            for (MethodContext.DTOInfo dto : context.getRequestDTOs()) {
                sb.append(formatSingleDTO(dto)).append("\n");
            }
        }

        // Response DTOs
        if (!context.getResponseDTOs().isEmpty()) {
            sb.append("### Выходные данные\n");
            for (MethodContext.DTOInfo dto : context.getResponseDTOs()) {
                sb.append(formatSingleDTO(dto)).append("\n");
            }
        }

        return sb.toString();
    }

    @NotNull
    private static String formatSingleDTO(@NotNull MethodContext.DTOInfo dto) {
        StringBuilder sb = new StringBuilder();

        // Для примитивных типов и простых оберток
        String type = dto.getClassName().toLowerCase();
        if (type.contains("integer") || type.contains("long")) {
            return "```json\n123\n```\n";
        }
        if (type.contains("boolean")) {
            return "```json\ntrue\n```\n";
        }
        if (type.contains("double") || type.contains("float")) {
            return "```json\n123.45\n```\n";
        }
        if (type.contains("string")) {
            return "```json\n\"example-value\"\n```\n";
        }
        if (type.contains("list") || type.contains("array")) {
            return "```json\n[]\n```\n";
        }
        if (type.contains("map")) {
            return "```json\n{}\n```\n";
        }
        if (type.contains("uuid")) {
            return "```json\n\"" + UUID.randomUUID() + "\"\n```\n";
        }
        if (type.contains("localdate")) {
            return "```json\n\"2024-01-01\"\n```\n";
        }
        if (type.contains("localdatetime")) {
            return "```json\n\"2024-01-01T10:00:00\"\n```\n";
        }

        // Для кастомных DTO
        sb.append("#### `").append(dto.getClassName()).append("`");
        if (!dto.getCategory().isEmpty()) {
            sb.append(" *(").append(dto.getCategory()).append(")*");
        }
        sb.append("\n\n");

        sb.append("**Поля:**\n");
        for (MethodContext.DTOInfo.FieldInfo field : dto.getFields()) {
            sb.append("- **").append(field.getName()).append("**: `").append(field.getTypeName()).append("`");

            if (!field.getAnnotations().isEmpty()) {
                sb.append(" *Аннотации: ").append(String.join(", ", field.getAnnotations())).append("*");
            }

            if (field.isNullable()) {
                sb.append(" (может быть null)");
            }
            sb.append("\n");
        }
        sb.append("\n");

        sb.append("**Пример JSON:**\n```json\n").append(dto.getJsonExample()).append("\n```\n\n");

        return sb.toString();
    }


    @NotNull
    private static String formatCalledMethod(MethodContext.MethodInfo info) {
        if (info.getBodySnippet() != null) {
            return String.format("""
                            **Метод:** `%s`
                            **Возвращает:** `%s`
                            **Параметры:** `%s`
                            **Исключения:** %s
                                                        
                            ```java
                            %s
                            ```
                            """,
                    info.getSignature(),
                    info.getReturnType(),
                    String.join(", ", info.getParameters()),
                    info.getThrownExceptions().isEmpty() ? "нет" : String.join(", ", info.getThrownExceptions()),
                    info.getBodySnippet()
            );
        } else {
            return String.format("""
                            **Метод:** `%s`
                            **Возвращает:** `%s`
                            **Параметры:** `%s`
                            **Исключения:** %s
                            *Тело метода недоступно для анализа*
                            """,
                    info.getSignature(),
                    info.getReturnType(),
                    String.join(", ", info.getParameters()),
                    info.getThrownExceptions().isEmpty() ? "нет" : String.join(", ", info.getThrownExceptions())
            );
        }
    }

    @NotNull
    private static String formatMethodInfo(PsiMethod method) {
        StringBuilder sb = new StringBuilder();

        ReadAction.run(() -> {
            // Аннотации
            for (var ann : method.getAnnotations()) {
                sb.append(ann.getText()).append("\n");
            }

            // Сигнатура метода
            sb.append(method.getReturnType().getPresentableText())
                    .append(" ")
                    .append(method.getName())
                    .append("(");

            var params = method.getParameterList().getParameters();
            for (int i = 0; i < params.length; i++) {
                if (i > 0) sb.append(", ");
                sb.append(params[i].getType().getPresentableText())
                        .append(" ")
                        .append(params[i].getName());
            }
            sb.append(")");

            // Исключения
            var throwsList = method.getThrowsList().getReferencedTypes();
            if (throwsList.length > 0) {
                sb.append(" throws ");
                for (int i = 0; i < throwsList.length; i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(throwsList[i].getPresentableText());
                }
            }

            sb.append(" {\n");

            // Тело метода
            if (method.getBody() != null) {
                String body = method.getBody().getText();
                if (body.length() > 1500) {
                    body = body.substring(0, 1500) + "\n    // ... [код сокращен для краткости]\n";
                }
                sb.append(body).append("\n");
            } else {
                sb.append("    // абстрактный метод\n");
            }
            sb.append("}\n");
        });

        return "```java\n" + sb.toString() + "```\n";
    }
}
