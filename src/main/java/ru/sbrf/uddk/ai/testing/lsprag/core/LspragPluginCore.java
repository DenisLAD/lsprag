package ru.sbrf.uddk.ai.testing.lsprag.core;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;
import ru.sbrf.uddk.ai.testing.lsprag.LspragSettingsState;
import ru.sbrf.uddk.ai.testing.lsprag.context.EnhancedContextRetriever;
import ru.sbrf.uddk.ai.testing.lsprag.context.MethodContext;
import ru.sbrf.uddk.ai.testing.lsprag.exceptions.GenerationException;
import ru.sbrf.uddk.ai.testing.lsprag.extraction.DTOContextExtractor;
import ru.sbrf.uddk.ai.testing.lsprag.extraction.KeyTokenExtractor;
import ru.sbrf.uddk.ai.testing.lsprag.generator.PromptBuilder;
import ru.sbrf.uddk.ai.testing.lsprag.generator.TestCodeGenerator;
import ru.sbrf.uddk.ai.testing.lsprag.llm.LLMAdapterFactory;
import ru.sbrf.uddk.ai.testing.lsprag.llm.LLMGateway;
import ru.sbrf.uddk.ai.testing.lsprag.model.GeneratedTestData;
import ru.sbrf.uddk.ai.testing.lsprag.model.GenerationResult;
import ru.sbrf.uddk.ai.testing.lsprag.model.TestCase;
import ru.sbrf.uddk.ai.testing.lsprag.planning.TestCasePlanner;
import ru.sbrf.uddk.ai.testing.lsprag.ui.LLMResponseDialog;
import ru.sbrf.uddk.ai.testing.lsprag.utils.NotificationUtils;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class LspragPluginCore {

    private final Project project;
    private final LspragSettingsState settings;

    public LspragPluginCore(Project project, LspragSettingsState settings) {
        this.project = project;
        this.settings = settings;
    }

    @NotNull
    public GenerationResult generateTestForMethod(@NotNull PsiMethod method,
                                                  ProgressIndicator indicator)
            throws GenerationException {

        try {
            // 1. Extract key tokens
            indicator.setText2("Extracting key tokens...");
            KeyTokenExtractor tokenExtractor = new KeyTokenExtractor();
            var keyTokens = tokenExtractor.extract(method);

            // 2. Retrieve context
            indicator.setText2("Retrieving context...");
//            ContextRetriever contextRetriever = new ContextRetriever(settings.getContextDepth());

            EnhancedContextRetriever contextRetriever = new EnhancedContextRetriever(
                    project,
                    settings.getContextDepth(),
                    150  // Увеличили лимит узлов для анализа реализаций
            );

            indicator.setText2("Analyzing DTOs...");
            DTOContextExtractor dtoExtractor = new DTOContextExtractor();
            DTOContextExtractor.DTOExtractionResult dtoResult = dtoExtractor.extractDTOs(method);

            MethodContext baseContext = contextRetriever.retrieveContext(method, keyTokens);

            MethodContext context = new MethodContext(
                    baseContext.getTargetMethod(),
                    baseContext.getKeyTokens(),
                    baseContext.getCalledMethods(),
                    baseContext.getDepth(),
                    dtoResult.getRequestDTOs(),
                    dtoResult.getResponseDTOs()
            );

            // 3. Plan test cases
            indicator.setText2("Planning test cases...");
            TestCasePlanner planner = new TestCasePlanner();
            List<TestCase> testCases = planner.plan(context);

            // 4. Generate code via LLM
            indicator.setText2("Generating test code...");
            LLMGateway llmGateway = LLMAdapterFactory.create(settings);
            TestCodeGenerator generator = new TestCodeGenerator(llmGateway, settings);


//            // 5. Self-correction
//            indicator.setText2("Validating and fixing code...");
//            DiagnosticFixer fixer = new DiagnosticFixer(
//                    project, llmGateway, settings.getMaxRepairAttempts());
//            String fixedCode = fixer.fixErrors(rawCode);
//
//            // 6. Write to file
//            indicator.setText2("Writing test file...");
//            TestFileWriter writer = new TestFileWriter(project);
//            String outputPath = writer.writeTestFile(fixedCode, context);

            return showPreviewDialog(context, generator, testCases);

        } catch (LLMGateway.LLMException e) {
            throw new GenerationException("Generation failed: " + e.getMessage(), e);
        }
    }

    @NotNull
    private GenerationResult showPreviewDialog(@NotNull MethodContext context,
                                               @NotNull TestCodeGenerator generator,
                                               @NotNull List<TestCase> testCases) {

        AtomicReference<GenerationResult> resultRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);


        String prompt = new PromptBuilder().buildPrompt(context, testCases, settings);
        GeneratedTestData testData = generator.generateTestData(project, context, testCases);

        // Создаём и показываем диалог в EDT
        com.intellij.openapi.application.ApplicationManager.getApplication()
                .invokeAndWait(() -> {
                    LLMResponseDialog dialog = new LLMResponseDialog(
                            project,
                            prompt,
                            testData.getRawResponse(),
                            testData.getJavaCode(),
                            testCases,
                            (finalCode) -> {

                            }
                    );

                    dialog.setRegenerateAction(() -> ProgressManager.getInstance().run(new Task.Backgroundable(project, "Regenerating...", true) {
                        @Override
                        public void run(@NotNull ProgressIndicator indicator) {
                            try {
                                GeneratedTestData newData = generator.generateTestData(project, context, testCases);
                                ApplicationManager.getApplication().invokeLater(() ->
                                        dialog.updateContent(prompt, newData.getRawResponse(), newData.getJavaCode())
                                );
                            } catch (Exception e) {
                                ApplicationManager.getApplication().invokeLater(() ->
                                        NotificationUtils.showError(project, "Regeneration failed: " + e.getMessage())
                                );
                                dialog.close(DialogWrapper.CANCEL_EXIT_CODE);
                            }
                        }
                    }));
                    // Показываем диалог модально
                    boolean accepted = dialog.showAndGet();

                    if (!accepted && resultRef.get() == null) {
                        resultRef.set(GenerationResult.failure("User cancelled"));
                        latch.countDown();
                    }
                });

        // Ждём ответа пользователя (с таймаутом)
        try {
            if (!latch.await(5, TimeUnit.MINUTES)) {
                return GenerationResult.failure("Dialog timeout");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return GenerationResult.failure("Dialog interrupted");
        }

        GenerationResult result = resultRef.get();
        return result != null ? result : GenerationResult.failure("No response");
    }
}
