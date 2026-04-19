package com.reasoningtestgen.validator;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.compiler.CompilerMessage;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.reasoningtestgen.llm.LLMProvider;
import com.reasoningtestgen.model.GeneratedCode;
import com.reasoningtestgen.model.TestDesign;
import com.reasoningtestgen.service.PromptHistoryService;
import com.reasoningtestgen.model.PromptEntry.ReasoningStep;
import com.reasoningtestgen.validator.RealCompilationValidator.CompilationError;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Compiler Loop Engine: Cyclic compilation validation with LLM-based error correction
 * Implements iterative compile-fix-validate cycle until code compiles or max attempts reached
 */
public class CompilerLoopEngine {

    private static final Logger LOG = LoggerFactory.getLogger(CompilerLoopEngine.class);

    private final Project project;
    private final LLMProvider llmProvider;
    private final PromptHistoryService promptHistoryService;
    private final RealCompilationValidator realValidator;

    // Configuration
    private final int maxAttempts;
    private final boolean continueUntilSuccess;
    private final long compilationTimeoutSeconds;

    /**
     * Create compiler loop engine
     */
    public CompilerLoopEngine(@NotNull Project project,
                               @NotNull LLMProvider llmProvider,
                               @Nullable PromptHistoryService promptHistoryService,
                               int maxAttempts,
                               boolean continueUntilSuccess) {
        this.project = project;
        this.llmProvider = llmProvider;
        this.promptHistoryService = promptHistoryService;
        this.realValidator = new RealCompilationValidator(project);
        this.maxAttempts = maxAttempts;
        this.continueUntilSuccess = continueUntilSuccess;
        this.compilationTimeoutSeconds = 30;
    }

    /**
     * Run compiler loop: compile → analyze errors → fix → repeat
     * @param originalCode Original generated code
     * @param testDesign Test design specifications
     * @return Correction result with fixed code
     */
    @NotNull
    public CompilerLoopResult runCompilerLoop(@NotNull GeneratedCode originalCode,
                                               @NotNull TestDesign testDesign) {
        LOG.info("Starting compiler loop engine");
        LOG.info("Configuration: maxAttempts={}, continueUntilSuccess={}", 
                 maxAttempts, continueUntilSuccess);

        String currentCode = originalCode.javaCode();
        List<CompilerLoopAttempt> attempts = new ArrayList<>();

        int attempt = 0;
        boolean success = false;
        List<RealCompilationValidator.CompilationError> remainingErrors = new ArrayList<>();

        while (!success && (continueUntilSuccess || attempt < maxAttempts)) {
            attempt++;
            LOG.info("===== Compiler Loop Attempt {}/{} =====", 
                     attempt, continueUntilSuccess ? "∞" : maxAttempts);

            try {
                // Step 1: Compile and validate
                RealCompilationValidator.ValidationResult compilationResult = compileCode(currentCode);

                if (compilationResult.isValid()) {
                    LOG.info("✓ Compilation PASSED on attempt {}", attempt);
                    success = true;
                    remainingErrors.clear();
                    break;
                }

                LOG.warn("✗ Compilation FAILED with {} errors", compilationResult.errors().size());

                // Step 2: Analyze errors with LLM
                String errorAnalysis = analyzeCompilationErrors(
                    currentCode, 
                    compilationResult.errors(),
                    testDesign
                );

                // Step 3: Generate fix with LLM
                String fixedCode = generateFix(
                    currentCode,
                    compilationResult.errors(),
                    errorAnalysis,
                    testDesign
                );

                // Record attempt
                CompilerLoopAttempt loopAttempt = new CompilerLoopAttempt(
                    attempt,
                    currentCode,
                    fixedCode,
                    compilationResult.errors(),
                    errorAnalysis,
                    true,
                    "Applied LLM-based fix"
                );
                attempts.add(loopAttempt);

                // Update code for next iteration
                currentCode = fixedCode;

                // Store prompt history if available
                if (promptHistoryService != null) {
                    promptHistoryService.storePrompt(
                        ReasoningStep.CODE_REFINEMENT,
                        "Compiler loop attempt " + attempt,
                        "Fixed compilation errors: " + compilationResult.errors().size(),
                        fixedCode,
                        "compiler-loop",
                        0,
                        false
                    );
                }

            } catch (Exception e) {
                LOG.error("Compiler loop attempt {} failed", attempt, e);
                
                CompilerLoopAttempt failedAttempt = new CompilerLoopAttempt(
                    attempt,
                    currentCode,
                    currentCode,
                    List.of(new RealCompilationValidator.CompilationError(-1, "ERROR", e.getMessage())),
                    null,
                    false,
                    "Exception: " + e.getMessage()
                );
                attempts.add(failedAttempt);
                
                // Don't break immediately - try next attempt if configured
                if (!continueUntilSuccess) {
                    break;
                }
            }
        }

        // Final validation
        if (!success) {
            remainingErrors = compileCode(currentCode).errors();
        }

        LOG.info("Compiler loop completed: {} attempts, success={}", attempt, success);

        return new CompilerLoopResult(
            originalCode,
            currentCode,
            success,
            attempt,
            attempts,
            remainingErrors
        );
    }

    /**
     * Compile code and return validation result
     */
    @NotNull
    private RealCompilationValidator.ValidationResult compileCode(@NotNull String code) {
        return ReadAction.compute(() -> {
            try {
                // Create temporary PSI file
                PsiFile tempFile = PsiFileFactory.getInstance(project)
                    .createFileFromText("TempTest.java", StdFileTypes.JAVA, code);

                // Get virtual file for compilation
                VirtualFile virtualFile = tempFile.getVirtualFile();
                if (virtualFile == null) {
                    // Create virtual file manually
                    virtualFile = new com.intellij.testFramework.LightVirtualFile(
                        "TempTest.java",
                        StdFileTypes.JAVA,
                        code
                    );
                }

                // Run real compilation
                return realValidator.validateCompilation(virtualFile);

            } catch (Exception e) {
                LOG.error("Compilation failed", e);
                List<RealCompilationValidator.CompilationError> errors = new ArrayList<>();
                errors.add(new RealCompilationValidator.CompilationError(-1, "ERROR", e.getMessage()));
                return new RealCompilationValidator.ValidationResult(false, errors);
            }
        });
    }

    /**
     * Analyze compilation errors using LLM
     */
    @NotNull
    private String analyzeCompilationErrors(@NotNull String code,
                                             @NotNull List<RealCompilationValidator.CompilationError> errors,
                                             @NotNull TestDesign testDesign) {
        String systemPrompt = """
            Вы — Senior Java Developer, анализирующий ошибки компиляции.
            Проанализируйте ошибки и предложите конкретные исправления.
            
            Для каждой ошибки укажите:
            1. Тип ошибки (синтаксис, импорты, типы, и т.д.)
            2. Причину возникновения
            3. Конкретное исправление
            
            Отвечайте структурированно на русском языке.
            """;

        String errorsText = errors.stream()
            .map(err -> String.format("- Line %d: %s - %s", 
                                     err.line() > 0 ? err.line() : "?",
                                     err.category(),
                                     err.description()))
            .collect(Collectors.joining("\n"));

        String userPrompt = String.format("""
            Проанализируйте следующие ошибки компиляции:
            
            ## Ошибки (%d)
            %s
            
            ## Тестовый код
            ```java
            %s
            ```
            
            ## Требования к тестам
            - Framework: %s
            - Mocking: %s
            - Assertions: %s
            
            Предложите план исправления ошибок.
            """,
            errors.size(),
            errorsText,
            code,
            testDesign.framework(),
            testDesign.mockingStrategy(),
            testDesign.assertionLibrary()
        );

        try {
            String analysis = llmProvider.chat(userPrompt, systemPrompt);
            LOG.debug("Error analysis received ({} chars)", analysis.length());
            return analysis;
        } catch (Exception e) {
            LOG.error("Error analysis failed", e);
            return "Failed to analyze errors: " + e.getMessage();
        }
    }

    /**
     * Generate code fix using LLM
     */
    @NotNull
    private String generateFix(@NotNull String currentCode,
                                @NotNull List<RealCompilationValidator.CompilationError> errors,
                                @NotNull String analysis,
                                @NotNull TestDesign testDesign) throws Exception {
        String systemPrompt = """
            Вы — Senior Java Developer, исправляющий ошибки компиляции в коде теста.
            Верните ТОЛЬКО исправленный код Java без дополнительных объяснений.
            
            Важно:
            - Сохраните структуру теста
            - Исправьте ВСЕ ошибки
            - Убедитесь, что все импорты присутствуют
            - Используйте правильные типы и методы
            """;

        String errorsText = errors.stream()
            .map(err -> String.format("- Line %d: %s - %s", 
                                     err.line() > 0 ? err.line() : "?",
                                     err.category(),
                                     err.description()))
            .collect(Collectors.joining("\n"));

        String userPrompt = String.format("""
            Исправьте ошибки компиляции в коде теста.
            
            ## Ошибки для исправления
            %s
            
            ## Анализ ошибок
            %s
            
            ## Текущий код (с ошибками)
            ```java
            %s
            ```
            
            ## Требования
            - Framework: %s
            - Mocking Strategy: %s
            - Assertion Library: %s
            
            Верните ПОЛНЫЙ исправленный код Java класса.
            Не используйте markdown блоки (```java). Просто код.
            """,
            errorsText,
            analysis,
            currentCode,
            testDesign.framework(),
            testDesign.mockingStrategy(),
            testDesign.assertionLibrary()
        );

        String response = llmProvider.chat(userPrompt, systemPrompt);
        String fixedCode = extractCodeFromResponse(response);
        
        LOG.info("Generated fix ({} chars)", fixedCode.length());
        return fixedCode;
    }

    /**
     * Extract code from LLM response (remove markdown blocks)
     */
    @NotNull
    private String extractCodeFromResponse(@NotNull String response) {
        // Remove markdown code blocks if present
        String code = response;
        
        int startIndex = code.indexOf("```java");
        if (startIndex == -1) {
            startIndex = code.indexOf("```");
        }
        
        if (startIndex != -1) {
            int codeStart = code.indexOf('\n', startIndex) + 1;
            int endIndex = code.indexOf("```", codeStart);
            if (endIndex != -1) {
                code = code.substring(codeStart, endIndex).trim();
            }
        }
        
        // Remove any leading/trailing whitespace
        return code.trim();
    }

    /**
     * Quick PSI validation (faster than compilation)
     * Checks for basic syntax errors without full compilation
     */
    @NotNull
    public PsiValidationResult quickPsiValidation(@NotNull String code) {
        return ReadAction.compute(() -> {
            List<String> errors = new ArrayList<>();
            
            try {
                // Create PSI file
                PsiFile file = PsiFileFactory.getInstance(project)
                    .createFileFromText("TempTest.java", StdFileTypes.JAVA, code);
                
                // Check for PSI errors
                file.accept(new com.intellij.psi.PsiRecursiveElementVisitor() {
                    @Override
                    public void visitErrorElement(com.intellij.psi.PsiErrorElement element) {
                        errors.add(element.getErrorDescription());
                    }
                });
                
                // Basic structure checks
                if (!code.contains("class ") && !code.contains("interface ")) {
                    errors.add("No class or interface definition found");
                }
                
                if (code.contains("@Test") && !code.contains("void ")) {
                    errors.add("Invalid @Test method signature (should return void)");
                }
                
                // Check for unclosed braces
                int openBraces = code.replace("{", "").length();
                int closeBraces = code.replace("}", "").length();
                if (openBraces != closeBraces) {
                    errors.add("Mismatched braces: " + openBraces + " open, " + closeBraces + " close");
                }
                
            } catch (Exception e) {
                errors.add("PSI validation error: " + e.getMessage());
            }
            
            return new PsiValidationResult(errors.isEmpty(), errors);
        });
    }

    // ===== Result Records =====

    /**
     * Result of compiler loop
     */
    public record CompilerLoopResult(
        @NotNull GeneratedCode originalCode,
        @NotNull String correctedCode,
        boolean success,
        int attemptsCount,
        @NotNull List<CompilerLoopAttempt> attempts,
        @NotNull List<RealCompilationValidator.CompilationError> remainingErrors
    ) {
        @Override
        public String toString() {
            return String.format("CompilerLoopResult{success=%s, attempts=%d, errors=%d}",
                success, attemptsCount, remainingErrors.size());
        }
    }

    /**
     * Single attempt in compiler loop
     */
    public record CompilerLoopAttempt(
        int attemptNumber,
        @NotNull String codeBefore,
        @NotNull String codeAfter,
        @NotNull List<RealCompilationValidator.CompilationError> errors,
        @Nullable String analysis,
        boolean fixApplied,
        @Nullable String fixDescription
    ) {
        @Override
        public String toString() {
            return String.format("Attempt #%d: %d errors, fix applied=%s",
                attemptNumber, errors.size(), fixApplied);
        }
    }

    /**
     * PSI validation result (quick check)
     */
    public record PsiValidationResult(
        boolean isValid,
        @NotNull List<String> errors
    ) {
        @Override
        public String toString() {
            return isValid ? "PSI Validation PASSED" : 
                           "PSI Validation FAILED: " + errors.size() + " errors";
        }
    }
}
