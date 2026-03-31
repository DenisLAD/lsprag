package ru.sbrf.uddk.ai.testing.lsprag.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.sbrf.uddk.ai.testing.lsprag.model.TestCase;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.util.List;
import java.util.function.Consumer;

public class LLMResponseDialog extends DialogWrapper {

    private final Project project;
    private final List<TestCase> testCases;
    private Consumer<String> generateCallback;
    private final Consumer<String> applyCallback;
    private final String initialPrompt; // сохранённый начальный промпт

    private JTabbedPane tabbedPane;
    private JTextArea promptArea;
    private JTextArea scenarioArea;
    private CodePreviewPanel codePanel;
    private JProgressBar progressBar;
    private JButton generateButton;
    private JButton copyButton;
    private JButton applyButton;
    private JButton regenerateButton;

    private String currentGeneratedCode = "";
    private String currentTestCases = "";

    public LLMResponseDialog(@Nullable Project project,
                             @NotNull String initialPrompt,
                             @Nullable List<TestCase> testCases,
                             @Nullable Consumer<String> generateCallback,
                             @NotNull Consumer<String> applyCallback) {
        super(project);
        this.project = project;
        this.initialPrompt = initialPrompt;
        this.testCases = testCases;
        this.generateCallback = generateCallback;
        this.applyCallback = applyCallback;

        setTitle("🤖 LLM Test Generator");
        setOKButtonText("Apply");
        setCancelButtonText("Cancel");
        init();
    }

    public void setGenerateCallback(Consumer<String> generateCallback) {
        this.generateCallback = generateCallback;
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(JBUI.Borders.empty(10));
        mainPanel.setPreferredSize(new Dimension(1000, 700));

        progressBar = new JProgressBar();
        progressBar.setIndeterminate(false);
        progressBar.setVisible(false);
        mainPanel.add(progressBar, BorderLayout.NORTH);

        tabbedPane = new JTabbedPane();

        // Вкладка с редактируемым промптом
        promptArea = new JTextArea();
        promptArea.setEditable(true);
        promptArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        promptArea.setText(initialPrompt); // установка текста после создания компонента
        tabbedPane.addTab("📝 Prompt (editable)", new JBScrollPane(promptArea));

        // Вкладка с тест-кейсами
        scenarioArea = new JTextArea();
        scenarioArea.setEditable(false);
        scenarioArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        if (testCases != null) {
            StringBuilder sb = new StringBuilder();
            for (TestCase tc : testCases) {
                sb.append(formatTestCase(tc)).append("\n\n");
            }
            scenarioArea.setText(sb.toString());
        }
        tabbedPane.addTab("📋 Test Cases", new JBScrollPane(scenarioArea));

        // Вкладка для сгенерированного кода
        codePanel = new CodePreviewPanel("");
        codePanel.setEditable(false);
        tabbedPane.addTab("💻 Java Code", codePanel);

        mainPanel.add(tabbedPane, BorderLayout.CENTER);
        return mainPanel;
    }

    @Override
    protected JComponent createSouthPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 5));

        copyButton = new JButton("📋 Copy current tab");
        copyButton.addActionListener(e -> copyCurrentTabContent());

        generateButton = new JButton("⚡ Generate");
        generateButton.addActionListener(e -> onGenerate());

        regenerateButton = new JButton("🔄 Regenerate");
        regenerateButton.setEnabled(false);
        regenerateButton.addActionListener(e -> onRegenerate());

        applyButton = new JButton("✅ Apply");
        applyButton.setEnabled(false);
        applyButton.addActionListener(e -> onApply());

        panel.add(copyButton);
        panel.add(generateButton);
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

    private void onGenerate() {
        String currentPrompt = promptArea.getText();
        if (currentPrompt.isBlank()) {
            JOptionPane.showMessageDialog(getContentPanel(), "Prompt is empty", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        setButtonsEnabled(false);
        progressBar.setIndeterminate(true);
        progressBar.setVisible(true);

        // Запускаем асинхронную генерацию через колбэк
        generateCallback.accept(currentPrompt);
    }

    private void onRegenerate() {
        onGenerate(); // использует текущий (возможно, отредактированный) промпт
    }

    private void onApply() {
        if (currentGeneratedCode != null && !currentGeneratedCode.isBlank()) {
            applyCallback.accept(currentGeneratedCode);
            close(OK_EXIT_CODE); // закрываем диалог с успехом
        } else {
            JOptionPane.showMessageDialog(getContentPanel(), "No code generated yet", "Error", JOptionPane.ERROR_MESSAGE);
        }

        if (currentTestCases != null && !currentTestCases.isBlank()) {
            close(OK_EXIT_CODE); // закрываем диалог с успехом
        } else {
            JOptionPane.showMessageDialog(getContentPanel(), "No code generated yet", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void setButtonsEnabled(boolean enabled) {
        generateButton.setEnabled(enabled);
        regenerateButton.setEnabled(enabled);
        applyButton.setEnabled(enabled);
        copyButton.setEnabled(enabled);
    }

    public void updateGeneratedCode(@NotNull String code) {
        SwingUtilities.invokeLater(() -> {
            currentGeneratedCode = code;
            codePanel.updateCode(code);
            progressBar.setVisible(false);
            setButtonsEnabled(true);
            regenerateButton.setEnabled(true);
            applyButton.setEnabled(true);
            // Переключаемся на вкладку с кодом
            tabbedPane.setSelectedIndex(2);
        });
    }

    public void updateTestCases(@NotNull String testCases) {
        SwingUtilities.invokeLater(() -> {
            currentTestCases = testCases;
            scenarioArea.setText(testCases);
            progressBar.setVisible(false);
            setButtonsEnabled(true);
            regenerateButton.setEnabled(true);
            applyButton.setEnabled(true);
            // Переключаемся на вкладку с кодом
            tabbedPane.setSelectedIndex(2);
        });
    }

    private String formatTestCase(TestCase tc) {
        return String.format("""
                        **ID:** %s
                        **Описание:** %s
                        **Метод:** %s %s
                        **Ожидаемый статус:** %s
                        **Входные данные:** %s
                        """,
                tc.getId(),
                tc.getDescription(),
                tc.getHttpMethod(),
                tc.getEndpoint(),
                tc.getExpectedStatus(),
                tc.getInput().isEmpty() ? "стандартные (см. контекст)" : "```json\n" + tc.getInput().toString() + "\n```"
        );
    }
}