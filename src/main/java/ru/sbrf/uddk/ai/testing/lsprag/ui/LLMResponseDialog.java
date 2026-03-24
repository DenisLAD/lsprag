package ru.sbrf.uddk.ai.testing.lsprag.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.Consumer;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.sbrf.uddk.ai.testing.lsprag.model.GenerationResult;
import ru.sbrf.uddk.ai.testing.lsprag.model.TestCase;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.util.List;

public class LLMResponseDialog extends DialogWrapper {

    private final Project project;
    private final String promptText;
    private final String scenarioText;
    private final String javaCodeText;
    private final List<TestCase> testCases;
    private Runnable regenerateAction;
    private final Consumer<String> applyAction;

    private JTabbedPane tabbedPane;
    private JTextArea promptArea;
    private JTextArea scenarioArea;
    private CodePreviewPanel codePanel;
    private JProgressBar progressBar;
    private JButton copyButton;
    private JButton regenerateButton;
    private JButton applyButton;

    public LLMResponseDialog(@Nullable Project project,
                             @NotNull String promptText,
                             @NotNull String scenarioText,
                             @NotNull String javaCodeText,
                             @NotNull List<TestCase> testCases,
                             @NotNull Consumer<String> applyAction) {
        super(project);
        this.project = project;
        this.promptText = promptText;
        this.scenarioText = scenarioText;
        this.javaCodeText = javaCodeText;
        this.testCases = testCases;
        this.applyAction = applyAction;

        setTitle("🤖 LLM Response Preview");
        setOKButtonText("Apply");
        setCancelButtonText("Cancel");
        init();
    }

    public void setRegenerateAction(Runnable action) {
        this.regenerateAction = action;
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(JBUI.Borders.empty(10));
        mainPanel.setPreferredSize(new Dimension(900, 650));

        progressBar = new JProgressBar();
        progressBar.setIndeterminate(false);
        progressBar.setVisible(false);
        mainPanel.add(progressBar, BorderLayout.NORTH);

        tabbedPane = new JTabbedPane();

        promptArea = new JTextArea(promptText);
        promptArea.setEditable(false);
        promptArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        tabbedPane.addTab("📝 Prompt", new JBScrollPane(promptArea));

        scenarioArea = new JTextArea(scenarioText);
        scenarioArea.setEditable(false);
        scenarioArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        tabbedPane.addTab("📋 Test Scenario", new JBScrollPane(scenarioArea));

        codePanel = new CodePreviewPanel(javaCodeText);
        tabbedPane.addTab("💻 Java Code", codePanel);

        mainPanel.add(tabbedPane, BorderLayout.CENTER);
        return mainPanel;
    }

    @Override
    protected JComponent createSouthPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 5));

        copyButton = new JButton("📋 Copy current tab");
        copyButton.addActionListener(e -> copyCurrentTabContent());

        regenerateButton = new JButton("🔄 Regenerate");
        regenerateButton.addActionListener(e -> onRegenerate());

        applyButton = new JButton("✅ Apply (create test class)");
        applyButton.addActionListener(e -> onApply());

        panel.add(copyButton);
        panel.add(regenerateButton);
        panel.add(applyButton);
        return panel;
    }

    private void copyCurrentTabContent() {
        int selectedIndex = tabbedPane.getSelectedIndex();
        String textToCopy = switch (selectedIndex) {
            case 0 -> promptArea.getText();
            case 1 -> scenarioArea.getText();
            case 2 -> codePanel.getCode();
            default -> "";
        };
        if (!textToCopy.isEmpty()) {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(textToCopy), null);
        }
    }

    private void onRegenerate() {
        int confirm = JOptionPane.showConfirmDialog(
                getContentPanel(),
                "Regenerate will request new code from LLM. Current changes will be lost. Continue?",
                "Confirm Regeneration",
                JOptionPane.YES_NO_OPTION
        );
        if (confirm == JOptionPane.YES_OPTION) {
            setButtonsEnabled(false);
            progressBar.setIndeterminate(true);
            progressBar.setVisible(true);
            regenerateAction.run();
        }
    }

    private void onApply() {
        String finalCode = codePanel.getCode();
        applyAction.accept(finalCode);
        close(OK_EXIT_CODE);
    }

    private void setButtonsEnabled(boolean enabled) {
        copyButton.setEnabled(enabled);
        regenerateButton.setEnabled(enabled);
        applyButton.setEnabled(enabled);
    }

    public void updateContent(@NotNull String newPrompt,
                              @NotNull String newScenario,
                              @NotNull String newJavaCode) {
        SwingUtilities.invokeLater(() -> {
            promptArea.setText(newPrompt);
            scenarioArea.setText(newScenario);
            codePanel.updateCode(newJavaCode);
            progressBar.setVisible(false);
            setButtonsEnabled(true);
        });
    }
}