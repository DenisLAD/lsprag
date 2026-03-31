package ru.sbrf.uddk.ai.testing.lsprag.generator;

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiIfStatement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import ru.sbrf.uddk.ai.testing.lsprag.context.MethodContext;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class PromptContextBuilder {

    public static String buildPrompt(@NotNull MethodContext context) {

        StringBuilder prompt = new StringBuilder();

        prompt.append("## ИСХОДНЫЙ КОД МЕТОДА ДЛЯ ТЕСТА\n");
        prompt.append(formatMethodInfo(context.getTargetMethod())).append("\n\n");

        String httpMethod = detectHttpMethod(context.getTargetMethod());
        if (!"UNKNOWN".equals(httpMethod)) {
            prompt.append("## ТИП ЭНДПОИНТА: ").append(httpMethod).append("\n");
            switch (httpMethod) {
                case "POST":
                    prompt.append("Для POST-эндпоинтов обязательно проверять:\n");
                    prompt.append("- Статус 201 Created\n");
                    prompt.append("- Заголовок Location с URL созданного ресурса\n");
                    prompt.append("- Возврат созданного объекта с id\n");
                    break;
                case "GET":
                    prompt.append("Для GET-эндпоинтов проверять:\n");
                    prompt.append("- Корректный ответ для существующего ресурса (200 OK)\n");
                    prompt.append("- Обработку несуществующего ресурса (404 Not Found)\n");
                    break;
                case "PUT":
                case "PATCH":
                    prompt.append("Для методов обновления проверять:\n");
                    prompt.append("- Обновление существующего ресурса (200 OK)\n");
                    prompt.append("- Попытку обновления несуществующего ресурса (404 Not Found)\n");
                    prompt.append("- Валидацию входных данных (400 Bad Request)\n");
                    break;
                case "DELETE":
                    prompt.append("Для DELETE-эндпоинтов проверять:\n");
                    prompt.append("- Удаление существующего ресурса (204 No Content или 200 OK)\n");
                    prompt.append("- Повторное удаление (404 Not Found)\n");
                    break;
            }
        }
        prompt.append(analyzeControlFlow(context.getTargetMethod())).append("\n");


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

    private static String detectHttpMethod(PsiMethod method) {
        return ReadAction.compute(() -> {
            for (PsiAnnotation ann : method.getAnnotations()) {
                String qn = ann.getQualifiedName();
                if (qn == null) continue;
                if (qn.contains("GetMapping")) return "GET";
                if (qn.contains("PostMapping")) return "POST";
                if (qn.contains("PutMapping")) return "PUT";
                if (qn.contains("DeleteMapping")) return "DELETE";
                if (qn.contains("PatchMapping")) return "PATCH";
                if (qn.contains("RequestMapping")) {
                    PsiAnnotationMemberValue methodAttr = ann.findAttributeValue("method");
                    if (methodAttr instanceof PsiReferenceExpression ref) {
                        String refName = ref.getReferenceName();
                        if (refName != null) {
                            if (refName.equals("GET")) return "GET";
                            if (refName.equals("POST")) return "POST";
                            if (refName.equals("PUT")) return "PUT";
                            if (refName.equals("DELETE")) return "DELETE";
                        }
                    }
                }
            }
            return "UNKNOWN";
        });
    }

    private static String analyzeControlFlow(PsiMethod method) {
        return ReadAction.compute(() -> {
            Set<String> conditions = new HashSet<>();
            for (PsiIfStatement ifStmt : PsiTreeUtil.findChildrenOfType(method, PsiIfStatement.class)) {
                String condition = ifStmt.getCondition().getText();
                condition = condition.replaceAll("\\s+", " ").trim();
                conditions.add(condition);
            }
            if (!conditions.isEmpty()) {
                StringBuilder sb = new StringBuilder("\n### ВЕТВЛЕНИЯ В МЕТОДЕ\n");
                sb.append("Метод содержит следующие условия:\n");
                for (String cond : conditions) {
                    sb.append("- `").append(cond).append("`\n");
                }
                sb.append("Рекомендуется создать тест-кейсы для каждой ветки.\n");
                return sb.toString();
            }
            return "";
        });
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
            Map<String, Object> constraints = field.getValidationConstraints();
            if (constraints != null && !constraints.isEmpty()) {
                sb.append(" *Ограничения: ");
                if (constraints.containsKey("required")) {
                    sb.append("обязательное поле; ");
                }
                if (constraints.containsKey("size")) {
                    Map<String, Integer> size = (Map<String, Integer>) constraints.get("size");
                    if (size.containsKey("min")) sb.append("min=").append(size.get("min")).append("; ");
                    if (size.containsKey("max")) sb.append("max=").append(size.get("max")).append("; ");
                }
                if (constraints.containsKey("pattern")) {
                    sb.append("формат: ").append(constraints.get("pattern")).append("; ");
                }
                sb.append("*");
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


//    private static String simplifyBody(String body) {
//        String[] lines = body.split("\n");
//        StringBuilder result = new StringBuilder();
//        for (String line : lines) {
//            String trimmed = line.trim();
//            if (trimmed.isEmpty() || trimmed.startsWith("//") || trimmed.startsWith("*")) continue;
//            boolean keep = trimmed.matches(".*\\b(if|for|while|try|catch|throw|return|new|\\w+\\.\\w+\\(|\\w+\\s*=\\s*\\w+\\.\\w+\\().*");
//            keep |= trimmed.matches(".*\\b(Service|Repository|Mapper|Client|Dao)\\s+\\w+\\s*=.*");
//            keep |= trimmed.contains(".") && trimmed.contains("(") && !trimmed.startsWith("//");
//            if (keep) {
//                result.append(line).append("\n");
//            }
//        }
//        return result.toString();
//    }

    private static String simplifyBody(String body) {
        if (body == null) return "";
        String[] lines = body.split("\n");
        StringBuilder result = new StringBuilder();
        for (String line : lines) {
            String trimmed = line.trim();
            // Пропускаем пустые строки
            if (trimmed.isEmpty()) continue;
            // Пропускаем однострочные комментарии
            if (trimmed.startsWith("//")) continue;
            // Пропускаем строки, состоящие только из скобок (не несут смысла)
            if (trimmed.equals("{") || trimmed.equals("}")) continue;
            result.append(line).append("\n");
        }
        return result.toString();
    }

    @NotNull
    private static String formatCalledMethod(MethodContext.MethodInfo info) {
        if (info.getBodySnippet() != null) {
            return String.format("""
                            **Метод:** `%s`
                            **Возвращает:** `%s`
                            **Параметры:** `%s`
                            **Исключения:** %s
                            
                            %s
                            
                            """,
                    info.getSignature(),
                    info.getReturnType(),
                    String.join(", ", info.getParameters()),
                    info.getThrownExceptions().isEmpty() ? "нет" : String.join(", ", info.getThrownExceptions()),
                    simplifyBody(info.getBodySnippet())
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
