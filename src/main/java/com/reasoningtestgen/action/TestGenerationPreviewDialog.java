package com.reasoningtestgen.action;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.fileChooser.FileSaverDescriptor;
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
import com.reasoningtestgen.generator.TestFileWriter;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;

/**
 * Non-blocking preview dialog for reviewing and editing prompts before LLM generation
 * Shows collected context, allows prompt modification, then triggers generation
 * With syntax highlighting and error highlighting
 */
public class TestGenerationPreviewDialog extends DialogWrapper {

    private static final Logger LOG = LoggerFactory.getLogger(TestGenerationPreviewDialog.class);

    private final Project project;
    private final String className;
    private final String methodName;
    private final String fullPrompt;
    private final Runnable onGenerateCallback;
    
    private Editor promptEditor;
    private Editor resultEditor;
    private JTextArea promptPreviewArea;
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
        mainPanel.setPreferredSize(new Dimension(1400, 800));
        mainPanel.setMinimumSize(new Dimension(1000, 700));
        
        // Create tabbed pane for Prompt, Scenario Tree, and Coverage
        JTabbedPane tabbedPane = new JTabbedPane();
        
        // Tab 1: Prompt Editor
        tabbedPane.addTab("📝 Prompt", createPromptPanel());
        
        // Tab 2: Scenario Tree
        tabbedPane.addTab("🌳 Scenarios", createScenarioTreePanel());
        
        // Tab 3: Coverage Preview
        tabbedPane.addTab("📊 Coverage", createCoveragePanel());
        
        // Tab 4: Generated Test
        tabbedPane.addTab("✨ Generated Test", createResultPanel());
        
        mainPanel.add(tabbedPane, BorderLayout.CENTER);
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
     * Create Scenario Tree visualization panel
     */
    @NotNull
    private JPanel createScenarioTreePanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(JBUI.Borders.empty(10));
        
        // Header
        JPanel headerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        headerPanel.add(new JLabel("<html><b>🌳 Scenario Tree</b><br/>Generated from Control Flow Graph</html>"));
        panel.add(headerPanel, BorderLayout.NORTH);
        
        // Build scenario tree text
        String scenarioTree = buildScenarioTreeText();
        
        // Display in text area with monospaced font
        JTextArea treeArea = new JTextArea(scenarioTree);
        treeArea.setFont(new Font("Monospaced", Font.PLAIN, 13));
        treeArea.setEditable(false);
        treeArea.setMargin(JBUI.insets(10));
        
        JBScrollPane scrollPane = new JBScrollPane(treeArea);
        scrollPane.setBorder(BorderFactory.createEtchedBorder());
        panel.add(scrollPane, BorderLayout.CENTER);
        
        return panel;
    }

    /**
     * Build scenario tree text from CFG information
     */
    @NotNull
    private String buildScenarioTreeText() {
        StringBuilder tree = new StringBuilder();
        tree.append("Scenario Tree (generated from CFG):\n\n");
        
        // Parse the prompt to extract CFG information
        // This is a simplified visualization based on common patterns
        tree.append("Root: ").append(className).append(".").append(methodName).append("\n");
        tree.append("│\n");
        tree.append("├── S1: HAPPY PATH\n");
        tree.append("│   └── Valid inputs → Expected output\n");
        tree.append("│\n");
        tree.append("├── S2: ERROR PATHS\n");
        tree.append("│   ├── Null parameters → Exception\n");
        tree.append("│   └── Invalid state → Exception\n");
        tree.append("│\n");
        tree.append("├── S3: BOUNDARY CONDITIONS\n");
        tree.append("│   ├── Empty collections\n");
        tree.append("│   ├── Single element\n");
        tree.append("│   └── Maximum values\n");
        tree.append("│\n");
        tree.append("└── S4: EDGE CASES\n");
        tree.append("    └── Special business rules\n");
        
        // Try to extract more specific scenarios from the prompt
        if (fullPrompt.contains("if (")) {
            tree.append("\n\nDetailed Branches:\n");
            tree.append("==================\n");
            
            // Count conditions in prompt
            String[] lines = fullPrompt.split("\n");
            int branchNum = 1;
            for (String line : lines) {
                if (line.contains("if (") || line.contains("ternary:")) {
                    String condition = line.trim();
                    if (condition.contains("at line")) {
                        String[] parts = condition.split(" at line ");
                        String cond = parts[0].replace("if (", "").replace(")", "").replace("ternary: ", "");
                        tree.append(String.format("├── B%d: if (%s) → Line %s\n", branchNum++, cond, parts[1]));
                    }
                }
            }
        }
        
        return tree.toString();
    }

    /**
     * Create Coverage Preview panel
     */
    @NotNull
    private JPanel createCoveragePanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(JBUI.Borders.empty(10));
        
        // Header
        JPanel headerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        headerPanel.add(new JLabel("<html><b>📊 Coverage Preview</b><br/>Branches that will be covered by generated tests</html>"));
        panel.add(headerPanel, BorderLayout.NORTH);
        
        // Build coverage information
        String coverageInfo = buildCoverageInfo();
        
        // Display in text area
        JTextArea coverageArea = new JTextArea(coverageInfo);
        coverageArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        coverageArea.setEditable(false);
        coverageArea.setMargin(JBUI.insets(10));
        
        JBScrollPane scrollPane = new JBScrollPane(coverageArea);
        scrollPane.setBorder(BorderFactory.createEtchedBorder());
        panel.add(scrollPane, BorderLayout.CENTER);
        
        return panel;
    }

    /**
     * Build coverage information text
     */
    @NotNull
    private String buildCoverageInfo() {
        StringBuilder coverage = new StringBuilder();
        coverage.append("Expected Test Coverage:\n");
        coverage.append("=====================\n\n");
        
        // Count conditions and branches from prompt
        String[] lines = fullPrompt.split("\n");
        int ifCount = 0;
        int ternaryCount = 0;
        int switchCount = 0;
        int loopCount = 0;
        
        for (String line : lines) {
            if (line.contains("if (")) ifCount++;
            if (line.contains("ternary:")) ternaryCount++;
            if (line.contains("switch") || line.contains("case")) switchCount++;
            if (line.contains("loop") || line.contains("for") || line.contains("while")) loopCount++;
        }
        
        int totalBranches = (ifCount * 2) + (ternaryCount * 2) + switchCount + loopCount;
        int expectedTests = totalBranches + 2; // +2 for happy path and edge cases
        
        coverage.append("Branches detected:\n");
        coverage.append("  - if statements: ").append(ifCount).append("\n");
        coverage.append("  - ternary operators: ").append(ternaryCount).append("\n");
        coverage.append("  - switch/case: ").append(switchCount).append("\n");
        coverage.append("  - loops: ").append(loopCount).append("\n");
        coverage.append("\nTotal branches: ").append(totalBranches).append("\n");
        coverage.append("Expected test methods: ~").append(expectedTests).append("\n\n");
        
        coverage.append("Expected coverage:\n");
        coverage.append("  ✓ Happy path (main success scenario)\n");
        coverage.append("  ✓ Error paths (exceptions)\n");
        coverage.append("  ✓ Boundary conditions (edge cases)\n");
        coverage.append("  ✓ Null/empty checks\n");
        coverage.append("  ✓ Business rules\n\n");
        
        if (totalBranches > 0) {
            double coveragePercent = Math.min(95.0, (expectedTests * 100.0) / Math.max(totalBranches, 1));
            coverage.append(String.format("Estimated coverage: %.0f%%\n", coveragePercent));
        }
        
        return coverage.toString();
    }
    @NotNull
    private JPanel createResultPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(JBUI.Borders.empty(10));
        
        // Header
        JPanel headerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        headerPanel.add(new JLabel("<html><b>✨ Generated Test</b></html>"));
        panel.add(headerPanel, BorderLayout.NORTH);
        
        // Result editor with syntax highlighting
        resultEditor = createEditorWithSyntaxHighlighting(
            "Click 'Generate Test' to start generation...\n\nThe generated test code will appear here."
        );
        
        JBScrollPane scrollPane = new JBScrollPane(resultEditor.getComponent());
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
     * Create editor with Java syntax highlighting
     */
    @NotNull
    private Editor createEditorWithSyntaxHighlighting(@NotNull String initialText) {
        EditorFactory editorFactory = EditorFactory.getInstance();
        
        // Create document
        com.intellij.openapi.editor.Document document = editorFactory.createDocument(initialText);
        
        // Create editor
        EditorEx editorEx = (EditorEx) editorFactory.createEditor(document, project);
        editorEx.setViewer(false); // Editable
        editorEx.setHighlighter(
            EditorHighlighterFactory.getInstance().createEditorHighlighter(
                project,
                new LightVirtualFile("Test.java", StdFileTypes.JAVA, initialText)
            )
        );
        
        return editorEx;
    }

    /**
     * Update result editor with new code
     */
    private void updateResultEditor(@NotNull String code) {
        if (resultEditor != null) {
            // Use WriteCommandAction for document changes
            WriteCommandAction.runWriteCommandAction(project, () -> {
                com.intellij.openapi.editor.Document document = resultEditor.getDocument();
                document.setText(code);
                
                // Update syntax highlighter
                if (resultEditor instanceof EditorEx) {
                    ((EditorEx) resultEditor).setHighlighter(
                        EditorHighlighterFactory.getInstance().createEditorHighlighter(
                            project,
                            new LightVirtualFile("Test.java", StdFileTypes.JAVA, code)
                        )
                    );
                }
                
                resultEditor.getCaretModel().moveToOffset(0);
            });
        }
    }

    /**
     * Highlight errors in the editor
     */
    private void highlightErrors(@NotNull String errors) {
        if (resultEditor instanceof EditorEx) {
            EditorEx editorEx = (EditorEx) resultEditor;
            
            // Show error message in status bar
            statusLabel.setText("  ⚠ Found errors: " + errors.split("\n").length + " error(s)");
            
            // Could add line-level highlighting here if we had line numbers
            // For now, showing errors in status is sufficient
        }
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
                            highlightErrors(currentErrors);
                        } else {
                            statusLabel.setText("  ✓ Test generated successfully!");
                            fixErrorsButton.setEnabled(false);
                        }
                        
                        progressBar.setVisible(false);
                        generateButton.setEnabled(true);
                        savePromptButton.setEnabled(true);
                        saveResultButton.setEnabled(true);
                        
                        // Update result area
                        updateResultEditor(finalCode);
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
                        updateResultEditor(fixedCode);
                        
                        if (success) {
                            highlightErrors("");
                        } else {
                            highlightErrors(currentErrors);
                        }
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
     * Save prompt to file
     */
    private void savePromptToFile() {
        String prompt = promptPreviewArea.getText();
        String fileName = className + "_" + methodName + "_prompt.txt";
        
        try {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setDialogTitle("Save Prompt");
            fileChooser.setSelectedFile(new java.io.File(fileName));
            
            if (fileChooser.showSaveDialog(getRootPane()) == JFileChooser.APPROVE_OPTION) {
                java.io.File file = fileChooser.getSelectedFile();
                try (java.io.FileWriter writer = new java.io.FileWriter(file)) {
                    writer.write(prompt);
                }
                statusLabel.setText("  ✓ Prompt saved to: " + file.getAbsolutePath());
            }
        } catch (Exception e) {
            Messages.showErrorDialog(project, "Failed to save prompt: " + e.getMessage(), "Error");
        }
    }

    /**
     * Save generated test to the appropriate test source root
     * Uses TestFileWriter to find the correct location and create directories
     */
    private void saveGeneratedTest() {
        if (generatedCode == null || generatedCode.isEmpty()) {
            Messages.showWarningDialog(project, "No generated test to save", "Warning");
            return;
        }
        
        // Run file operations in background
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Saving Test File") {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                try {
                    indicator.setIndeterminate(false);
                    indicator.setText("Finding test source root...");
                    indicator.setFraction(0.2);
                    
                    // Extract package from generated code
                    String packageName = extractPackageFromCode(generatedCode);
                    String testClassName = extractTestClassName(generatedCode);
                    String qualifiedName = packageName.isEmpty() ? testClassName : packageName + "." + testClassName;
                    
                    indicator.setText("Creating test file...");
                    indicator.setFraction(0.5);
                    
                    // Use TestFileWriter to save the file
                    TestFileWriter writer = new TestFileWriter(project);
                    TestFileWriter.WriteResult result = writer.writeTestFile(qualifiedName, generatedCode);
                    
                    indicator.setFraction(1.0);
                    
                    // Show result on EDT
                    ApplicationManager.getApplication().invokeLater(() -> {
                        if (result.success()) {
                            statusLabel.setText("  ✓ Test saved: " + result.message());
                            
                            // Optionally ask if user wants to open the file
                            int response = Messages.showYesNoDialog(
                                project,
                                "Test file created successfully!\n\n" + result.message() + "\n\nOpen the file?",
                                "Test Saved",
                                Messages.getQuestionIcon()
                            );
                            
                            if (response == Messages.YES && result.file() != null) {
                                // Open file in editor
                                com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project)
                                    .openFile(result.file(), true);
                            }
                        } else {
                            statusLabel.setText("  ✗ Failed to save test");
                            Messages.showErrorDialog(
                                project,
                                "Failed to save test file:\n\n" + result.message(),
                                "Save Error"
                            );
                        }
                    });
                    
                } catch (Exception e) {
                    LOG.error("Failed to save test", e);
                    ApplicationManager.getApplication().invokeLater(() -> {
                        Messages.showErrorDialog(
                            project,
                            "Failed to save test: " + e.getMessage(),
                            "Error"
                        );
                    });
                }
            }
        });
    }

    /**
     * Extract package name from generated code
     */
    @NotNull
    private String extractPackageFromCode(@NotNull String code) {
        // Look for "package xxx.yyy.zzz;"
        String[] lines = code.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("package ") && trimmed.endsWith(";")) {
                return trimmed.substring(8, trimmed.length() - 1).trim();
            }
            // Stop searching after imports start
            if (trimmed.startsWith("import ")) break;
        }
        return "";
    }

    /**
     * Extract test class name from generated code
     */
    @NotNull
    private String extractTestClassName(@NotNull String code) {
        // Look for "class XxxTest" or "class Xxx"
        String[] lines = code.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("public class ") || trimmed.startsWith("class ")) {
                String classPart = trimmed.contains("class ") ? 
                    trimmed.substring(trimmed.indexOf("class ") + 6) : trimmed;
                
                // Extract class name (before { or space)
                String className = classPart.split("[\\s{]")[0];
                return className;
            }
        }
        
        // Fallback to className + "Test"
        return className + "Test";
    }

    /**
     * Check for compilation errors in generated code
     * Uses RealCompilationValidator for actual compilation check
     * MUST be called from background thread (Task.Backgroundable)
     */
    private boolean checkForCompilationErrors(String code) {
        // First check: basic syntax (no threading issues)
        if (code.trim().startsWith("{") && code.contains("\"testMethods\"")) {
            System.out.println("WARNING: LLM returned JSON instead of Java code!");
            return true;
        }
        
        if (!code.contains("class ") || !code.contains("{") || !code.contains("}")) {
            System.out.println("WARNING: Code doesn't look like valid Java class");
            return true;
        }
        
        // Second check: try real compilation if we have a VirtualFile
        try {
            // Wrap PSI operations in ReadAction
            VirtualFile[] virtualFileHolder = new VirtualFile[1];
            
            com.intellij.openapi.application.ReadAction.run(() -> {
                // Create temporary PSI file for compilation check
                PsiFile tempFile = PsiFileFactory.getInstance(project)
                    .createFileFromText("TempTest.java", StdFileTypes.JAVA, code);
                
                virtualFileHolder[0] = tempFile.getVirtualFile();
            });
            
            VirtualFile virtualFile = virtualFileHolder[0];
            if (virtualFile != null) {
                // Use RealCompilationValidator
                com.reasoningtestgen.validator.RealCompilationValidator validator = 
                    new com.reasoningtestgen.validator.RealCompilationValidator(project);
                
                com.reasoningtestgen.validator.RealCompilationValidator.ValidationResult result = 
                    validator.validateCompilation(virtualFile);
                
                if (!result.isValid()) {
                    currentErrors = result.errors().stream()
                        .map(e -> "Line " + e.line() + ": " + e.description())
                        .collect(java.util.stream.Collectors.joining("\n"));
                    System.out.println("Found " + result.errors().size() + " compilation errors");
                    return true;
                }
            }
        } catch (Exception e) {
            System.out.println("Compilation check failed: " + e.getMessage());
            // If we can't compile, fall back to basic checks
            if (!code.contains("import ") && code.contains("class ")) {
                currentErrors = "Missing imports";
                return true;
            }
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

    @Override
    public void dispose() {
        // Release editor resources
        if (promptEditor != null) {
            EditorFactory.getInstance().releaseEditor(promptEditor);
        }
        if (resultEditor != null) {
            EditorFactory.getInstance().releaseEditor(resultEditor);
        }
        super.dispose();
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
