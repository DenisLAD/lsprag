package com.reasoningtestgen.service;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiMethod;
import com.reasoningtestgen.builder.ContextBuilder;
import com.reasoningtestgen.extractor.PSIExtractor;
import com.reasoningtestgen.model.MethodContext;
import com.reasoningtestgen.model.PromptBundle;
import org.jetbrains.annotations.NotNull;

/**
 * Controller for test generation use case
 * Coordinates between PSI extraction and prompt building
 * 
 * According to GRASP Controller pattern - handles system operations
 */
public class TestGenerationController {

    private final PSIExtractor extractor;
    private final ContextBuilder contextBuilder;
    private final Project project;

    public TestGenerationController(@NotNull Project project,
                                     @NotNull PSIExtractor extractor,
                                     @NotNull ContextBuilder contextBuilder) {
        this.project = project;
        this.extractor = extractor;
        this.contextBuilder = contextBuilder;
    }

    /**
     * Generate test context for the given method
     * 
     * @param method Method to generate tests for
     * @return GenerationResult with context and prompt
     * @throws GenerationException if extraction fails
     */
    @NotNull
    public GenerationResult generateTests(@NotNull PsiMethod method) {
        try {
            // Extract method context (ReadAction will be applied inside extractor)
            MethodContext context = extractor.extract(method);
            
            // Build prompt bundle
            PromptBundle promptBundle = contextBuilder.buildPromptBundle(context);
            
            return new GenerationResult(context, promptBundle);
            
        } catch (Exception e) {
            throw new GenerationException(
                "Failed to generate tests for method: " + 
                method.getContainingClass().getName() + "." + method.getName(),
                e
            );
        }
    }

    /**
     * Generate test context with source code inclusion
     */
    @NotNull
    public GenerationResult generateTestsWithSource(@NotNull PsiMethod method,
                                                     boolean includeSourceCode,
                                                     int maxCodeLength,
                                                     int analysisDepth) {
        try {
            MethodContext context = extractor.extractWithSource(
                method,
                includeSourceCode,
                maxCodeLength,
                analysisDepth
            );
            
            PromptBundle promptBundle = contextBuilder.buildPromptBundle(context);
            
            return new GenerationResult(context, promptBundle);
            
        } catch (Exception e) {
            throw new GenerationException(
                "Failed to generate tests with source for method: " + 
                method.getContainingClass().getName() + "." + method.getName(),
                e
            );
        }
    }

    /**
     * Result of test generation
     */
    public record GenerationResult(
        @NotNull MethodContext context,
        @NotNull PromptBundle promptBundle
    ) {}

    /**
     * Exception during generation
     */
    public static class GenerationException extends RuntimeException {
        public GenerationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
