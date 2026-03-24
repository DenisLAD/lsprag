package ru.sbrf.uddk.ai.testing.lsprag.ui;

import org.jetbrains.annotations.NotNull;
import ru.sbrf.uddk.ai.testing.lsprag.utils.LLMResponseParser;

public class LLMResponseViewer {

    /**
     * Форматирует ответ для отображения в диалоге
     */
    @NotNull
    public static DisplayInfo formatForDisplay(@NotNull String rawResponse) {
        String cleanCode = LLMResponseParser.extractJavaCode(rawResponse);

        // Статистика
        int lineCount = cleanCode.split("\n").length;
        int charCount = cleanCode.length();
        int methodCount = countTestMethods(cleanCode);
        int importCount = countImports(cleanCode);

        // Проверка валидности
        boolean isValid = LLMResponseParser.isValidTestClass(cleanCode);
        String className = LLMResponseParser.extractClassName(cleanCode);

        return new DisplayInfo(
                cleanCode,
                lineCount,
                charCount,
                methodCount,
                importCount,
                isValid,
                className
        );
    }

    private static int countTestMethods(@NotNull String code) {
        return (int) java.util.regex.Pattern.compile("@Test")
                .matcher(code)
                .results()
                .count();
    }

    private static int countImports(@NotNull String code) {
        return (int) java.util.regex.Pattern.compile("^import\\s+", java.util.regex.Pattern.MULTILINE)
                .matcher(code)
                .results()
                .count();
    }

    /**
     * Информация для отображения
     */
    public static class DisplayInfo {
        private final String code;
        private final int lineCount;
        private final int charCount;
        private final int methodCount;
        private final int importCount;
        private final boolean isValid;
        private final String className;

        public DisplayInfo(String code, int lineCount, int charCount,
                           int methodCount, int importCount,
                           boolean isValid, String className) {
            this.code = code;
            this.lineCount = lineCount;
            this.charCount = charCount;
            this.methodCount = methodCount;
            this.importCount = importCount;
            this.isValid = isValid;
            this.className = className;
        }

        public String getCode() { return code; }
        public int getLineCount() { return lineCount; }
        public int getCharCount() { return charCount; }
        public int getMethodCount() { return methodCount; }
        public int getImportCount() { return importCount; }
        public boolean isValid() { return isValid; }
        public String getClassName() { return className; }

        @NotNull
        public String getSummary() {
            return String.format("Class: %s | Lines: %d | Tests: %d | Imports: %d | Valid: %s",
                    className, lineCount, methodCount, importCount, isValid ? "✓" : "✗");
        }
    }
}