package com.reasoningtestgen.action;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import com.reasoningtestgen.generator.TestFileWriter;
import com.reasoningtestgen.llm.LLMProvider;
import com.reasoningtestgen.llm.LLMProviderFactory;
import com.reasoningtestgen.llm.ReasoningEngine;
import com.reasoningtestgen.model.*;
import com.reasoningtestgen.service.PromptHistoryService;
import com.reasoningtestgen.settings.PluginSettings;
import com.reasoningtestgen.validator.CompilerLoopEngine;
import com.reasoningtestgen.validator.RealCompilationValidator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * Enhanced dialog with 5 tabs showing each step of Reasoning Pipeline
 * Provides real-time progress feedback to prevent "frozen UI" perception
 */
public class ReasoningPipelineDialog extends DialogWrapper {

    private static final Logger LOG = LoggerFactory.getLogger(ReasoningPipelineDialog.class);

    private final Project project;
    private final String className;
    private final String methodName;
    private final MethodContext methodContext;
    private final String initialPrompt;

    // UI Components
    private JTabbedPane pipelineTabs;
    private JPanel intentPanel;
    private JPanel scenariosPanel;
    private JPanel designPanel;
    private JPanel codePanel;
    private JPanel validationPanel;

    // Editor components
    private Editor intentEditor;
    private Editor codeEditor;
    private JTextArea scenariosArea;
    private JTextArea designArea;
    private JTextArea validationArea;

    // Progress components
    private JProgressBar progressBar;
    private JLabel statusLabel;
    private JButton runPipelineButton;
    private JButton saveButton;

    // State
    private IntentOutput intentOutput;
    private ScenarioTree scenarioTree;
    private TestDesign testDesign;
    private GeneratedCode generatedCode;
    private CompilerLoopEngine.CompilerLoopResult correctionResult;

    public ReasoningPipelineDialog(@NotNull Project project,
                                    @NotNull String className,
                                    @NotNull String methodName,
                                    @NotNull MethodContext methodContext,
                                    @NotNull String initialPrompt) {
        super(project, true);
        this.project = project;
        this.className = className;
        this.methodName = methodName;
        this.methodContext = methodContext;
        this.initialPrompt = initialPrompt;

        setTitle("Reasoning Pipeline: " + className + "." + methodName);
        setModal(false);
        init();
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setPreferredSize(new Dimension(1400, 900));

        // Progress bar at the top of the dialog
        JPanel progressPanel = createProgressPanel();
        mainPanel.add(progressPanel, BorderLayout.NORTH);

        // Create tabbed pane with 6 tabs for prompt + 5 reasoning steps
        pipelineTabs = new JTabbedPane();

        // Tab 0: Original Prompt (NEW - for context)
        pipelineTabs.addTab("📝 Prompt", createScrollPane(createPromptPanel()));

        // Tab 1: Intent Analysis
        intentPanel = createIntentPanel();
        pipelineTabs.addTab("🎯 Step 1: Intent", createScrollPane(intentPanel));

        // Tab 2: Scenario Mapping
        scenariosPanel = createScenariosPanel();
        pipelineTabs.addTab("🌳 Step 2: Scenarios", createScrollPane(scenariosPanel));

        // Tab 3: Test Design
        designPanel = createDesignPanel();
        pipelineTabs.addTab("📋 Step 3: Design", createScrollPane(designPanel));

        // Tab 4: Code Generation
        codePanel = createCodePanel();
        pipelineTabs.addTab("✨ Step 4: Code", createScrollPane(codePanel));

        // Tab 5: Validation
        validationPanel = createValidationPanel();
        pipelineTabs.addTab("✅ Step 5: Validation", createScrollPane(validationPanel));

        mainPanel.add(pipelineTabs, BorderLayout.CENTER);
        mainPanel.add(createStatusBar(), BorderLayout.SOUTH);

        return mainPanel;
    }

    /**
     * Create progress panel with progress bar and status label
     */
    @NotNull
    private JPanel createProgressPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(JBUI.Borders.empty(5));
        panel.setPreferredSize(new Dimension(1400, 50));

        // Status label
        JLabel progressLabel = new JLabel("  Ready");
        progressLabel.setBorder(JBUI.Borders.emptyLeft(5));
        panel.add(progressLabel, BorderLayout.WEST);

        // Progress bar
        progressBar = new JProgressBar();
        progressBar.setVisible(false);
        progressBar.setStringPainted(true);
        progressBar.setPreferredSize(new Dimension(300, 20));
        panel.add(progressBar, BorderLayout.CENTER);

        return panel;
    }

    @NotNull
    private JScrollPane createScrollPane(@NotNull JComponent component) {
        JBScrollPane scrollPane = new JBScrollPane(component);
        scrollPane.setBorder(BorderFactory.createEtchedBorder());
        return scrollPane;
    }

    /**
     * Tab 1: Intent Analysis
     */
    @NotNull
    private JPanel createIntentPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(JBUI.Borders.empty(10));

        JLabel header = new JLabel("<html><b>🎯 Intent & Contract Analysis</b><br/>" +
            "Analyzes method purpose, preconditions, postconditions, and exceptions</html>");
        panel.add(header, BorderLayout.NORTH);

        intentEditor = createEditor("");
        panel.add(intentEditor.getComponent(), BorderLayout.CENTER);

        return panel;
    }

    /**
     * Tab 0: Original Prompt (added for context)
     */
    @NotNull
    private JPanel createPromptPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(JBUI.Borders.empty(10));

        JLabel header = new JLabel("<html><b>📝 Original Prompt</b><br/>" +
            "Full prompt sent to LLM including system and user sections</html>");
        panel.add(header, BorderLayout.NORTH);

        Editor promptEditor = createEditor(initialPrompt);
        panel.add(promptEditor.getComponent(), BorderLayout.CENTER);

        return panel;
    }

    /**
     * Tab 2: Scenario Mapping
     */
    @NotNull
    private JPanel createScenariosPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(JBUI.Borders.empty(10));

        JLabel header = new JLabel("<html><b>🌳 Scenario Tree</b><br/>" +
            "Test scenarios generated from Control Flow Graph</html>");
        panel.add(header, BorderLayout.NORTH);

        scenariosArea = new JTextArea();
        scenariosArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        scenariosArea.setEditable(false);
        scenariosArea.setMargin(JBUI.insets(10));
        panel.add(new JBScrollPane(scenariosArea), BorderLayout.CENTER);

        return panel;
    }

    /**
     * Tab 3: Test Design
     */
    @NotNull
    private JPanel createDesignPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(JBUI.Borders.empty(10));

        JLabel header = new JLabel("<html><b>📋 Test Design Strategy</b><br/>" +
            "Framework, mocking, and assertion library selection</html>");
        panel.add(header, BorderLayout.NORTH);

        designArea = new JTextArea();
        designArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        designArea.setEditable(false);
        designArea.setMargin(JBUI.insets(10));
        panel.add(new JBScrollPane(designArea), BorderLayout.CENTER);

        return panel;
    }

    /**
     * Tab 4: Code Generation
     */
    @NotNull
    private JPanel createCodePanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(JBUI.Borders.empty(10));

        JLabel header = new JLabel("<html><b>✨ Generated Test Code</b><br/>" +
            "Production-ready unit tests with Given-When-Then structure</html>");
        panel.add(header, BorderLayout.NORTH);

        codeEditor = createEditor("");
        panel.add(codeEditor.getComponent(), BorderLayout.CENTER);

        return panel;
    }

    /**
     * Tab 5: Validation
     */
    @NotNull
    private JPanel createValidationPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(JBUI.Borders.empty(10));

        JLabel header = new JLabel("<html><b>✅ Compiler Loop Validation</b><br/>" +
            "Iterative compilation check and error correction</html>");
        panel.add(header, BorderLayout.NORTH);

        validationArea = new JTextArea();
        validationArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        validationArea.setEditable(false);
        validationArea.setMargin(JBUI.insets(10));
        panel.add(new JBScrollPane(validationArea), BorderLayout.CENTER);

        return panel;
    }

    /**
     * Create editor with syntax highlighting
     */
    @NotNull
    private Editor createEditor(@NotNull String initialText) {
        EditorFactory editorFactory = EditorFactory.getInstance();
        com.intellij.openapi.editor.Document document = editorFactory.createDocument(initialText);

        EditorEx editorEx = (EditorEx) editorFactory.createEditor(document, project);
        editorEx.setViewer(false);
        editorEx.setHighlighter(
            EditorHighlighterFactory.getInstance().createEditorHighlighter(
                project,
                new LightVirtualFile("Temp.java", StdFileTypes.JAVA, initialText)
            )
        );

        return editorEx;
    }

    /**
     * Create status bar with progress
     */
    @NotNull
    private JPanel createStatusBar() {
        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setBorder(BorderFactory.createEtchedBorder());
        statusBar.setPreferredSize(new Dimension(1200, 35));

        // Status label (progress bar is at the top)
        statusLabel = new JLabel("  📋 Ready to run reasoning pipeline");
        statusLabel.setBorder(JBUI.Borders.emptyLeft(5));
        statusBar.add(statusLabel, BorderLayout.CENTER);

        // Buttons only (no progress bar here - it's at the top)
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));

        runPipelineButton = new JButton("🚀 Run Pipeline");
        runPipelineButton.addActionListener(e -> startPipeline());
        buttonPanel.add(runPipelineButton);

        saveButton = new JButton("💾 Save Test");
        saveButton.setEnabled(false);
        saveButton.addActionListener(e -> saveGeneratedTest());
        buttonPanel.add(saveButton);

        statusBar.add(buttonPanel, BorderLayout.SOUTH);

        return statusBar;
    }

    /**
     * Start the reasoning pipeline
     */
    private void startPipeline() {
        runPipelineButton.setEnabled(false);
        saveButton.setEnabled(false);
        progressBar.setVisible(true);
        progressBar.setIndeterminate(false);
        progressBar.setValue(0);
        statusLabel.setText("  🚀 Starting reasoning pipeline...");

        // Run pipeline in background thread, update UI in EDT
        new Thread(() -> {
            try {
                statusLabel.setText("  ⚙️  Initializing LLM provider...");
                progressBar.setValue(5);

                // Initialize LLM provider
                PluginSettings settings = PluginSettings.getInstance();
                LLMProvider llmProvider = LLMProviderFactory.createProvider(settings);
                PromptHistoryService historyService = new PromptHistoryService(project.getName());

                // Create reasoning engine
                ReasoningEngine reasoningEngine = new ReasoningEngine(
                    settings,
                    historyService,
                    project
                );

                // ===== STEP 1: Intent Analysis =====
                LOG.info("Step 1/5: Intent Analysis");
                ApplicationManager.getApplication().invokeLater(() -> {
                    statusLabel.setText("  🎯 Step 1/5: Analyzing method intent and contract...");
                    pipelineTabs.setSelectedIndex(1); // Switch to Intent tab
                    progressBar.setValue(20);
                });

                intentOutput = reasoningEngine.analyzeIntent(methodContext);
                ApplicationManager.getApplication().invokeLater(() -> {
                    pipelineTabs.setSelectedIndex(1); // Switch to Intent tab after completion
                });
                updateIntentDisplay();

                // ===== STEP 2: Scenario Mapping =====
                LOG.info("Step 2/5: Scenario Mapping");
                ApplicationManager.getApplication().invokeLater(() -> {
                    statusLabel.setText("  🌳 Step 2/5: Building scenario tree from CFG...");
                    pipelineTabs.setSelectedIndex(2);
                    progressBar.setValue(40);
                });

                scenarioTree = reasoningEngine.generateScenarios(methodContext, intentOutput);
                updateScenariosDisplay();

                // ===== STEP 3: Test Design =====
                LOG.info("Step 3/5: Test Design");
                ApplicationManager.getApplication().invokeLater(() -> {
                    statusLabel.setText("  📋 Step 3/5: Selecting test framework and mocking strategy...");
                    pipelineTabs.setSelectedIndex(3);
                    progressBar.setValue(60);
                });

                testDesign = reasoningEngine.designTests(methodContext, scenarioTree);
                updateDesignDisplay();

                // ===== STEP 4: Code Generation =====
                LOG.info("Step 4/5: Code Generation");
                ApplicationManager.getApplication().invokeLater(() -> {
                    statusLabel.setText("  ✨ Step 4/5: Generating test code with JUnit/Mockito...");
                    pipelineTabs.setSelectedIndex(4);
                    progressBar.setValue(80);
                });

                generatedCode = reasoningEngine.generateCode(testDesign, scenarioTree, methodContext);
                updateCodeDisplay();

                // ===== STEP 5: Compiler Loop Validation =====
                LOG.info("Step 5/5: Compiler Loop Validation");
                ApplicationManager.getApplication().invokeLater(() -> {
                    statusLabel.setText("  ✅ Step 5/5: Validating compilation and fixing errors...");
                    pipelineTabs.setSelectedIndex(5);
                    progressBar.setValue(90);
                });

                CompilerLoopEngine compilerLoop = new CompilerLoopEngine(
                    project,
                    llmProvider,
                    historyService,
                    settings.getMaxCorrectionAttempts(),
                    settings.isCorrectUntilSuccess()
                );

                correctionResult = compilerLoop.runCompilerLoop(generatedCode, testDesign);
                updateValidationDisplay();

                // Complete
                ApplicationManager.getApplication().invokeLater(() -> {
                    progressBar.setValue(100);
                    progressBar.setIndeterminate(false);

                    if (correctionResult.success()) {
                        statusLabel.setText(String.format(
                            "  ✅ Pipeline complete! Fixed in %d attempt(s)",
                            correctionResult.attemptsCount()
                        ));
                    } else {
                        statusLabel.setText(String.format(
                            "  ⚠️ Pipeline complete with %d remaining error(s)",
                            correctionResult.remainingErrors().size()
                        ));
                    }

                    runPipelineButton.setEnabled(true);
                    saveButton.setEnabled(correctionResult.success());
                });

            } catch (Exception e) {
                LOG.error("Pipeline failed", e);
                ApplicationManager.getApplication().invokeLater(() -> {
                    statusLabel.setText("  ❌ Pipeline failed: " + e.getMessage());
                    progressBar.setValue(0);
                    progressBar.setVisible(false);
                    runPipelineButton.setEnabled(true);

                    // Show error in validation tab
                    validationArea.setText("## ❌ Pipeline Error\n\n" +
                        "**Error:** " + e.getClass().getSimpleName() + "\n\n" +
                        "**Message:** " + e.getMessage() + "\n\n" +
                        "**Check logs for details.**");
                    pipelineTabs.setSelectedIndex(5);
                });
            }
        }).start();
    }

    /**
     * Update intent display
     */
    private void updateIntentDisplay() {
        if (intentOutput == null) return;

        String intentText = String.format("""
            ## 🎯 Intent Analysis Results
            
            ### Goal
            %s
            
            ### Preconditions
            %s
            
            ### Postconditions
            %s
            
            ### Side Effects
            %s
            
            ### Exceptions
            %s
            """,
            intentOutput.goal(),
            formatList(intentOutput.preconditions()),
            formatList(intentOutput.postconditions()),
            formatList(intentOutput.sideEffects()),
            formatList(intentOutput.exceptions())
        );

        WriteCommandAction.runWriteCommandAction(project, () -> {
            intentEditor.getDocument().setText(intentText);
        });
    }

    /**
     * Update scenarios display
     */
    private void updateScenariosDisplay() {
        if (scenarioTree == null) return;

        StringBuilder sb = new StringBuilder();
        sb.append("🌳 Scenario Tree\n");
        sb.append("===============\n\n");
        sb.append("Root: ").append(className).append(".").append(methodName).append("\n");

        if (scenarioTree.children() != null) {
            for (ScenarioTree.ScenarioNode node : scenarioTree.children()) {
                appendScenarioNode(sb, node, 0);
            }
        }

        scenariosArea.setText(sb.toString());
    }

    private void appendScenarioNode(StringBuilder sb, ScenarioTree.ScenarioNode node, int depth) {
        String indent = "  ".repeat(depth);
        String icon = switch (node.type()) {
            case HAPPY -> "✅";
            case ERROR -> "❌";
            case BOUNDARY -> "⚠️";
            case STATE -> "🔄";
            case PERFORMANCE -> "⚡";
        };

        sb.append(indent).append("├── ").append(icon).append(" ")
          .append(node.id()).append(": ").append(node.description()).append("\n");
        sb.append(indent).append("│   Type: ").append(node.type()).append("\n");
        sb.append(indent).append("│   Conditions: ").append(node.inputConditions()).append("\n");
        sb.append(indent).append("│   Expected: ").append(node.expectedOutcome()).append("\n");

        if (node.testCaseSpec() != null) {
            sb.append(indent).append("│   Test: ").append(node.testCaseSpec().testName()).append("\n");
            sb.append(indent).append("│   Priority: ").append(node.testCaseSpec().priority()).append("\n");
        }

        if (node.children() != null && !node.children().isEmpty()) {
            for (ScenarioTree.ScenarioNode child : node.children()) {
                appendScenarioNode(sb, child, depth + 1);
            }
        }
        sb.append("\n");
    }

    /**
     * Update design display
     */
    private void updateDesignDisplay() {
        if (testDesign == null) return;

        String designText = String.format("""
            ## 📋 Test Design Strategy
            
            ### Test Framework
            **%s**
            
            ### Naming Convention
            `%s`
            
            ### Mocking Strategy
            **%s**
            
            ### Parameterized Tests
            %s
            
            ### Assertion Library
            **%s**
            
            ### Recommended Structure
            ```java
            @ExtendWith(MockitoExtension.class)
            class %sTest {
                @Mock private Dependency dependency;
                private %s classUnderTest;
                
                @BeforeEach
                void setUp() { ... }
                
                @Test
                void should_expectedResult_when_condition() { ... }
            }
            ```
            """,
            testDesign.framework(),
            testDesign.namingConvention(),
            testDesign.mockingStrategy(),
            testDesign.useParameterized() ? "Yes" : "No",
            testDesign.assertionLibrary(),
            className,
            className
        );

        designArea.setText(designText);
    }

    /**
     * Update code display
     */
    private void updateCodeDisplay() {
        if (generatedCode == null) return;

        ApplicationManager.getApplication().invokeLater(() -> {
            WriteCommandAction.runWriteCommandAction(project, () -> {
                codeEditor.getDocument().setText(generatedCode.javaCode());

                // Update syntax highlighter
                if (codeEditor instanceof EditorEx) {
                    ((EditorEx) codeEditor).setHighlighter(
                        EditorHighlighterFactory.getInstance().createEditorHighlighter(
                            project,
                            new LightVirtualFile(className + "Test.java", StdFileTypes.JAVA, generatedCode.javaCode())
                        )
                    );
                }

                // Enable save button when code is generated
                saveButton.setEnabled(true);
            });
        });
    }

    /**
     * Update validation display
     */
    private void updateValidationDisplay() {
        if (correctionResult == null) return;

        StringBuilder sb = new StringBuilder();
        sb.append("## ✅ Compiler Loop Validation Results\n\n");

        if (correctionResult.success()) {
            sb.append("### Status: ✅ SUCCESS\n\n");
            sb.append(String.format("Fixed in **%d** compilation loop iteration(s)\n\n", 
                correctionResult.attemptsCount()));
        } else {
            sb.append("### Status: ⚠️ PARTIAL SUCCESS\n\n");
            sb.append(String.format("Still has **%d** compilation error(s) after %d attempt(s)\n\n",
                correctionResult.remainingErrors().size(),
                correctionResult.attemptsCount()));
        }

        // Show attempt history
        sb.append("### Attempt History\n\n");
        for (CompilerLoopEngine.CompilerLoopAttempt attempt : correctionResult.attempts()) {
            sb.append(String.format("#### Attempt #%d\n", attempt.attemptNumber()));
            sb.append(String.format("- Errors found: %d\n", attempt.errors().size()));
            sb.append(String.format("- Fix applied: %s\n", attempt.fixApplied() ? "Yes" : "No"));
            sb.append(String.format("- Description: %s\n\n", attempt.fixDescription()));
        }

        // Show remaining errors
        if (!correctionResult.remainingErrors().isEmpty()) {
            sb.append("### Remaining Errors\n\n");
            for (RealCompilationValidator.CompilationError error : correctionResult.remainingErrors()) {
                String lineStr = error.line() > 0 ? String.valueOf(error.line()) : "?";
                sb.append(String.format("- Line %s: %s - %s\n",
                    lineStr,
                    error.category(),
                    error.description()));
            }
        } else {
            sb.append("### Remaining Errors\n\n");
            sb.append("✅ No remaining errors - code compiles successfully!\n");
        }

        validationArea.setText(sb.toString());
    }

    /**
     * Save generated test
     */
    private void saveGeneratedTest() {
        if (correctionResult == null || !correctionResult.success()) {
            JOptionPane.showMessageDialog(
                getContentPane(),
                "Cannot save: code has compilation errors",
                "Save Error",
                JOptionPane.ERROR_MESSAGE
            );
            return;
        }

        String testClassName = className + "Test";
        String qualifiedName = "tests." + testClassName;

        TestFileWriter writer = new TestFileWriter(project);
        TestFileWriter.WriteResult result = writer.writeTestFile(
            qualifiedName,
            correctionResult.correctedCode()
        );

        if (result.success()) {
            JOptionPane.showMessageDialog(
                getContentPane(),
                "Test saved successfully!\n" + result.message(),
                "Success",
                JOptionPane.INFORMATION_MESSAGE
            );

            // Open file in editor
            if (result.file() != null) {
                FileEditorManager.getInstance(project).openFile(result.file(), true);
            }
        } else {
            JOptionPane.showMessageDialog(
                getContentPane(),
                "Failed to save test:\n" + result.message(),
                "Save Error",
                JOptionPane.ERROR_MESSAGE
            );
        }
    }

    @NotNull
    private String formatList(@NotNull List<String> items) {
        if (items == null || items.isEmpty()) return "None";
        return items.stream()
            .map(item -> "• " + item)
            .reduce("", (a, b) -> a + "\n" + b);
    }

    @Override
    protected Action[] createActions() {
        return new Action[]{getOKAction()};
    }

    @Override
    public void dispose() {
        if (intentEditor != null) {
            EditorFactory.getInstance().releaseEditor(intentEditor);
        }
        if (codeEditor != null) {
            EditorFactory.getInstance().releaseEditor(codeEditor);
        }
        super.dispose();
    }
}
