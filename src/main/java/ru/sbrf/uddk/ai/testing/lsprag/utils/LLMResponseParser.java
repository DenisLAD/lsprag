package ru.sbrf.uddk.ai.testing.lsprag.utils;

import org.jetbrains.annotations.NotNull;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LLMResponseParser {

    private static final Pattern JAVA_CODE_BLOCK = Pattern.compile(
            "```(?:java)?\\s*([\\s\\S]*?)```",
            Pattern.MULTILINE
    );

    private static final Pattern PACKAGE_DECLARATION = Pattern.compile(
            "^package\\s+[\\w.]+;",
            Pattern.MULTILINE
    );

    private static final Pattern IMPORT_STATEMENT = Pattern.compile(
            "^import\\s+[^;]+;",
            Pattern.MULTILINE
    );

    /**
     * Извлекает Java-код из ответа LLM, удаляя markdown и лишний текст
     */
    @NotNull
    public static String extractJavaCode(@NotNull String response) {
        String cleaned = response.trim();

        // 1. Пробуем найти код в markdown-блоке
        Matcher matcher = JAVA_CODE_BLOCK.matcher(cleaned);
        if (matcher.find()) {
            cleaned = matcher.group(1).trim();
        }

        // 2. Удаляем возможные пояснения до/после кода
        // Ищем начало с package или import или public class
        int codeStart = findCodeStart(cleaned);
        if (codeStart > 0) {
            cleaned = cleaned.substring(codeStart);
        }

        // 3. Удаляем возможные пояснения в конце (после закрывающей })
        cleaned = trimTrailingText(cleaned);

        // 4. Нормализуем переносы строк
        cleaned = cleaned.replaceAll("\\r\\n?", "\n");

        return cleaned;
    }

    private static int findCodeStart(@NotNull String text) {
        // Ищем package
        Matcher pkgMatcher = PACKAGE_DECLARATION.matcher(text);
        if (pkgMatcher.find()) {
            return pkgMatcher.start();
        }

        // Ищем import
        Matcher importMatcher = IMPORT_STATEMENT.matcher(text);
        if (importMatcher.find()) {
            // Возвращаем начало первого import
            return importMatcher.start();
        }

        // Ищем public class / class
        Pattern classPattern = Pattern.compile("(?:^|\\n)\\s*(?:public\\s+)?(?:abstract\\s+)?(?:final\\s+)?class\\s+\\w+");
        Matcher classMatcher = classPattern.matcher(text);
        if (classMatcher.find()) {
            return classMatcher.start();
        }

        // Если ничего не нашли, возвращаем 0
        return 0;
    }

    private static String trimTrailingText(@NotNull String code) {
        // Находим последнюю закрывающую скобку класса
        int lastBrace = code.lastIndexOf('}');
        if (lastBrace > 0 && lastBrace < code.length() - 1) {
            // Проверяем, что после } только пробелы и переносы
            String after = code.substring(lastBrace + 1).trim();
            if (after.isEmpty() || after.matches("^[)}\\s]*$")) {
                return code.substring(0, lastBrace + 1).trim();
            }
        }
        return code;
    }

    /**
     * Валидирует, что код содержит минимально необходимую структуру
     */
    public static boolean isValidTestClass(@NotNull String code) {
        return code.contains("class") &&
                (code.contains("@Test") || code.contains("void test")) &&
                code.contains("import");
    }

    /**
     * Извлекает имя класса из кода
     */
    @NotNull
    public static String extractClassName(@NotNull String code) {
        Pattern pattern = Pattern.compile("class\\s+(\\w+)");
        Matcher matcher = pattern.matcher(code);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "GeneratedTest";
    }
}