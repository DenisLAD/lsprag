package com.reasoningtestgen.validator;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileStatusNotification;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Validates generated tests by compiling them
 * According to ANALYTICS.md Section 5.4 - Validator
 */
public class CompilationValidator {

    private static final Logger LOG = LoggerFactory.getLogger(CompilationValidator.class);
    private static final long COMPILATION_TIMEOUT_SECONDS = 30;

    private final Project project;

    public CompilationValidator(@NotNull Project project) {
        this.project = project;
    }

    /**
     * Compile test file and collect errors
     */
    @NotNull
    public ValidationResult validateCompilation(@NotNull PsiFile testFile) {
        List<CompilationError> errors = new ArrayList<>();
        CompletableFuture<List<CompilationError>> future = new CompletableFuture<>();

        VirtualFile virtualFile = testFile.getVirtualFile();
        if (virtualFile == null) {
            errors.add(new CompilationError(
                -1,
                "Test file has no virtual file",
                "Cannot validate test"
            ));
            return new ValidationResult(false, errors);
        }

        CompileStatusNotification callback = (aborted, errorsCount, warningsCount, context) -> {
            if (aborted) {
                future.completeExceptionally(new RuntimeException("Compilation aborted"));
            } else {
                // Get compiler messages from context
                List<CompilationError> compilationErrors = extractErrors(context);
                future.complete(compilationErrors);
            }
        };

        try {
            LOG.info("Starting compilation validation for: {}", testFile.getName());
            
            CompilerManager.getInstance(project).compile(
                new VirtualFile[]{virtualFile},
                callback
            );

            List<CompilationError> compilationErrors = future.get(
                COMPILATION_TIMEOUT_SECONDS, 
                TimeUnit.SECONDS
            );

            boolean isValid = compilationErrors.isEmpty();
            LOG.info("Compilation validation {}: {} errors found", 
                    isValid ? "PASSED" : "FAILED", compilationErrors.size());

            return new ValidationResult(isValid, compilationErrors);

        } catch (TimeoutException e) {
            LOG.warn("Compilation validation timed out after {} seconds", COMPILATION_TIMEOUT_SECONDS);
            errors.add(new CompilationError(
                -1,
                "Compilation timed out",
                "Validation skipped due to timeout"
            ));
            return new ValidationResult(false, errors);
        } catch (Exception e) {
            LOG.error("Compilation validation failed", e);
            errors.add(new CompilationError(
                -1,
                "Compilation failed: " + e.getMessage(),
                "Validation error"
            ));
            return new ValidationResult(false, errors);
        }
    }

    /**
     * Extract compilation errors from context
     */
    @NotNull
    private List<CompilationError> extractErrors(@NotNull CompileContext context) {
        List<CompilationError> errors = new ArrayList<>();
        
        // Get error messages from compiler
        try {
            // Get all error messages
            com.intellij.openapi.compiler.CompilerMessage[] errorMessages = 
                context.getMessages(com.intellij.openapi.compiler.CompilerMessageCategory.ERROR);
            
            for (com.intellij.openapi.compiler.CompilerMessage message : errorMessages) {
                CompilationError error = new CompilationError(
                    -1,
                    "Compilation error",
                    message.getMessage()
                );
                errors.add(error);
            }
        } catch (Exception e) {
            LOG.warn("Failed to extract compilation errors", e);
        }
        
        return errors;
    }

    /**
     * Parse error message to structured format
     */
    @Nullable
    private CompilationError parseErrorMessage(@NotNull String message) {
        // Simple parsing - in real implementation would use IDEA's error format
        // Format: "file.java:line:column: error: message"
        String[] parts = message.split(":");
        if (parts.length >= 4) {
            try {
                int line = Integer.parseInt(parts[1].trim());
                String errorType = parts[2].trim();
                String description = parts[3].trim();
                return new CompilationError(line, errorType, description);
            } catch (NumberFormatException e) {
                // If parsing fails, return generic error
                return new CompilationError(-1, "Compilation error", message);
            }
        }
        return null;
    }

    /**
     * Compilation error representation
     */
    public record CompilationError(
        int line,
        String errorType,
        String description
    ) {
        @Override
        public String toString() {
            return line > 0 ? 
                String.format("Line %d: %s - %s", line, errorType, description) :
                String.format("%s: %s", errorType, description);
        }
    }

    /**
     * Validation result
     */
    public record ValidationResult(
        boolean isValid,
        List<CompilationError> errors
    ) {
        @Override
        public String toString() {
            if (isValid) {
                return "Validation PASSED";
            }
            return "Validation FAILED: " + errors.size() + " errors";
        }
    }
}
