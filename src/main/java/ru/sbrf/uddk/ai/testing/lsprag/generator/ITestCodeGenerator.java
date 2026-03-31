package ru.sbrf.uddk.ai.testing.lsprag.generator;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.sbrf.uddk.ai.testing.lsprag.LspragSettingsState;
import ru.sbrf.uddk.ai.testing.lsprag.context.MethodContext;
import ru.sbrf.uddk.ai.testing.lsprag.llm.LLMGateway;
import ru.sbrf.uddk.ai.testing.lsprag.model.GeneratedTestData;
import ru.sbrf.uddk.ai.testing.lsprag.model.TestCase;

import java.io.IOException;
import java.util.List;

public interface ITestCodeGenerator {
    GeneratedTestData generateTestData(Project project, @NotNull MethodContext context,
                                       @Nullable List<TestCase> testCases)
            throws LLMGateway.LLMException;

    @NotNull
    String generateTestClass(@NotNull Project project, @NotNull MethodContext context,
                             @NotNull String generatedTestCases)
            throws LLMGateway.LLMException;

    GeneratedTestData generateTestData(@NotNull Project project,
                                       @NotNull MethodContext context,
                                       @Nullable List<TestCase> testCases,
                                       @NotNull String prompt);

    @NotNull
    String fixCode(@NotNull String brokenCode,
                   @NotNull List<String> errors)
            throws IOException, LLMGateway.LLMException;

    String buildPrompt(@NotNull MethodContext context,
                       @Nullable List<TestCase> testCases,
                       @NotNull LspragSettingsState settings);

    String buildCodePrompt(@NotNull MethodContext context,
                           @NotNull String testCases,
                           @NotNull LspragSettingsState settings);
}
