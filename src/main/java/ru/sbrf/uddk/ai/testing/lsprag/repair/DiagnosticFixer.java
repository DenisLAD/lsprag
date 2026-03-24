package ru.sbrf.uddk.ai.testing.lsprag.repair;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import org.jetbrains.annotations.NotNull;
import ru.sbrf.uddk.ai.testing.lsprag.llm.LLMGateway;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

public class DiagnosticFixer {

    private final Project project;
    private final LLMGateway llmGateway;
    private final int maxAttempts;

    public DiagnosticFixer(Project project, LLMGateway llmGateway, int maxAttempts) {
        this.project = project;
        this.llmGateway = llmGateway;
        this.maxAttempts = maxAttempts;
    }

    @NotNull
    public String fixErrors(@NotNull String code) throws LLMGateway.LLMException, IOException {
        String currentCode = code;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            List<HighlightInfo> errors = analyzeCode(currentCode);

            if (errors.isEmpty()) {
                return currentCode; // Успех
            }

            String errorContext = buildErrorContext(errors);
            String fixPrompt = buildFixPrompt(currentCode, errorContext);

            currentCode = llmGateway.generate(fixPrompt);
            currentCode = cleanCodeBlock(currentCode);
        }

        return currentCode; // Возвращаем лучший результат после лимита
    }

    @NotNull
    private List<HighlightInfo> analyzeCode(String code) {
        // Создаём временный файл для анализа
        FileType javaType = FileTypeManager.getInstance().getFileTypeByExtension("java");

        return ApplicationManager.getApplication().runReadAction((Computable<? extends List<HighlightInfo>>) () -> {
            PsiFile tempFile = PsiFileFactory.getInstance(project)
                    .createFileFromText("TempTest.java", javaType, code);

            Document doc = PsiDocumentManager.getInstance(project).getDocument(tempFile);
            if (doc == null) return List.of();

            // Запускаем локальные инспекции
            List<HighlightInfo> errors = new java.util.ArrayList<>();
//            HighlightInfoProcessor processor = (info, forced) -> {
//                if (info.getSeverity().isError()) {
//                    errors.add(info);
//                }
//                return true;
//            };
//
//            new LocalInspectionsPass(tempFile, doc, new TextRange(0, doc.getTextLength()), new TextRange(0, doc.getTextLength()), false,
//                    processor, false).collectInformationWithProgress();

            return errors;
        });
    }

    @NotNull
    private String buildErrorContext(List<HighlightInfo> errors) {
        return errors.stream()
                .map(e -> String.format("- Line %d: %s",
                        e.getStartOffset(), e.getDescription()))
                .collect(Collectors.joining("\n"));
    }

    @NotNull
    private String buildFixPrompt(String code, String errorContext) {
        return """
                You are a Java code repair expert. Fix the following compilation errors:
                            
                ERRORS:
                %s
                            
                CODE TO FIX:
                ```java
                %s
                ```
                            
                Return ONLY the corrected Java code, no explanations, no markdown.
                """.formatted(errorContext, code);
    }

    @NotNull
    private String cleanCodeBlock(String response) {
        // Удаляем markdown-блоки ```java ... ```
        if (response.contains("```")) {
            int start = response.indexOf("```java");
            if (start == -1) start = response.indexOf("```");
            if (start != -1) {
                int end = response.indexOf("```", start + 3);
                if (end != -1) {
                    return response.substring(start + (response.startsWith("```java", start) ? 7 : 3), end).trim();
                }
            }
        }
        return response.trim();
    }
}
