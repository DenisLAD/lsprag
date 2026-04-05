package com.reasoningtestgen.validator;

import com.intellij.compiler.CompilerConfiguration;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * V2: Real compilation validation using IDEA's CompilerManager
 * Actually compiles the test file and collects real compiler errors
 */
public class RealCompilationValidator {

    private static final Logger LOG = LoggerFactory.getLogger(RealCompilationValidator.class);
    private static final long COMPILATION_TIMEOUT_SECONDS = 30;

    private final Project project;

    public RealCompilationValidator(@NotNull Project project) {
        this.project = project;
    }

    /**
     * Compile test file and collect real compiler errors
     */
    @NotNull
    public ValidationResult validateCompilation(@NotNull VirtualFile testFile) {
        List<CompilationError> errors = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch latch = new CountDownLatch(1);

        CompileStatusNotification callback = (aborted, errorsCount, warningsCount, compileContext) -> {
            if (aborted) {
                errors.add(new CompilationError(-1, "Compilation", "Compilation was aborted"));
                latch.countDown();
                return;
            }

            // Get all error messages from compile context
            CompilerMessage[] errorMessages = compileContext.getMessages(CompilerMessageCategory.ERROR);
            for (CompilerMessage message : errorMessages) {
                String description = message.getMessage();
                int line = -1;
                
                // Try to extract line number from message
                if (description != null && description.contains(":")) {
                    String[] parts = description.split(":");
                    if (parts.length > 1) {
                        try {
                            line = Integer.parseInt(parts[1].trim());
                        } catch (NumberFormatException e) {
                            // Ignore, keep line = -1
                        }
                    }
                }
                
                errors.add(new CompilationError(
                    line,
                    CompilerMessageCategory.ERROR.toString(),
                    description != null ? description : "Unknown error"
                ));
            }

            LOG.info("Compilation completed: {} errors, {} warnings", errorsCount, warningsCount);
            latch.countDown();
        };

        try {
            LOG.info("Starting real compilation validation for: {}", testFile.getPath());

            // Compile the test file
            CompilerManager.getInstance(project).compile(
                new VirtualFile[]{testFile},
                callback
            );

            // Wait for compilation to complete
            boolean completed = latch.await(COMPILATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            
            if (!completed) {
                LOG.warn("Compilation timed out after {} seconds", COMPILATION_TIMEOUT_SECONDS);
                errors.add(new CompilationError(-1, "Compilation", "Compilation timed out"));
                return new ValidationResult(false, errors);
            }

            boolean isValid = errors.isEmpty();
            LOG.info("Compilation validation {}: {} errors found", 
                    isValid ? "PASSED" : "FAILED", errors.size());

            return new ValidationResult(isValid, errors);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.error("Compilation validation was interrupted", e);
            errors.add(new CompilationError(-1, "Compilation", "Validation was interrupted"));
            return new ValidationResult(false, errors);
        } catch (Exception e) {
            LOG.error("Compilation validation failed", e);
            errors.add(new CompilationError(-1, "Compilation", "Validation failed: " + e.getMessage()));
            return new ValidationResult(false, errors);
        }
    }

    /**
     * Compile and validate a PsiFile
     * Converts PsiFile to VirtualFile first
     */
    @NotNull
    public ValidationResult validateCompilation(@NotNull PsiFile psiFile) {
        VirtualFile virtualFile = psiFile.getVirtualFile();
        if (virtualFile == null) {
            List<CompilationError> errors = new ArrayList<>();
            errors.add(new CompilationError(-1, "PSI", "PsiFile has no VirtualFile"));
            return new ValidationResult(false, errors);
        }
        
        return validateCompilation(virtualFile);
    }

    /**
     * Compilation error representation
     */
    public record CompilationError(
        int line,
        String category,
        String description
    ) {
        @Override
        public String toString() {
            return line > 0 ? 
                String.format("Line %d [%s]: %s", line, category, description) :
                String.format("[%s]: %s", category, description);
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
            return "Validation FAILED: " + errors.size() + " errors\n" +
                   errors.stream()
                       .map(Object::toString)
                       .reduce("", (a, b) -> a + "\n  " + b);
        }
    }
}
