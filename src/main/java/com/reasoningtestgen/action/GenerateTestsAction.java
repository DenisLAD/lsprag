package com.reasoningtestgen.action;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.reasoningtestgen.extractor.PSIExtractor;
import com.reasoningtestgen.builder.ContextBuilder;
import com.reasoningtestgen.service.TestGenerationController;
import com.reasoningtestgen.service.TestGenerationController.GenerationResult;
import com.reasoningtestgen.model.MethodContext;
import com.reasoningtestgen.service.CoverageAnalysisService;
import com.reasoningtestgen.settings.PluginSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main action for generating tests
 * Accessible from editor context menu and project view
 * According to ANALYTICS.md Section 9
 * 
 * GRASP Controller: This action delegates to TestGenerationController
 */
public class GenerateTestsAction extends AnAction {

    private static final Logger LOG = LoggerFactory.getLogger(GenerateTestsAction.class);

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            return;
        }

        // Get selected PSI element
        PsiElement element = getTargetPsiElement(e);
        if (element == null) {
            Messages.showWarningDialog(
                project,
                "Please select a Java method to generate tests for.",
                "Generate Tests"
            );
            return;
        }

        // Find containing method
        PsiMethod method = findContainingMethod(element);
        if (method == null) {
            Messages.showWarningDialog(
                project,
                "Please select a Java method to generate tests for.",
                "Generate Tests"
            );
            return;
        }

        // Run generation task
        runGenerationTask(project, method);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        // Enable action only for Java files
        Project project = e.getProject();
        VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
        boolean visible = project != null && file != null && 
                         file.getFileType() == StdFileTypes.JAVA;
        e.getPresentation().setEnabledAndVisible(visible);
    }

    @NotNull
    @Override
    public ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    /**
     * Get target PSI element from context
     */
    @Nullable
    private PsiElement getTargetPsiElement(@NotNull AnActionEvent e) {
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
        
        if (editor != null && psiFile != null) {
            int offset = editor.getCaretModel().getOffset();
            return psiFile.findElementAt(offset);
        }
        
        return e.getData(CommonDataKeys.PSI_ELEMENT);
    }

    /**
     * Find containing method from PSI element
     */
    @Nullable
    private PsiMethod findContainingMethod(@NotNull PsiElement element) {
        // Check if element itself is a method
        if (element instanceof PsiMethod) {
            return (PsiMethod) element;
        }
        
        // Walk up the tree to find containing method
        return PsiTreeUtil.getParentOfType(element, PsiMethod.class);
    }

    /**
     * Run the generation task as a background task
     * Two-phase approach:
     * 1. Collect context (non-blocking)
     * 2. Show preview dialog for review and editing
     */
    private void runGenerationTask(@NotNull Project project, @NotNull PsiMethod method) {
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Collecting Method Context") {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                try {
                    indicator.setIndeterminate(false);

                    // Step 1: Extract context using Controller (ReadAction)
                    indicator.setText("Extracting method context...");
                    indicator.setFraction(0.3);

                    TestGenerationController controller = new TestGenerationController(
                        project,
                        new PSIExtractor(),
                        new ContextBuilder()
                    );
                    
                    GenerationResult result = controller.generateTests(method);

                    LOG.info("Extracted context for {}.{}", 
                        result.context().className(), result.context().methodName());

                    // Step 1.5: Start background coverage analysis
                    CoverageAnalysisService coverageService = CoverageAnalysisService.getInstance(project);
                    MethodContext[] contextWithCoverage = new MethodContext[1];
                    contextWithCoverage[0] = result.context();

                    coverageService.analyzeInBackground(result.context(), method, new CoverageAnalysisService.CoverageCallback() {
                        @Override
                        public void onComplete(com.reasoningtestgen.model.CoverageInfo coverageInfo) {
                            if (coverageInfo != null) {
                                contextWithCoverage[0] = result.context().withCoverageInfo(coverageInfo);
                                LOG.info("Coverage analysis completed: {}", coverageInfo);
                            }
                        }

                        @Override
                        public void onError(@NotNull Throwable error) {
                            LOG.error("Coverage analysis failed", error);
                        }
                    });

                    // Step 2: Use prompt from result
                    indicator.setText("Building prompts...");
                    indicator.setFraction(0.6);
                    
                    final String finalFullPrompt = result.promptBundle().systemPrompt() + "\n\n=== USER PROMPT ===\n\n" +
                                       result.promptBundle().userPrompt() +
                                       (!result.promptBundle().examples().isEmpty() ?
                                           "\n\n=== EXAMPLES ===\n" + String.join("\n", result.promptBundle().examples()) : "");

                    indicator.setText("Opening preview dialog...");
                    indicator.setFraction(1.0);

                    // Step 3: Show reasoning pipeline dialog (non-blocking)
                    ApplicationManager.getApplication().invokeLater(() -> {
                        ReasoningPipelineDialog dialog = new ReasoningPipelineDialog(
                            project,
                            result.context().className(),
                            result.context().methodName(),
                            contextWithCoverage[0],
                            finalFullPrompt
                        );
                        dialog.show();
                    });

                } catch (TestGenerationController.GenerationException e) {
                    LOG.error("Error during generation", e);
                    ApplicationManager.getApplication().invokeLater(() -> {
                        Messages.showErrorDialog(
                            project,
                            "Error during test generation: " + e.getMessage(),
                            "Generation Error"
                        );
                    });
                } catch (Exception e) {
                    LOG.error("Error during context collection", e);
                    ApplicationManager.getApplication().invokeLater(() -> {
                        Messages.showErrorDialog(
                            project,
                            "Error during context collection: " + e.getMessage(),
                            "Collection Error"
                        );
                    });
                }
            }
        });
    }
}
