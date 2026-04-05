package com.reasoningtestgen.refiner;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.reasoningtestgen.llm.LLMProvider;
import com.reasoningtestgen.model.*;
import com.reasoningtestgen.model.PromptEntry.ReasoningStep;
import com.reasoningtestgen.service.PromptHistoryService;
import com.reasoningtestgen.settings.PluginSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Self-correction engine using LLM reasoning
 * Simple synchronous validation using PSI (NO VirtualFile, NO CompilationValidator)
 * According to ANALYTICS.md Section 8.3
 */
public class SelfCorrectionEngine {

    private static final Logger LOG = LoggerFactory.getLogger(SelfCorrectionEngine.class);

    private final LLMProvider llmProvider;
    private final Project project;
    @Nullable
    private final PromptHistoryService promptHistoryService;
    private final PluginSettings settings;

    public SelfCorrectionEngine(@NotNull LLMProvider llmProvider,
                                 @NotNull Project project,
                                 @NotNull PluginSettings settings,
                                 @Nullable PromptHistoryService promptHistoryService) {
        this.llmProvider = llmProvider;
        this.project = project;
        this.settings = settings;
        this.promptHistoryService = promptHistoryService;
    }

    /**
     * Correct generated test code through reasoning loop
     */
    @NotNull
    public CorrectionResult correctCode(@NotNull GeneratedCode code,
                                         @NotNull TestDesign design,
                                         @NotNull String originalCode) {
        LOG.info("Starting self-correction engine");

        String currentCode = originalCode;
        List<CorrectionAttempt> attempts = new ArrayList<>();
        
        int maxAttempts = settings.isCorrectUntilSuccess() ? 999 : settings.getMaxCorrectionAttempts();

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            LOG.info("Correction attempt {}/{}", attempt, 
                settings.isCorrectUntilSuccess() ? "∞" : settings.getMaxCorrectionAttempts());
            
            // Simple synchronous PSI validation
            List<String> errors = validateCode(currentCode);
            
            CorrectionAttempt correctionAttempt = new CorrectionAttempt(
                attempt,
                currentCode,
                errors.size(),
                errors,
                false,
                null,
                null
            );
            attempts.add(correctionAttempt);
            
            if (errors.isEmpty()) {
                LOG.info("Code validation passed on attempt {}", attempt);
                
                if (promptHistoryService != null) {
                    promptHistoryService.storePrompt(
                        ReasoningStep.CODE_REFINEMENT,
                        "Self-correction validation",
                        "Code validated successfully",
                        currentCode,
                        settings.getModel(),
                        0,
                        true
                    );
                }
                
                return new CorrectionResult(
                    code,
                    currentCode,
                    true,
                    attempt,
                    attempts,
                    List.of()
                );
            }
            
            LOG.warn("Validation found {} errors", errors.size());
            
            // Analyze and fix errors
            try {
                String analysis = analyzeErrors(currentCode, errors, design);
                String fixedCode = generateFix(currentCode, errors, analysis, design);
                
                correctionAttempt = new CorrectionAttempt(
                    attempt,
                    currentCode,
                    errors.size(),
                    errors,
                    true,
                    "Applied fix from analysis",
                    null
                );
                attempts.set(attempts.size() - 1, correctionAttempt);
                
                currentCode = fixedCode;
                
            } catch (Exception e) {
                LOG.error("Failed to generate fix on attempt {}", attempt, e);
                // Don't break - return what we have
                return new CorrectionResult(
                    code,
                    currentCode,
                    false,
                    attempt,
                    attempts,
                    errors
                );
            }
        }
        
        // Max attempts reached
        LOG.warn("Max correction attempts reached");
        return new CorrectionResult(
            code,
            currentCode,
            false,
            maxAttempts,
            attempts,
            validateCode(currentCode)
        );
    }

    /**
     * Simple synchronous PSI validation (NO VirtualFile needed)
     */
    @NotNull
    private List<String> validateCode(@NotNull String code) {
        return ReadAction.compute(() -> {
            List<String> errors = new ArrayList<>();

            try {
                // Create temporary PSI file
                PsiFile tempFile = PsiFileFactory.getInstance(project)
                    .createFileFromText("TempTest.java", StdFileTypes.JAVA, code);

                // Check for PSI errors
                tempFile.accept(new PsiRecursiveElementVisitor() {
                    @Override
                    public void visitErrorElement(@NotNull PsiErrorElement element) {
                        super.visitErrorElement(element);
                        errors.add(element.getErrorDescription());
                    }
                });

                // Basic checks
                if (!code.contains("import ") && code.contains("class ")) {
                    errors.add("Missing imports");
                }

                if (code.contains("@Test") && !code.contains("void ")) {
                    errors.add("Invalid test method signature");
                }

            } catch (Exception e) {
                LOG.error("Validation error", e);
                errors.add("Validation error: " + e.getMessage());
            }

            return errors;
        });
    }

    /**
     * Analyze errors with LLM
     */
    @NotNull
    private String analyzeErrors(@NotNull String code,
                                  @NotNull List<String> errors,
                                  @NotNull TestDesign design) {
        String systemPrompt = "Вы — Senior Java Developer анализирующий ошибки компиляции. Предложите исправления.";

        String userPrompt = String.format("""
            Ошибки:
            %s

            Код:
            ```java
            %s
            ```
            """,
            String.join("\n", errors),
            code
        );

        try {
            return llmProvider.chat(userPrompt, systemPrompt);
        } catch (Exception e) {
            LOG.error("Analysis failed", e);
            return "Failed to analyze errors";
        }
    }

    /**
     * Generate fix
     */
    @NotNull
    private String generateFix(@NotNull String code,
                                @NotNull List<String> errors,
                                @NotNull String analysis,
                                @NotNull TestDesign design) throws Exception {
        String systemPrompt = "Исправьте ошибки компиляции в Java коде. Верните ТОЛЬКО исправленный код.";

        String userPrompt = String.format("""
            Ошибки:
            %s

            Анализ:
            %s

            Код:
            ```java
            %s
            ```
            """,
            String.join("\n", errors),
            analysis,
            code
        );

        String response = llmProvider.chat(userPrompt, systemPrompt);
        return extractCode(response);
    }

    @NotNull
    private String extractCode(@NotNull String response) {
        int start = response.indexOf("```java");
        if (start == -1) start = response.indexOf("```");
        
        if (start != -1) {
            int codeStart = response.indexOf('\n', start) + 1;
            int end = response.indexOf("```", codeStart);
            if (end != -1) {
                return response.substring(codeStart, end).trim();
            }
        }
        return response;
    }

    // ===== Records =====

    public record CorrectionResult(
        @NotNull GeneratedCode originalCode,
        @NotNull String correctedCode,
        boolean success,
        int attemptsCount,
        @NotNull List<CorrectionAttempt> attempts,
        @NotNull List<String> remainingErrors
    ) {}

    public record CorrectionAttempt(
        int attemptNumber,
        @NotNull String codeBefore,
        int errorCount,
        @NotNull List<String> errors,
        boolean fixApplied,
        @Nullable String fixDescription,
        @Nullable String errorMessage
    ) {}
}
