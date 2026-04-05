package com.reasoningtestgen.action;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import com.reasoningtestgen.llm.LLMProvider;
import com.reasoningtestgen.llm.LLMProviderFactory;
import com.reasoningtestgen.model.GeneratedCode;
import com.reasoningtestgen.model.TestDesign;
import com.reasoningtestgen.refiner.SelfCorrectionEngine;
import com.reasoningtestgen.settings.PluginSettings;
import com.reasoningtestgen.validator.CompilationValidator;
import com.reasoningtestgen.validator.CompilationValidator.ValidationResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Non-blocking preview dialog for reviewing and editing prompts before LLM generation
 * Shows collected context, allows prompt modification, then triggers generation
 */
public class TestGenerationPreviewDialog extends DialogWrapper {

    private final Project project;
    private final String className;
    private final String methodName;
    private final String fullPrompt;
    private final Runnable onGenerateCallback;
    
    private Editor promptEditor;
    private Editor resultEditor;
    private JTextArea promptPreviewArea;
    private JTextArea resultPreviewArea; // Cache reference to result area
    private JButton generateButton;
    private JButton savePromptButton;
    private JButton saveResultButton;
    private JButton fixErrorsButton;
    private JLabel statusLabel;
    private JProgressBar progressBar;
    private String generatedCode;
    private boolean hasErrors = false;
    private String currentErrors = "";

    public TestGenerationPreviewDialog(@NotNull Project project,
                                        @NotNull String className,
                                        @NotNull String methodName,
                                        @NotNull String fullPrompt) {
        super(project, true);
        this.project = project;
        this.className = className;
        this.methodName = methodName;
        this.fullPrompt = fullPrompt;
        this.onGenerateCallback = null;
        
        setTitle("Test Generation Preview - " + className + "." + methodName);
        setModal(false); // Non-blocking
        init();
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setPreferredSize(new Dimension(1200, 700));
        mainPanel.setMinimumSize(new Dimension(800, 600));
        
        // Splitter for prompt and result
        JBSplitter splitter = new JBSplitter(true, 0.5f);
        splitter.setFirstComponent(createPromptPanel());
        splitter.setSecondComponent(createResultPanel());
        
        mainPanel.add(splitter, BorderLayout.CENTER);
        mainPanel.add(createStatusBar(), BorderLayout.SOUTH);
        
        return mainPanel;
    }

    /**
     * Create prompt editing panel
     */
    @NotNull
    private JPanel createPromptPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(JBUI.Borders.empty(10));
        
        // Header
        JPanel headerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        headerPanel.add(new JLabel("<html><b>📝 Prompt (editable)</b></html>"));
        panel.add(headerPanel, BorderLayout.NORTH);
        
        // Prompt text area (editable)
        promptPreviewArea = new JTextArea(fullPrompt);
        promptPreviewArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        promptPreviewArea.setLineWrap(true);
        promptPreviewArea.setWrapStyleWord(true);
        promptPreviewArea.setMargin(JBUI.insets(5));
        
        JBScrollPane scrollPane = new JBScrollPane(promptPreviewArea);
        scrollPane.setBorder(BorderFactory.createEtchedBorder());
        panel.add(scrollPane, BorderLayout.CENTER);
        
        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        
        savePromptButton = new JButton("💾 Save Prompt");
        savePromptButton.addActionListener(e -> savePromptToFile());
        buttonPanel.add(savePromptButton);
        
        JButton copyButton = new JButton("📋 Copy");
        copyButton.addActionListener(e -> {
            promptPreviewArea.selectAll();
            promptPreviewArea.copy();
            promptPreviewArea.select(0, 0);
        });
        buttonPanel.add(copyButton);
        
        panel.add(buttonPanel, BorderLayout.SOUTH);
        
        return panel;
    }

    /**
     * Create result display panel
     */
    @NotNull
    private JPanel createResultPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(JBUI.Borders.empty(10));
        
        // Header
        JPanel headerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        headerPanel.add(new JLabel("<html><b>✨ Generated Test</b></html>"));
        panel.add(headerPanel, BorderLayout.NORTH);
        
        // Result text area (read-only initially)
        resultPreviewArea = new JTextArea("Click 'Generate Test' to start generation...\n\nThe generated test code will appear here.");
        resultPreviewArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        resultPreviewArea.setEditable(false);
        resultPreviewArea.setLineWrap(true);
        resultPreviewArea.setWrapStyleWord(true);
        resultPreviewArea.setMargin(JBUI.insets(5));
        
        JBScrollPane scrollPane = new JBScrollPane(resultPreviewArea);
        scrollPane.setBorder(BorderFactory.createEtchedBorder());
        panel.add(scrollPane, BorderLayout.CENTER);
        
        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        
        generateButton = new JButton("🚀 Generate Test");
        generateButton.addActionListener(e -> startGeneration());
        buttonPanel.add(generateButton);
        
        fixErrorsButton = new JButton("🔧 Fix Errors");
        fixErrorsButton.setEnabled(false);
        fixErrorsButton.addActionListener(e -> startSelfCorrection());
        buttonPanel.add(fixErrorsButton);
        
        saveResultButton = new JButton("💾 Save Test");
        saveResultButton.setEnabled(false);
        saveResultButton.addActionListener(e -> saveGeneratedTest());
        buttonPanel.add(saveResultButton);
        
        panel.add(buttonPanel, BorderLayout.SOUTH);
        
        return panel;
    }

    /**
     * Create status bar
     */
    @NotNull
    private JPanel createStatusBar() {
        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setBorder(BorderFactory.createEtchedBorder());
        statusBar.setPreferredSize(new Dimension(1200, 30));
        
        statusLabel = new JLabel("  Ready. Edit prompt if needed, then click 'Generate Test'");
        statusBar.add(statusLabel, BorderLayout.CENTER);
        
        progressBar = new JProgressBar();
        progressBar.setVisible(false);
        progressBar.setStringPainted(true);
        statusBar.add(progressBar, BorderLayout.EAST);
        
        return statusBar;
    }

    @Override
    protected Action[] createActions() {
        return new Action[]{getOKAction()};
    }

    /**
     * Start test generation using LLM
     */
    private void startGeneration() {
        String currentPrompt = promptPreviewArea.getText();
        
        // Disable buttons during generation
        generateButton.setEnabled(false);
        savePromptButton.setEnabled(false);
        fixErrorsButton.setEnabled(false);
        progressBar.setVisible(true);
        progressBar.setIndeterminate(true);
        statusLabel.setText("  Generating test... This may take 30-60 seconds...");
        
        // Run generation in background
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Generating Test") {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                try {
                    indicator.setIndeterminate(false);
                    indicator.setText("Sending to LLM...");
                    indicator.setFraction(0.1);
                    
                    // Get LLM provider
                    PluginSettings settings = PluginSettings.getInstance();
                    LLMProvider llmProvider = LLMProviderFactory.createProvider(settings);
                    
                    // Call LLM with current prompt
                    String systemPrompt = "Вы — Senior Test Engineer. Сгенерируйте ПРОИЗВОДСТВЕННО-ГОТОВЫЕ unit-тесты для Java метода. Используйте JUnit 5, Mockito, AssertJ.";
                    String response = llmProvider.chat(currentPrompt, systemPrompt);
                    
                    indicator.setText("Receiving response...");
                    indicator.setFraction(0.7);
                    
                    // Extract code from response
                    String testCode = extractCodeFromResponse(response);

                    indicator.setText("Validating code...");
                    indicator.setFraction(0.9);

                    // Simple synchronous validation
                    hasErrors = checkForCompilationErrors(testCode);
                    
                    if (hasErrors) {
                        currentErrors = "Code may have compilation errors. Click Fix Errors to attempt automatic correction.";
                    }
                    
                    indicator.setText("Complete!");
                    indicator.setFraction(1.0);
                    
                    // Update UI on EDT
                    final String finalCode = testCode;
                    final boolean errors = hasErrors;
                    ApplicationManager.getApplication().invokeLater(() -> {
                        generatedCode = finalCode;
                        hasErrors = errors;
                        
                        if (hasErrors) {
                            statusLabel.setText("  ⚠ Test generated with errors! Click 'Fix Errors' to correct.");
                            fixErrorsButton.setEnabled(true);
                        } else {
                            statusLabel.setText("  ✓ Test generated successfully!");
                            fixErrorsButton.setEnabled(false);
                        }
                        
                        progressBar.setVisible(false);
                        generateButton.setEnabled(true);
                        savePromptButton.setEnabled(true);
                        saveResultButton.setEnabled(true);
                        
                        // Update result area
                        updateResultArea(finalCode);
                    });
                    
                } catch (Exception e) {
                    final String errorMsg = e.getMessage();
                    ApplicationManager.getApplication().invokeLater(() -> {
                        statusLabel.setText("  ✗ Error: " + errorMsg);
                        progressBar.setVisible(false);
                        generateButton.setEnabled(true);
                        savePromptButton.setEnabled(true);
                        fixErrorsButton.setEnabled(false);
                    });
                }
            }
        });
    }

    /**
     * Start self-correction process to fix compilation errors
     */
    private void startSelfCorrection() {
        if (generatedCode == null || generatedCode.isEmpty()) {
            Messages.showWarningDialog(project, "No generated code to fix", "Warning");
            return;
        }
        
        // Show current errors to user
        if (!currentErrors.isEmpty()) {
            int result = Messages.showYesNoDialog(
                project,
                "Found compilation errors:\n\n" + currentErrors + "\n\nAttempt to fix automatically?",
                "Fix Compilation Errors",
                Messages.getQuestionIcon()
            );
            
            if (result != Messages.YES) {
                return;
            }
        }
        
        // Disable buttons during correction
        fixErrorsButton.setEnabled(false);
        generateButton.setEnabled(false);
        progressBar.setVisible(true);
        progressBar.setIndeterminate(true);
        statusLabel.setText("  Analyzing errors and fixing... This may take 30-60 seconds...");
        
        // Run correction in background
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Fixing Test Errors") {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                try {
                    indicator.setIndeterminate(false);
                    indicator.setText("Analyzing compilation errors...");
                    indicator.setFraction(0.1);
                    
                    // Get settings
                    PluginSettings settings = PluginSettings.getInstance();
                    LLMProvider llmProvider = LLMProviderFactory.createProvider(settings);
                    
                    // Create self-correction engine
                    SelfCorrectionEngine correctionEngine = new SelfCorrectionEngine(
                        llmProvider,
                        project,
                        settings,
                        null // No prompt history for now
                    );
                    
                    indicator.setText("Running self-correction...");
                    indicator.setFraction(0.3);
                    
                    // Create test design (default)
                    TestDesign design = new TestDesign(
                        TestDesign.TestFramework.JUNIT5,
                        "should_{expected}_when_{condition}",
                        TestDesign.MockingStrategy.MOCKITO_EXTEND_WITH,
                        false,
                        TestDesign.AssertionLibrary.ASSERTJ
                    );
                    
                    // Run correction
                    GeneratedCode code = new GeneratedCode(
                        generatedCode,
                        java.util.List.of(),
                        java.util.Map.of()
                    );
                    
                    SelfCorrectionEngine.CorrectionResult result = correctionEngine.correctCode(
                        code,
                        design,
                        generatedCode
                    );
                    
                    indicator.setText("Complete!");
                    indicator.setFraction(1.0);
                    
                    // Update UI
                    final String fixedCode = result.correctedCode();
                    final boolean success = result.success();
                    final int attempts = result.attemptsCount();
                    final int remainingErrors = result.remainingErrors().size();
                    
                    ApplicationManager.getApplication().invokeLater(() -> {
                        generatedCode = fixedCode;
                        hasErrors = !success;
                        currentErrors = remainingErrors > 0 ? 
                            String.join("\n", result.remainingErrors()) : "";
                        
                        if (success) {
                            statusLabel.setText(String.format(
                                "  ✓ All errors fixed in %d attempt(s)!", attempts
                            ));
                            fixErrorsButton.setEnabled(false);
                        } else {
                            statusLabel.setText(String.format(
                                "  ⚠ Partial fix: %d error(s) remaining after %d attempt(s)",
                                remainingErrors, attempts
                            ));
                            fixErrorsButton.setEnabled(true);
                        }
                        
                        progressBar.setVisible(false);
                        generateButton.setEnabled(true);
                        saveResultButton.setEnabled(true);
                        
                        // Update result area with fixed code
                        updateResultArea(fixedCode);
                    });
                    
                } catch (Exception e) {
                    final String errorMsg = e.getMessage();
                    ApplicationManager.getApplication().invokeLater(() -> {
                        statusLabel.setText("  ✗ Correction error: " + errorMsg);
                        progressBar.setVisible(false);
                        fixErrorsButton.setEnabled(true);
                        generateButton.setEnabled(true);
                    });
                }
            }
        });
    }

    /**
     * Update result area with code
     */
    private void updateResultArea(String code) {
        if (resultPreviewArea != null) {
            resultPreviewArea.setText(code);
            resultPreviewArea.setCaretPosition(0);
        }
    }

    /**
     * Check for compilation errors in generated code
     */
    private boolean checkForCompilationErrors(String code) {
        // Check if response is JSON instead of Java code
        if (code.trim().startsWith("{") && code.contains("\"testMethods\"")) {
            System.out.println("WARNING: LLM returned JSON instead of Java code!");
            System.out.println("Attempting to extract Java code from JSON...");
            // The extractCodeFromResponse method will handle this
            return true; // Has "errors" - JSON instead of Java
        }
        
        // Check for TODO or placeholder comments
        if (code.contains("TODO") || code.contains("placeholder")) {
            return true;
        }
        
        // Check if code looks like valid Java (has class definition)
        if (!code.contains("class ") || !code.contains("{") || !code.contains("}")) {
            return true;
        }
        
        return false;
    }

    /**
     * Extract code from response (handles both Java code and JSON with codeSnippet)
     */
    @NotNull
    private String extractCodeFromResponse(String response) {
        // First try to extract from markdown code blocks
        int startIndex = response.indexOf("```java");
        if (startIndex == -1) {
            startIndex = response.indexOf("```");
        }
        
        if (startIndex != -1) {
            int codeStart = response.indexOf('\n', startIndex) + 1;
            int endIndex = response.indexOf("```", codeStart);
            if (endIndex != -1) {
                return response.substring(codeStart, endIndex).trim();
            }
        }
        
        // If response looks like JSON with testMethods, extract codeSnippets
        if (response.contains("\"testMethods\"") && response.contains("\"codeSnippet\"")) {
            try {
                return extractCodeFromJson(response);
            } catch (Exception e) {
                System.err.println("Failed to parse JSON response: " + e.getMessage());
                // Return original response if JSON parsing fails
                return response;
            }
        }
        
        // Return original response if nothing else worked
        return response;
    }

    /**
     * Extract Java code from JSON response with testMethods
     */
    @NotNull
    private String extractCodeFromJson(String jsonResponse) throws Exception {
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(jsonResponse);
        
        // Check if it has testMethods array
        com.fasterxml.jackson.databind.JsonNode testMethods = root.get("testMethods");
        if (testMethods == null || !testMethods.isArray()) {
            return jsonResponse;
        }
        
        StringBuilder javaCode = new StringBuilder();
        
        // Extract package and imports
        com.fasterxml.jackson.databind.JsonNode packageNode = root.get("package");
        if (packageNode != null) {
            javaCode.append("package ").append(packageNode.asText()).append(";\n\n");
        }
        
        com.fasterxml.jackson.databind.JsonNode imports = root.get("imports");
        if (imports != null && imports.isArray()) {
            for (com.fasterxml.jackson.databind.JsonNode imp : imports) {
                javaCode.append("import ").append(imp.asText()).append(";\n");
            }
            javaCode.append("\n");
        }
        
        // Extract test class name
        com.fasterxml.jackson.databind.JsonNode testClass = root.get("testClass");
        String className = testClass != null ? testClass.asText() : "GeneratedTest";
        
        javaCode.append("class ").append(className).append(" {\n\n");
        
        // Extract each test method
        for (com.fasterxml.jackson.databind.JsonNode testMethod : testMethods) {
            com.fasterxml.jackson.databind.JsonNode codeSnippet = testMethod.get("codeSnippet");
            if (codeSnippet != null) {
                String snippet = codeSnippet.asText()
                    .replace("\\n", "\n")
                    .replace("\\t", "\t")
                    .replace("\\\"", "\"");
                
                javaCode.append(snippet).append("\n\n");
            }
        }
        
        javaCode.append("}\n");
        
        return javaCode.toString();
    }

    /**
     * Save prompt to file
     */
    private void savePromptToFile() {
        String prompt = promptPreviewArea.getText();
        String fileName = className + "_" + methodName + "_prompt.txt";
        
        try {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setDialogTitle("Save Prompt");
            fileChooser.setSelectedFile(new File(fileName));
            
            if (fileChooser.showSaveDialog(getRootPane()) == JFileChooser.APPROVE_OPTION) {
                File file = fileChooser.getSelectedFile();
                try (FileWriter writer = new FileWriter(file)) {
                    writer.write(prompt);
                }
                statusLabel.setText("  ✓ Prompt saved to: " + file.getAbsolutePath());
            }
        } catch (IOException e) {
            Messages.showErrorDialog(project, "Failed to save prompt: " + e.getMessage(), "Error");
        }
    }

    /**
     * Save generated test to file
     */
    private void saveGeneratedTest() {
        if (generatedCode == null || generatedCode.isEmpty()) {
            Messages.showWarningDialog(project, "No generated test to save", "Warning");
            return;
        }
        
        String fileName = className + "Test.java";
        
        try {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setDialogTitle("Save Test");
            fileChooser.setSelectedFile(new File(fileName));
            
            if (fileChooser.showSaveDialog(getRootPane()) == JFileChooser.APPROVE_OPTION) {
                File file = fileChooser.getSelectedFile();
                try (FileWriter writer = new FileWriter(file)) {
                    writer.write(generatedCode);
                }
                statusLabel.setText("  ✓ Test saved to: " + file.getAbsolutePath());
                
                // Refresh file system
                VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
                if (virtualFile != null) {
                    virtualFile.refresh(false, false);
                }
            }
        } catch (IOException e) {
            Messages.showErrorDialog(project, "Failed to save test: " + e.getMessage(), "Error");
        }
    }

    /**
     * Get the edited prompt
     */
    @NotNull
    public String getEditedPrompt() {
        return promptPreviewArea.getText();
    }

    /**
     * Set the generated code
     */
    public void setGeneratedCode(@NotNull String code) {
        this.generatedCode = code;
    }
}
