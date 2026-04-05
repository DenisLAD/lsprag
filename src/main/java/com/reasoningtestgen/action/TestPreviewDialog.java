package com.reasoningtestgen.action;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.*;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.LocalTimeCounter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * Preview dialog for generated tests
 * According to ANALYTICS.md Section 9 - Preview Dialog
 */
public class TestPreviewDialog extends DialogWrapper {

    private final Project project;
    private final String testClassName;
    private final String testCode;
    private Editor editor;

    public TestPreviewDialog(@NotNull Project project,
                              @NotNull String testClassName,
                              @NotNull String testCode) {
        super(project, true);
        this.project = project;
        this.testClassName = testClassName;
        this.testCode = testCode;
        
        setTitle("Preview Generated Tests");
        init();
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setPreferredSize(new Dimension(800, 600));

        // Create editor for test code
        editor = createEditor();
        
        // Add scroll pane
        JBScrollPane scrollPane = new JBScrollPane(editor.getComponent());
        panel.add(scrollPane, BorderLayout.CENTER);

        // Add info panel at top
        JPanel infoPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        infoPanel.add(new JLabel("Class: "));
        infoPanel.add(new JLabel("<html><b>" + testClassName + ".java</b></html>"));
        panel.add(infoPanel, BorderLayout.NORTH);

        return panel;
    }

    /**
     * Create editor with Java syntax highlighting
     */
    @NotNull
    private Editor createEditor() {
        EditorFactory editorFactory = EditorFactory.getInstance();
        
        // Create document
        com.intellij.openapi.editor.Document document = editorFactory.createDocument(testCode);
        
        // Create editor
        EditorEx editorEx = (EditorEx) editorFactory.createEditor(document, project);
        editorEx.setViewer(true);
        editorEx.setHighlighter(
            EditorHighlighterFactory.getInstance().createEditorHighlighter(
                project,
                new LightVirtualFile(testClassName + ".java", StdFileTypes.JAVA, testCode)
            )
        );
        
        this.editor = editorEx;
        return editorEx;
    }

    @Override
    protected void doOKAction() {
        // Insert test file
        insertTestFile();
        super.doOKAction();
    }

    @Override
    protected Action[] createActions() {
        // Custom actions: Insert, Cancel
        Action insertAction = new AbstractAction("Insert") {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                doOKAction();
            }
        };
        
        Action cancelAction = new AbstractAction("Cancel") {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                doCancelAction();
            }
        };
        
        return new Action[]{insertAction, cancelAction};
    }

    /**
     * Insert test file into project
     */
    private void insertTestFile() {
        WriteCommandAction.runWriteCommandAction(project, () -> {
            try {
                // Create PSI file within write action
                PsiFile psiFile = PsiFileFactory.getInstance(project)
                    .createFileFromText(
                        testClassName + ".java",
                        StdFileTypes.JAVA,
                        testCode
                    );

                // Format code
                com.intellij.psi.codeStyle.CodeStyleManager.getInstance(project)
                    .reformat(psiFile);

                // Show success message
                com.intellij.openapi.ui.Messages.showMessageDialog(
                    project,
                    "Test class " + testClassName + " generated successfully!",
                    "Test Generated",
                    com.intellij.openapi.ui.Messages.getInformationIcon()
                );

            } catch (Exception e) {
                com.intellij.openapi.ui.Messages.showErrorDialog(
                    project,
                    "Failed to insert test file: " + e.getMessage(),
                    "Error"
                );
            }
        });
    }

    @Override
    public void dispose() {
        // Release editor resources
        if (editor != null) {
            EditorFactory.getInstance().releaseEditor(editor);
        }
        super.dispose();
    }
}
