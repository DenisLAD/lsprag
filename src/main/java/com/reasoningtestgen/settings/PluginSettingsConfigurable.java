package com.reasoningtestgen.settings;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.reasoningtestgen.llm.GigaChatProvider;
import com.reasoningtestgen.llm.LLMProviderType;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.filechooser.FileFilter;
import java.awt.*;
import java.io.File;
import java.util.Objects;

public class PluginSettingsConfigurable implements Configurable {

    private PluginSettings settings;

    private JPanel mainPanel;

    // LLM settings
    private ComboBox<LLMProviderType> providerTypeComboBox;
    private JTextField endpointTextField;
    private JPasswordField apiKeyPasswordField;
    private JTextField modelTextField;
    private JTextField timeoutTextField;
    private JTextField maxScenariosTextField;

    // Checkboxes
    private JBCheckBox showPreviewCheckBox;
    private JBCheckBox autoFormatCheckBox;
    private JBCheckBox enableValidationCheckBox;
    private JBCheckBox savePromptHistoryCheckBox;

    // Self-correction settings
    private JBCheckBox correctUntilSuccessCheckBox;
    private JTextField maxCorrectionAttemptsField;

    // Source code settings
    private JBCheckBox includeSourceCodeCheckBox;
    private JBCheckBox includeMethodSigsCheckBox;
    private JTextField maxCodeLengthField;

    // Deep analysis settings
    private JTextField analysisDepthField;
    private JTextField implementationSearchDepthField;
    private JBCheckBox includeCalledMethodsCheckBox;
    private JBCheckBox includeDTOStructuresCheckBox;
    private JBCheckBox includeDataTransformationsCheckBox;

    // SSL/JKS Settings
    private JBCheckBox useSSLCheckBox;
    private JPanel sslKeyStorePathPanel;
    private JTextField sslKeyStorePathField;
    private JButton sslKeyStorePathButton;
    private JPasswordField sslKeyStorePasswordField;
    private ComboBox<String> sslKeyStoreTypeComboBox;

    // GigaChat Settings
    private JPanel gigaChatPanel;
    private ComboBox<GigaChatProvider.AuthMethod> gigaChatAuthMethodComboBox;
    private JTextField gigaChatClientIdField;
    private JPasswordField gigaChatClientSecretField;
    private JTextField gigaChatScopeField;
    private JLabel gigaChatClientIdLabel;
    private JLabel gigaChatClientSecretLabel;

    @Nls(capitalization = Nls.Capitalization.Title)
    @Override
    public String getDisplayName() {
        return "Reasoning Test Generator";
    }

    @Nullable
    @Override
    public JComponent createComponent() {
        mainPanel = new JPanel();
        mainPanel.setLayout(new GridLayoutManager(30, 2, new Insets(10, 10, 10, 10), 5, 5));

        int row = 0;

        // === LLM Provider Section ===
        mainPanel.add(new JBLabel("LLM Provider:"), createConstraint(row, 0, GridConstraints.FILL_HORIZONTAL));
        providerTypeComboBox = new ComboBox<>(LLMProviderType.values());
        mainPanel.add(providerTypeComboBox, createConstraint(row, 1, GridConstraints.FILL_HORIZONTAL));
        row++;

        // Endpoint
        mainPanel.add(new JBLabel("Endpoint:"), createConstraint(row, 0, GridConstraints.FILL_HORIZONTAL));
        endpointTextField = new JBTextField();
        mainPanel.add(endpointTextField, createConstraint(row, 1, GridConstraints.FILL_HORIZONTAL));
        row++;

        // API Key
        mainPanel.add(new JBLabel("API Key:"), createConstraint(row, 0, GridConstraints.FILL_HORIZONTAL));
        apiKeyPasswordField = new JPasswordField();
        mainPanel.add(apiKeyPasswordField, createConstraint(row, 1, GridConstraints.FILL_HORIZONTAL));
        row++;

        // Model
        mainPanel.add(new JBLabel("Model:"), createConstraint(row, 0, GridConstraints.FILL_HORIZONTAL));
        modelTextField = new JBTextField();
        mainPanel.add(modelTextField, createConstraint(row, 1, GridConstraints.FILL_HORIZONTAL));
        row++;

        // Timeout
        mainPanel.add(new JBLabel("Timeout (seconds):"), createConstraint(row, 0, GridConstraints.FILL_HORIZONTAL));
        timeoutTextField = new JBTextField();
        mainPanel.add(timeoutTextField, createConstraint(row, 1, GridConstraints.FILL_HORIZONTAL));
        row++;

        // Max Scenarios
        mainPanel.add(new JBLabel("Max Scenarios:"), createConstraint(row, 0, GridConstraints.FILL_HORIZONTAL));
        maxScenariosTextField = new JBTextField();
        mainPanel.add(maxScenariosTextField, createConstraint(row, 1, GridConstraints.FILL_HORIZONTAL));
        row++;

        // === General Checkboxes ===
        showPreviewCheckBox = new JBCheckBox("Show preview dialog before generation");
        mainPanel.add(showPreviewCheckBox, createConstraint(row, 0, 2, GridConstraints.FILL_HORIZONTAL));
        row++;

        autoFormatCheckBox = new JBCheckBox("Auto-format generated code");
        mainPanel.add(autoFormatCheckBox, createConstraint(row, 0, 2, GridConstraints.FILL_HORIZONTAL));
        row++;

        enableValidationCheckBox = new JBCheckBox("Enable compilation check");
        mainPanel.add(enableValidationCheckBox, createConstraint(row, 0, 2, GridConstraints.FILL_HORIZONTAL));
        row++;

        savePromptHistoryCheckBox = new JBCheckBox("Save prompt history");
        mainPanel.add(savePromptHistoryCheckBox, createConstraint(row, 0, 2, GridConstraints.FILL_HORIZONTAL));
        row++;

        // === Self-correction Settings ===
        correctUntilSuccessCheckBox = new JBCheckBox("Correct until success");
        mainPanel.add(correctUntilSuccessCheckBox, createConstraint(row, 0, 2, GridConstraints.FILL_HORIZONTAL));
        row++;

        mainPanel.add(new JBLabel("Max correction attempts:"), createConstraint(row, 0, GridConstraints.FILL_HORIZONTAL));
        maxCorrectionAttemptsField = new JBTextField();
        mainPanel.add(maxCorrectionAttemptsField, createConstraint(row, 1, GridConstraints.FILL_HORIZONTAL));
        row++;

        // === GigaChat Settings ===
        row = addGigaChatSection(row);

        // === Source Code Settings ===
        includeSourceCodeCheckBox = new JBCheckBox("Include dependency source code");
        mainPanel.add(includeSourceCodeCheckBox, createConstraint(row, 0, 2, GridConstraints.FILL_HORIZONTAL));
        row++;

        includeMethodSigsCheckBox = new JBCheckBox("Include method signatures");
        mainPanel.add(includeMethodSigsCheckBox, createConstraint(row, 0, 2, GridConstraints.FILL_HORIZONTAL));
        row++;

        mainPanel.add(new JBLabel("Max dependency code length:"), createConstraint(row, 0, GridConstraints.FILL_HORIZONTAL));
        maxCodeLengthField = new JBTextField();
        mainPanel.add(maxCodeLengthField, createConstraint(row, 1, GridConstraints.FILL_HORIZONTAL));
        row++;

        // === Deep Analysis Settings ===
        mainPanel.add(new JBLabel("Analysis depth:"), createConstraint(row, 0, GridConstraints.FILL_HORIZONTAL));
        analysisDepthField = new JBTextField();
        mainPanel.add(analysisDepthField, createConstraint(row, 1, GridConstraints.FILL_HORIZONTAL));
        row++;

        mainPanel.add(new JBLabel("Implementation search depth:"), createConstraint(row, 0, GridConstraints.FILL_HORIZONTAL));
        implementationSearchDepthField = new JBTextField();
        mainPanel.add(implementationSearchDepthField, createConstraint(row, 1, GridConstraints.FILL_HORIZONTAL));
        row++;

        includeCalledMethodsCheckBox = new JBCheckBox("Include called methods");
        mainPanel.add(includeCalledMethodsCheckBox, createConstraint(row, 0, 2, GridConstraints.FILL_HORIZONTAL));
        row++;

        includeDTOStructuresCheckBox = new JBCheckBox("Include DTO structures");
        mainPanel.add(includeDTOStructuresCheckBox, createConstraint(row, 0, 2, GridConstraints.FILL_HORIZONTAL));
        row++;

        includeDataTransformationsCheckBox = new JBCheckBox("Include data transformations");
        mainPanel.add(includeDataTransformationsCheckBox, createConstraint(row, 0, 2, GridConstraints.FILL_HORIZONTAL));
        row++;

        // === SSL/JKS Settings ===
        useSSLCheckBox = new JBCheckBox("Use SSL with JKS keystore");
        mainPanel.add(useSSLCheckBox, createConstraint(row, 0, 2, GridConstraints.FILL_HORIZONTAL));
        row++;

        mainPanel.add(new JBLabel("KeyStore Type:"), createConstraint(row, 0, GridConstraints.FILL_HORIZONTAL));
        sslKeyStoreTypeComboBox = new ComboBox<>(new String[]{"JKS", "PKCS12", "BKS"});
        mainPanel.add(sslKeyStoreTypeComboBox, createConstraint(row, 1, GridConstraints.FILL_HORIZONTAL));
        row++;

        mainPanel.add(new JBLabel("KeyStore Path:"), createConstraint(row, 0, GridConstraints.FILL_HORIZONTAL));
        sslKeyStorePathPanel = new JPanel(new BorderLayout(5, 0));
        sslKeyStorePathField = new JBTextField();
        sslKeyStorePathButton = new JButton("...");
        sslKeyStorePathButton.setPreferredSize(new Dimension(40, sslKeyStorePathField.getPreferredSize().height));
        sslKeyStorePathPanel.add(sslKeyStorePathField, BorderLayout.CENTER);
        sslKeyStorePathPanel.add(sslKeyStorePathButton, BorderLayout.EAST);
        sslKeyStorePathButton.addActionListener(e -> openKeyStoreFileChooser());
        mainPanel.add(sslKeyStorePathPanel, createConstraint(row, 1, GridConstraints.FILL_HORIZONTAL));
        row++;

        mainPanel.add(new JBLabel("KeyStore Password:"), createConstraint(row, 0, GridConstraints.FILL_HORIZONTAL));
        sslKeyStorePasswordField = new JPasswordField();
        mainPanel.add(sslKeyStorePasswordField, createConstraint(row, 1, GridConstraints.FILL_HORIZONTAL));
        row++;

        // Add listener for provider type changes to show/hide GigaChat fields
        providerTypeComboBox.addActionListener(e -> updateGigaChatVisibility());

        // Add listener for SSL checkbox to enable/disable SSL fields
        useSSLCheckBox.addActionListener(e -> updateSSLFieldsVisibility());

        // Initial visibility setup
        updateGigaChatVisibility();
        updateSSLFieldsVisibility();

        return mainPanel;
    }

    private int addGigaChatSection(int startRow) {
        int row = startRow;

        // GigaChat section header
        gigaChatPanel = new JPanel();
        gigaChatPanel.setLayout(new GridLayoutManager(4, 2, new Insets(0, 0, 0, 0), 5, 5));

        // Auth Method
        gigaChatPanel.add(new JBLabel("Auth Method:"), createConstraint(0, 0, GridConstraints.FILL_HORIZONTAL));
        gigaChatAuthMethodComboBox = new ComboBox<>(GigaChatProvider.AuthMethod.values());
        gigaChatPanel.add(gigaChatAuthMethodComboBox, createConstraint(0, 1, GridConstraints.FILL_HORIZONTAL));

        // Client ID
        gigaChatClientIdLabel = new JBLabel("Client ID:");
        gigaChatPanel.add(gigaChatClientIdLabel, createConstraint(1, 0, GridConstraints.FILL_HORIZONTAL));
        gigaChatClientIdField = new JBTextField();
        gigaChatPanel.add(gigaChatClientIdField, createConstraint(1, 1, GridConstraints.FILL_HORIZONTAL));

        // Client Secret
        gigaChatClientSecretLabel = new JBLabel("Client Secret:");
        gigaChatPanel.add(gigaChatClientSecretLabel, createConstraint(2, 0, GridConstraints.FILL_HORIZONTAL));
        gigaChatClientSecretField = new JPasswordField();
        gigaChatPanel.add(gigaChatClientSecretField, createConstraint(2, 1, GridConstraints.FILL_HORIZONTAL));

        // Scope
        gigaChatPanel.add(new JBLabel("Scope:"), createConstraint(3, 0, GridConstraints.FILL_HORIZONTAL));
        gigaChatScopeField = new JBTextField();
        gigaChatPanel.add(gigaChatScopeField, createConstraint(3, 1, GridConstraints.FILL_HORIZONTAL));

        // Add the GigaChat panel to the main panel spanning 2 columns
        GridConstraints gc = new GridConstraints();
        gc.setRow(row);
        gc.setColumn(0);
        gc.setRowSpan(1);
        gc.setColSpan(2);
        gc.setAnchor(GridConstraints.ANCHOR_CENTER);
        gc.setFill(GridConstraints.FILL_BOTH);
        gc.setHSizePolicy(GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW);
        gc.setVSizePolicy(GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW);
        mainPanel.add(gigaChatPanel, gc);

        // Return the number of rows used (4 rows for the GigaChat panel + 1 for the panel itself)
        return row + 5;
    }

    private void updateGigaChatVisibility() {
        boolean isGigaChat = providerTypeComboBox.getSelectedItem() == LLMProviderType.GIGACHAT;
        gigaChatPanel.setVisible(isGigaChat);

        if (isGigaChat) {
            updateGigaChatFieldsVisibility();
        }
    }

    private void updateGigaChatFieldsVisibility() {
        GigaChatProvider.AuthMethod method = (GigaChatProvider.AuthMethod) gigaChatAuthMethodComboBox.getSelectedItem();
        boolean isApiKey = method == GigaChatProvider.AuthMethod.API_KEY;

        // For API_KEY: show Client ID and Client Secret
        // For CERTIFICATE: show Client ID and Client Secret (certificate-based)
        gigaChatClientIdLabel.setVisible(true);
        gigaChatClientIdField.setVisible(true);
        gigaChatClientSecretLabel.setVisible(true);
        gigaChatClientSecretField.setVisible(true);
    }

    private void updateSSLFieldsVisibility() {
        boolean useSSL = useSSLCheckBox.isSelected();
        sslKeyStorePathField.setEnabled(useSSL);
        sslKeyStorePathButton.setEnabled(useSSL);
        sslKeyStorePasswordField.setEnabled(useSSL);
        sslKeyStoreTypeComboBox.setEnabled(useSSL);
    }

    private void openKeyStoreFileChooser() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Select JKS Keystore File");
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fileChooser.setFileFilter(new FileFilter() {
            @Override
            public boolean accept(File f) {
                if (f.isDirectory()) return true;
                String name = f.getName().toLowerCase();
                return name.endsWith(".jks") || name.endsWith(".pkcs12") || name.endsWith(".p12") || name.endsWith(".bks");
            }

            @Override
            public String getDescription() {
                return "Keystore Files (*.jks, *.pkcs12, *.bks)";
            }
        });

        String currentPath = sslKeyStorePathField.getText();
        if (currentPath != null && !currentPath.isEmpty()) {
            File currentFile = new File(currentPath);
            if (currentFile.exists()) {
                fileChooser.setCurrentDirectory(currentFile.getParentFile());
                fileChooser.setSelectedFile(currentFile);
            }
        }

        int result = fileChooser.showOpenDialog(mainPanel);
        if (result == JFileChooser.APPROVE_OPTION) {
            sslKeyStorePathField.setText(fileChooser.getSelectedFile().getAbsolutePath());
        }
    }

    private GridConstraints createConstraint(int row, int col, int fill) {
        return createConstraint(row, col, 1, fill);
    }

    private GridConstraints createConstraint(int row, int col, int colSpan, int fill) {
        GridConstraints c = new GridConstraints();
        c.setRow(row);
        c.setColumn(col);
        c.setRowSpan(1);
        c.setColSpan(colSpan);
        c.setAnchor(GridConstraints.ANCHOR_WEST);
        c.setFill(fill);
        c.setHSizePolicy(GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW);
        c.setVSizePolicy(GridConstraints.SIZEPOLICY_FIXED);
        return c;
    }

    @Override
    public boolean isModified() {
        if (settings == null) return false;

        PluginSettings.State defaults = new PluginSettings.State();

        String currentEndpoint = nullOrEmpty(endpointTextField.getText()) ? defaults.endpoint : endpointTextField.getText();
        String settingsEndpoint = nullOrEmpty(settings.getEndpoint()) ? defaults.endpoint : settings.getEndpoint();

        String currentApiKey = new String(apiKeyPasswordField.getPassword());
        String settingsApiKey = nullOrEmpty(settings.getApiKey()) ? defaults.apiKey : settings.getApiKey();

        String currentModel = nullOrEmpty(modelTextField.getText()) ? defaults.model : modelTextField.getText();
        String settingsModel = nullOrEmpty(settings.getModel()) ? defaults.model : settings.getModel();

        String currentKeystorePath = nullOrEmpty(sslKeyStorePathField.getText()) ? defaults.keystorePath : sslKeyStorePathField.getText();
        String settingsKeystorePath = nullOrEmpty(settings.getSslKeyStorePath()) ? defaults.keystorePath : settings.getSslKeyStorePath();

        String currentKeystorePassword = new String(sslKeyStorePasswordField.getPassword());
        String settingsKeystorePassword = nullOrEmpty(settings.getSslKeyStorePassword()) ? defaults.keystorePassword : settings.getSslKeyStorePassword();

        String currentClientId = nullOrEmpty(gigaChatClientIdField.getText()) ? defaults.gigachatClientId : gigaChatClientIdField.getText();
        String settingsClientId = nullOrEmpty(settings.getGigachatClientId()) ? defaults.gigachatClientId : settings.getGigachatClientId();

        String currentClientSecret = new String(gigaChatClientSecretField.getPassword());
        String settingsClientSecret = nullOrEmpty(settings.getGigachatClientSecret()) ? defaults.gigachatClientSecret : settings.getGigachatClientSecret();

        String currentScope = nullOrEmpty(gigaChatScopeField.getText()) ? defaults.gigachatScope : gigaChatScopeField.getText();
        String settingsScope = nullOrEmpty(settings.getGigachatScope()) ? defaults.gigachatScope : settings.getGigachatScope();

        String currentKeystoreType = (String) sslKeyStoreTypeComboBox.getSelectedItem();
        String settingsKeystoreType = nullOrEmpty(settings.getKeystoreType()) ? defaults.keystoreType : settings.getKeystoreType();

        return !Objects.equals(settings.getProviderType(), providerTypeComboBox.getSelectedItem()) ||
               !Objects.equals(settingsEndpoint, currentEndpoint) ||
               !Objects.equals(settingsApiKey, currentApiKey) ||
               !Objects.equals(settingsModel, currentModel) ||
               settings.getTimeout() != parseInt(timeoutTextField.getText(), defaults.timeout) ||
               settings.getMaxScenarios() != parseInt(maxScenariosTextField.getText(), defaults.maxScenarios) ||
               settings.isShowPreview() != showPreviewCheckBox.isSelected() ||
               settings.isAutoFormat() != autoFormatCheckBox.isSelected() ||
               settings.isEnableValidation() != enableValidationCheckBox.isSelected() ||
               settings.isSavePromptHistory() != savePromptHistoryCheckBox.isSelected() ||
               settings.isCorrectUntilSuccess() != correctUntilSuccessCheckBox.isSelected() ||
               settings.getMaxCorrectionAttempts() != parseInt(maxCorrectionAttemptsField.getText(), defaults.maxCorrectionAttempts) ||
               settings.isIncludeDependencySourceCode() != includeSourceCodeCheckBox.isSelected() ||
               settings.isIncludeMethodSignatures() != includeMethodSigsCheckBox.isSelected() ||
               settings.getMaxDependencyCodeLength() != parseInt(maxCodeLengthField.getText(), defaults.maxDependencyCodeLength) ||
               settings.getAnalysisDepth() != parseInt(analysisDepthField.getText(), defaults.analysisDepth) ||
               settings.getImplementationSearchDepth() != parseInt(implementationSearchDepthField.getText(), defaults.implementationSearchDepth) ||
               settings.isIncludeCalledMethods() != includeCalledMethodsCheckBox.isSelected() ||
               settings.isIncludeDTOStructures() != includeDTOStructuresCheckBox.isSelected() ||
               settings.isIncludeDataTransformations() != includeDataTransformationsCheckBox.isSelected() ||
               settings.isUseSSL() != useSSLCheckBox.isSelected() ||
               !Objects.equals(settingsKeystoreType, currentKeystoreType) ||
               !Objects.equals(settingsKeystorePath, currentKeystorePath) ||
               !Objects.equals(settingsKeystorePassword, currentKeystorePassword) ||
               !Objects.equals(settings.getGigachatAuthMethod(), gigaChatAuthMethodComboBox.getSelectedItem()) ||
               !Objects.equals(settingsClientId, currentClientId) ||
               !Objects.equals(settingsClientSecret, currentClientSecret) ||
               !Objects.equals(settingsScope, currentScope);
    }

    private boolean nullOrEmpty(String s) {
        return s == null || s.isEmpty();
    }

    @Override
    public void apply() {
        if (settings == null) {
            settings = PluginSettings.getInstance();
        }

        settings.setProviderType((LLMProviderType) providerTypeComboBox.getSelectedItem());
        settings.setEndpoint(endpointTextField.getText());
        settings.setApiKey(new String(apiKeyPasswordField.getPassword()));
        settings.setModel(modelTextField.getText());
        settings.setTimeout(parseInt(timeoutTextField.getText(), 120));
        settings.setMaxScenarios(parseInt(maxScenariosTextField.getText(), 10));
        settings.setShowPreview(showPreviewCheckBox.isSelected());
        settings.setAutoFormat(autoFormatCheckBox.isSelected());
        settings.setEnableValidation(enableValidationCheckBox.isSelected());
        settings.setSavePromptHistory(savePromptHistoryCheckBox.isSelected());
        settings.setCorrectUntilSuccess(correctUntilSuccessCheckBox.isSelected());
        settings.setMaxCorrectionAttempts(parseInt(maxCorrectionAttemptsField.getText(), 3));
        settings.setIncludeDependencySourceCode(includeSourceCodeCheckBox.isSelected());
        settings.setIncludeMethodSignatures(includeMethodSigsCheckBox.isSelected());
        settings.setMaxDependencyCodeLength(parseInt(maxCodeLengthField.getText(), 2000));
        settings.setAnalysisDepth(parseInt(analysisDepthField.getText(), 2));
        settings.setImplementationSearchDepth(parseInt(implementationSearchDepthField.getText(), 3));
        settings.setIncludeCalledMethods(includeCalledMethodsCheckBox.isSelected());
        settings.setIncludeDTOStructures(includeDTOStructuresCheckBox.isSelected());
        settings.setIncludeDataTransformations(includeDataTransformationsCheckBox.isSelected());
        settings.setUseSSL(useSSLCheckBox.isSelected());
        settings.setKeystoreType((String) sslKeyStoreTypeComboBox.getSelectedItem());
        settings.setSslKeyStorePath(sslKeyStorePathField.getText());
        settings.setSslKeyStorePassword(new String(sslKeyStorePasswordField.getPassword()));
        settings.setGigachatAuthMethod((GigaChatProvider.AuthMethod) gigaChatAuthMethodComboBox.getSelectedItem());
        settings.setGigachatClientId(gigaChatClientIdField.getText());
        settings.setGigachatClientSecret(new String(gigaChatClientSecretField.getPassword()));
        settings.setGigachatScope(gigaChatScopeField.getText());
    }

    @Override
    public void reset() {
        if (settings == null) {
            settings = PluginSettings.getInstance();
        }

        PluginSettings.State defaults = new PluginSettings.State();

        providerTypeComboBox.setSelectedItem(settings.getProviderType() != null ? settings.getProviderType() : defaults.providerType);
        endpointTextField.setText(nullOrEmpty(settings.getEndpoint()) ? defaults.endpoint : settings.getEndpoint());
        apiKeyPasswordField.setText(nullOrEmpty(settings.getApiKey()) ? defaults.apiKey : settings.getApiKey());
        modelTextField.setText(nullOrEmpty(settings.getModel()) ? defaults.model : settings.getModel());
        timeoutTextField.setText(String.valueOf(settings.getTimeout()));
        maxScenariosTextField.setText(String.valueOf(settings.getMaxScenarios()));
        showPreviewCheckBox.setSelected(settings.isShowPreview());
        autoFormatCheckBox.setSelected(settings.isAutoFormat());
        enableValidationCheckBox.setSelected(settings.isEnableValidation());
        savePromptHistoryCheckBox.setSelected(settings.isSavePromptHistory());
        correctUntilSuccessCheckBox.setSelected(settings.isCorrectUntilSuccess());
        maxCorrectionAttemptsField.setText(String.valueOf(settings.getMaxCorrectionAttempts()));
        includeSourceCodeCheckBox.setSelected(settings.isIncludeDependencySourceCode());
        includeMethodSigsCheckBox.setSelected(settings.isIncludeMethodSignatures());
        maxCodeLengthField.setText(String.valueOf(settings.getMaxDependencyCodeLength()));
        analysisDepthField.setText(String.valueOf(settings.getAnalysisDepth()));
        implementationSearchDepthField.setText(String.valueOf(settings.getImplementationSearchDepth()));
        includeCalledMethodsCheckBox.setSelected(settings.isIncludeCalledMethods());
        includeDTOStructuresCheckBox.setSelected(settings.isIncludeDTOStructures());
        includeDataTransformationsCheckBox.setSelected(settings.isIncludeDataTransformations());
        useSSLCheckBox.setSelected(settings.isUseSSL());
        sslKeyStoreTypeComboBox.setSelectedItem(nullOrEmpty(settings.getKeystoreType()) ? defaults.keystoreType : settings.getKeystoreType());
        sslKeyStorePathField.setText(nullOrEmpty(settings.getSslKeyStorePath()) ? defaults.keystorePath : settings.getSslKeyStorePath());
        sslKeyStorePasswordField.setText(nullOrEmpty(settings.getSslKeyStorePassword()) ? defaults.keystorePassword : settings.getSslKeyStorePassword());
        gigaChatAuthMethodComboBox.setSelectedItem(settings.getGigachatAuthMethod());
        gigaChatClientIdField.setText(nullOrEmpty(settings.getGigachatClientId()) ? defaults.gigachatClientId : settings.getGigachatClientId());
        gigaChatClientSecretField.setText(nullOrEmpty(settings.getGigachatClientSecret()) ? defaults.gigachatClientSecret : settings.getGigachatClientSecret());
        gigaChatScopeField.setText(nullOrEmpty(settings.getGigachatScope()) ? defaults.gigachatScope : settings.getGigachatScope());

        updateGigaChatVisibility();
        updateSSLFieldsVisibility();
    }

    @Override
    public void disposeUIResources() {
        mainPanel = null;
        providerTypeComboBox = null;
        endpointTextField = null;
        apiKeyPasswordField = null;
        modelTextField = null;
        timeoutTextField = null;
        maxScenariosTextField = null;
        showPreviewCheckBox = null;
        autoFormatCheckBox = null;
        enableValidationCheckBox = null;
        savePromptHistoryCheckBox = null;
        correctUntilSuccessCheckBox = null;
        maxCorrectionAttemptsField = null;
        gigaChatPanel = null;
        gigaChatAuthMethodComboBox = null;
        gigaChatClientIdField = null;
        gigaChatClientSecretField = null;
        gigaChatScopeField = null;
        includeSourceCodeCheckBox = null;
        includeMethodSigsCheckBox = null;
        maxCodeLengthField = null;
        analysisDepthField = null;
        implementationSearchDepthField = null;
        includeCalledMethodsCheckBox = null;
        includeDTOStructuresCheckBox = null;
        includeDataTransformationsCheckBox = null;
        useSSLCheckBox = null;
        sslKeyStorePathPanel = null;
        sslKeyStorePathField = null;
        sslKeyStorePathButton = null;
        sslKeyStorePasswordField = null;
        sslKeyStoreTypeComboBox = null;
    }

    private int parseInt(String text, int defaultValue) {
        if (text == null || text.trim().isEmpty()) return defaultValue;
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
