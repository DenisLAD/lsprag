package ru.sbrf.uddk.ai.testing.lsprag;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import ru.sbrf.uddk.ai.testing.lsprag.core.LspragPluginCore;
import ru.sbrf.uddk.ai.testing.lsprag.exceptions.GenerationException;
import ru.sbrf.uddk.ai.testing.lsprag.model.GenerationResult;
import ru.sbrf.uddk.ai.testing.lsprag.utils.NotificationUtils;

public class GenerateUnitTestsAction extends AnAction {

    @Override
    public void update(@NotNull AnActionEvent e) {
        // Показываем действие только для Java-файлов
        var file = e.getData(CommonDataKeys.PSI_FILE);
        e.getPresentation().setEnabled(file instanceof PsiJavaFile);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        var psiFile = e.getData(CommonDataKeys.PSI_FILE);
        if (!(psiFile instanceof PsiJavaFile javaFile)) return;

        // Находим метод под курсором
        var editor = e.getData(CommonDataKeys.EDITOR);
        if (editor == null) return;

        PsiMethod targetMethod = PsiTreeUtil.findElementOfClassAtOffset(
                javaFile, editor.getCaretModel().getOffset(), PsiMethod.class, false);

        // Запускаем генерацию в фоне
        runGenerationTask(project, targetMethod);
    }

    private void runGenerationTask(Project project, PsiMethod method) {
        ProgressManager.getInstance().run(new Task.Backgroundable(project,
                "Generating API Tests", true) {

            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                try {
                    indicator.setText("Analyzing code...");

                    LspragSettingsState settings = LspragSettingsState.getInstance();

                    LspragPluginCore core = new LspragPluginCore(project, settings);
                    GenerationResult result = core.generateUnitTestForMethod(method, indicator);

                    if (result.isSuccess()) {
                        NotificationUtils.showSuccess(project,
                                "Tests generated: " + result.getOutputPath());
                    } else {
                        NotificationUtils.showError(project,
                                "Generation failed: " + result.getErrorMessage());
                    }

                } catch (
                        GenerationException ex) {
                    NotificationUtils.showError(project,
                            "Error: " + ex.getMessage());
                }
            }
        });
    }
}