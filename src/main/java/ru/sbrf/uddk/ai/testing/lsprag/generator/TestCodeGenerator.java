package ru.sbrf.uddk.ai.testing.lsprag.generator;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.sbrf.uddk.ai.testing.lsprag.LspragSettingsState;
import ru.sbrf.uddk.ai.testing.lsprag.context.MethodContext;
import ru.sbrf.uddk.ai.testing.lsprag.llm.LLMGateway;
import ru.sbrf.uddk.ai.testing.lsprag.model.GeneratedTestData;
import ru.sbrf.uddk.ai.testing.lsprag.model.TestCase;
import ru.sbrf.uddk.ai.testing.lsprag.repair.DiagnosticFixer;
import ru.sbrf.uddk.ai.testing.lsprag.utils.LLMResponseParser;

import java.io.IOException;
import java.util.List;

public class TestCodeGenerator implements ITestCodeGenerator {

    private final LLMGateway llmGateway;
    private final LspragSettingsState settings;
    private final PromptBuilder promptBuilder;

    public TestCodeGenerator(@NotNull LLMGateway llmGateway,
                             @NotNull LspragSettingsState settings) {
        this.llmGateway = llmGateway;
        this.settings = settings;
        this.promptBuilder = new PromptBuilder();
    }

    @Override
    public GeneratedTestData generateTestData(Project project, @NotNull MethodContext context,
                                              @Nullable List<TestCase> testCases)
            throws LLMGateway.LLMException {
        String prompt = promptBuilder.buildPrompt(context, testCases, settings);
        String rawResponse = null;
        try {
            rawResponse = llmGateway.generate(prompt);
        } catch (IOException e) {
            throw new LLMGateway.LLMException("Ошибка генерации", e);
        }

        return new GeneratedTestData(rawResponse, generateTestClass(project, context, rawResponse));
    }

    @Override
    @NotNull
    public String generateTestClass(@NotNull Project project, @NotNull MethodContext context,
                                    @NotNull String generatedTestCases)
            throws LLMGateway.LLMException {

        // 1. Формируем промпт
        String prompt = promptBuilder.buildCodePrompt(context, generatedTestCases, settings);

        // 2. Запрашиваем генерацию у LLM
        String fixedCode = null;
        try {
            String rawResponse = llmGateway.generate(prompt);
            DiagnosticFixer fixer = new DiagnosticFixer(project, llmGateway, settings.getMaxRepairAttempts());
            fixedCode = fixer.fixErrors(rawResponse);
        } catch (IOException e) {
            throw new LLMGateway.LLMException("Ошибка генерации", e);
        }

        return LLMResponseParser.extractJavaCode(fixedCode);
    }

    @Override
    @NotNull
    public String fixCode(@NotNull String brokenCode,
                          @NotNull List<String> errors)
            throws IOException, LLMGateway.LLMException {

        String fixPrompt = promptBuilder.buildFixPrompt(brokenCode, errors);
        String rawResponse = llmGateway.generate(fixPrompt);

        return LLMResponseParser.extractJavaCode(rawResponse);
    }
}