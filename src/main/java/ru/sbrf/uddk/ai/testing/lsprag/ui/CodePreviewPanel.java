package ru.sbrf.uddk.ai.testing.lsprag.ui;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public class CodePreviewPanel extends JPanel {

    private final EditorTextField editorTextField;
    private final JScrollPane scrollPane;
    private String currentCode;

    public CodePreviewPanel(@NotNull String initialCode) {
        super(new BorderLayout());
        this.currentCode = initialCode;

        editorTextField = createEditorTextField(initialCode);
        scrollPane = new JBScrollPane(editorTextField);
        scrollPane.setBorder(JBUI.Borders.empty()); // рамка будет на панели
        add(scrollPane, BorderLayout.CENTER);

        setBorder(JBUI.Borders.empty(5));
    }

    @NotNull
    private EditorTextField createEditorTextField(@NotNull String code) {
        Project project = ProjectUtil.guessCurrentProject(this);
        FileType fileType = FileTypeManager.getInstance().getFileTypeByExtension("java");

        EditorTextField editorField = new EditorTextField(code, project, fileType) {
            @Override
            protected @NotNull EditorEx createEditor() {
                final EditorEx editor = super.createEditor();

                editor.setViewer(true);
                editor.setOneLineMode(false);
                editor.getSettings().setLineNumbersShown(true);
                editor.getSettings().setFoldingOutlineShown(true);
                editor.getSettings().setIndentGuidesShown(true);
                editor.getSettings().setAdditionalLinesCount(0);
                editor.getSettings().setAdditionalColumnsCount(0);
                editor.getSettings().setLineMarkerAreaShown(false);
                editor.getSettings().setWhitespacesShown(false);

                editor.setColorsScheme(EditorColorsManager.getInstance().getGlobalScheme());

                return editor;
            }
        };

        editorField.setMinimumSize(new Dimension(200, 100));
        editorField.setOneLineMode(false);

        return editorField;
    }

    public void updateCode(@NotNull String newCode) {
        this.currentCode = newCode;
        editorTextField.setText(newCode);
        editorTextField.revalidate();
        editorTextField.repaint();
    }

    @NotNull
    public String getCode() {
        return currentCode != null ? currentCode : "";
    }

    public void highlightLine(int lineNumber) {
        Editor editor = editorTextField.getEditor();
        if (editor != null) {
            int lineCount = editor.getDocument().getLineCount();
            if (lineNumber >= 0 && lineNumber < lineCount) {
                int offset = editor.getDocument().getLineStartOffset(lineNumber);
                editor.getCaretModel().moveToOffset(offset);
                editor.getScrollingModel().scrollToCaret(ScrollType.CENTER);
            }
        }
    }

    public void setEditable(boolean editable) {
        // реализация при необходимости
    }
}