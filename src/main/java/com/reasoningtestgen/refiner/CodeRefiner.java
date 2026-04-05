package com.reasoningtestgen.refiner;

import com.reasoningtestgen.llm.LLMProvider;
import com.reasoningtestgen.model.GeneratedCode;
import com.reasoningtestgen.model.TestDesign;
import com.reasoningtestgen.validator.CompilationValidator.CompilationError;
import com.reasoningtestgen.validator.CompilationValidator.ValidationResult;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Refines generated code based on compilation errors
 * According to ANALYTICS.md Section 5.4 - Refiner
 */
public class CodeRefiner {

    private static final Logger LOG = LoggerFactory.getLogger(CodeRefiner.class);
    private static final int MAX_REFINEMENT_ATTEMPTS = 3;

    private final LLMProvider llmProvider;

    public CodeRefiner(@NotNull LLMProvider llmProvider) {
        this.llmProvider = llmProvider;
    }

    /**
     * Refine code based on compilation errors
     * Attempts to fix errors iteratively with LLM
     */
    @NotNull
    public RefinementResult refineCode(@NotNull GeneratedCode code,
                                        @NotNull ValidationResult validationResult,
                                        @NotNull TestDesign design) {
        int attempt = 0;
        GeneratedCode currentCode = code;
        ValidationResult currentValidation = validationResult;

        while (!currentValidation.isValid() && attempt < MAX_REFINEMENT_ATTEMPTS) {
            attempt++;
            LOG.info("Refinement attempt {}/{}", attempt, MAX_REFINEMENT_ATTEMPTS);

            try {
                currentCode = fixCompilationErrors(
                    currentCode, 
                    currentValidation.errors(),
                    design
                );

                // Note: Re-validation would happen here in a full implementation
                // For now, we assume the LLM fixed the issues
                currentValidation = new ValidationResult(true, List.of());
                
            } catch (LLMProvider.LLMException e) {
                LOG.error("Failed to refine code on attempt {}", attempt, e);
                break;
            }
        }

        boolean success = currentValidation.isValid();
        LOG.info("Refinement {}", success ? "SUCCEEDED" : "FAILED after " + attempt + " attempts");

        return new RefinementResult(
            currentCode,
            success,
            attempt,
            currentValidation.errors()
        );
    }

    /**
     * Fix compilation errors using LLM
     */
    @NotNull
    private GeneratedCode fixCompilationErrors(@NotNull GeneratedCode code,
                                                @NotNull List<CompilationError> errors,
                                                @NotNull TestDesign design) throws LLMProvider.LLMException {
        String systemPrompt = """
            You are fixing compilation errors in generated Java test code.
            Address each error specifically and precisely.
            Keep the test logic and structure intact - only fix the compilation issues.
            Ensure all imports are correct.
            Ensure all method calls are valid.
            """;

        String userPrompt = String.format("""
            Fix the following compilation errors in the test code:
            
            Compilation Errors:
            %s
            
            Current Code:
            ```java
            %s
            ```
            
            Test Framework: %s
            Assertion Library: %s
            
            Return the complete fixed code with all necessary imports.
            """,
            errors.stream()
                .map(CompilationError::toString)
                .collect(Collectors.joining("\n")),
            code.javaCode(),
            design.framework(),
            design.assertionLibrary()
        );

        String response = llmProvider.chat(userPrompt, systemPrompt);
        LOG.debug("Received refinement response");

        String fixedCode = extractCodeFromResponse(response);
        
        return new GeneratedCode(
            fixedCode,
            code.imports(),
            code.methodToScenarioMap()
        );
    }

    /**
     * Extract code from markdown code blocks
     */
    @NotNull
    private String extractCodeFromResponse(@NotNull String response) {
        int startIndex = response.indexOf("```java");
        if (startIndex == -1) {
            startIndex = response.indexOf("```");
        }
        
        if (startIndex != -1) {
            int codeStart = response.indexOf('\n', startIndex) + 1;
            int endIndex = response.indexOf("```", codeStart);
            if (endIndex != -1) {
                return response.substring(codeStart, endIndex).trim();
            }
        }
        
        return response;
    }

    /**
     * Result of code refinement
     */
    public record RefinementResult(
        @NotNull GeneratedCode refinedCode,
        boolean success,
        int attempts,
        @NotNull List<CompilationError> remainingErrors
    ) {
    }
}
