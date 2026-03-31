package ru.sbrf.uddk.ai.testing.lsprag.core;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiMethod;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.sbrf.uddk.ai.testing.lsprag.LspragSettingsState;
import ru.sbrf.uddk.ai.testing.lsprag.context.EnhancedContextRetriever;
import ru.sbrf.uddk.ai.testing.lsprag.context.MethodContext;
import ru.sbrf.uddk.ai.testing.lsprag.exceptions.GenerationException;
import ru.sbrf.uddk.ai.testing.lsprag.extraction.DTOContextExtractor;
import ru.sbrf.uddk.ai.testing.lsprag.extraction.KeyTokenExtractor;
import ru.sbrf.uddk.ai.testing.lsprag.generator.ITestCodeGenerator;
import ru.sbrf.uddk.ai.testing.lsprag.generator.TestCodeGenerator;
import ru.sbrf.uddk.ai.testing.lsprag.generator.UnitTestCodeGenerator;
import ru.sbrf.uddk.ai.testing.lsprag.llm.LLMAdapterFactory;
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
                                                  ProgressIndicator indicator, boolean isIntegrationTest)
            throws GenerationException {

        try {
            // 1. Подготовка контекста (без вызова LLM)
            MethodContext context = prepareMethodContext(method, indicator);
            // 2. Планирование тест-кейсов
            indicator.setText2("Planning test cases...");
            TestCasePlanner planner = new TestCasePlanner();
            List<TestCase> testCases = planner.plan(context);

            // 3. Построение промпта
            ITestCodeGenerator generator = isIntegrationTest
                    ? new TestCodeGenerator(LLMAdapterFactory.create(settings), settings)
                    : new UnitTestCodeGenerator(LLMAdapterFactory.create(settings), settings);

            String prompt = generator.buildPrompt(context, testCases, settings);

            // 4. Показ диалога с возможностью редактирования промпта и последующей генерации
            return showPreviewDialog(context, prompt, testCases, generator);

        } catch (Exception e) {
            throw new GenerationException("Preparation failed: " + e.getMessage(), e);
        }
    }

    @NotNull
    private MethodContext prepareMethodContext(@NotNull PsiMethod method, ProgressIndicator indicator) {
        indicator.setText2("Extracting key tokens...");
        KeyTokenExtractor tokenExtractor = new KeyTokenExtractor();
        var keyTokens = tokenExtractor.extract(method);

        indicator.setText2("Retrieving context...");
        EnhancedContextRetriever contextRetriever = new EnhancedContextRetriever(
                project,
                settings.getContextDepth(),
                150
        );

        indicator.setText2("Analyzing DTOs...");
        DTOContextExtractor dtoExtractor = new DTOContextExtractor();
        DTOContextExtractor.DTOExtractionResult dtoResult = dtoExtractor.extractDTOs(method);

        MethodContext baseContext = contextRetriever.retrieveContext(method, keyTokens);

        return new MethodContext(
                baseContext.getTargetMethod(),
                baseContext.getKeyTokens(),
                baseContext.getCalledMethods(),
                baseContext.getDepth(),
                dtoResult.getRequestDTOs(),
                dtoResult.getResponseDTOs()
        );
    }

    @NotNull
    private GenerationResult showPreviewDialog(@NotNull MethodContext context,
                                               @NotNull String initialPrompt,
                                               @Nullable List<TestCase> testCases,
                                               ITestCodeGenerator generator) {

        AtomicReference<GenerationResult> resultRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        // Показываем диалог
        ApplicationManager.getApplication().invokeAndWait(() -> {
            LLMResponseDialog dialog = new LLMResponseDialog(
                    project,
                    initialPrompt,
                    testCases,
                    null,
                    (finalCode) -> {
                        // Apply action: пользователь нажал "Apply", код сохранён или записан
                        resultRef.set(GenerationResult.success(finalCode, ""));
                        latch.countDown();
                    }
            );

            Consumer<String> generationCallback = (finalPrompt) -> {
                // Запускаем генерацию в фоне
                ProgressManager.getInstance().run(new Task.Backgroundable(project, "Generating test code...", true) {
                    @Override
                    public void run(@NotNull ProgressIndicator indicator) {
                        try {
                            GeneratedTestData testData = generator.generateTestData(project, context, testCases, finalPrompt);
                            // Обновляем диалог с результатом
                            ApplicationManager.getApplication().invokeLater(() -> {
                                dialog.updateGeneratedCode(testData.getJavaCode());
                                dialog.updateTestCases(testData.getRawResponse());
                            });
                            resultRef.set(GenerationResult.success(testData.getJavaCode(), ""));
                            latch.countDown();
                        } catch (Exception e) {
                            dialog.updateGeneratedCode(e.getMessage());
                            dialog.updateTestCases(e.getMessage());
                            ApplicationManager.getApplication().invokeLater(() ->
                                    NotificationUtils.showError(project, "Generation failed: " + e.getMessage())
                            );
                            resultRef.set(GenerationResult.failure(e.getMessage()));
                            latch.countDown();
                        }
                    }
                });
            };

            dialog.setGenerateCallback(generationCallback);

            dialog.show();
            // Если диалог закрыт без генерации, то не будет latch.countDown()
            // Нужно обработать закрытие диалога. Проще всего добавить в диалог обработчик закрытия.
        });

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

    public GenerationResult generateUnitTestForMethod(PsiMethod method, ProgressIndicator indicator) throws GenerationException {
        return generateTestForMethod(method, indicator, false);
    }
}