package com.reasoningtestgen.settings;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPasswordField;
import com.intellij.ui.components.JBTextField;
import com.reasoningtestgen.llm.GigaChatProvider;
import com.reasoningtestgen.llm.LLMProviderType;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Settings UI configurable
 * According to ANALYTICS.md Section 9 - Settings Page
 */
public class PluginSettingsConfigurable implements Configurable {

    private PluginSettings settings;
    
    // UI Components
    private JPanel mainPanel;
    private ComboBox<LLMProviderType> providerTypeComboBox;
    private JTextField endpointTextField;
    private JPasswordField apiKeyPasswordField;
    private JTextField modelTextField;
    private JTextField timeoutTextField;
    private JTextField maxScenariosTextField;
    private JBCheckBox showPreviewCheckBox;
    private JBCheckBox autoFormatCheckBox;
    private JBCheckBox enableValidationCheckBox;
    
    // Source code inclusion UI
    private JBCheckBox includeSourceCodeCheckBox;
    private JBCheckBox includeMethodSigsCheckBox;
    private JTextField maxCodeLengthField;
    private JBCheckBox savePromptHistoryCheckBox;
    
    // GigaChat components
    private JPanel gigachatPanel;
    private ComboBox<GigaChatProvider.AuthMethod> gigachatAuthComboBox;
    private JTextField gigachatClientIdField;
    private JPasswordField gigachatClientSecretField;
    private JTextField gigachatScopeField;
    private JTextField keystorePathField;
    private JPasswordField keystorePasswordField;
    private ComboBox<String> keystoreTypeComboBox;

    @Nls(capitalization = Nls.Capitalization.Title)
    @Override
    public String getDisplayName() {
        return "Reasoning Test Generator";
    }

    @Nullable
    @Override
    public JComponent createComponent() {
        settings = PluginSettings.getInstance();
        
        mainPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.gridx = 0;
        gbc.gridy = 0;

        // Provider Type
        gbc.weightx = 0;
        mainPanel.add(new JBLabel("LLM Provider:"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        providerTypeComboBox = new ComboBox<>(LLMProviderType.values());
        mainPanel.add(providerTypeComboBox, gbc);
        gbc.gridx = 0;
        gbc.gridy++;

        // Endpoint
        gbc.weightx = 0;
        mainPanel.add(new JBLabel("API Endpoint:"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        endpointTextField = new JBTextField();
        mainPanel.add(endpointTextField, gbc);
        gbc.gridx = 0;
        gbc.gridy++;

        // API Key
        gbc.weightx = 0;
        mainPanel.add(new JBLabel("API Key:"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        apiKeyPasswordField = new JPasswordField();
        mainPanel.add(apiKeyPasswordField, gbc);
        gbc.gridx = 0;
        gbc.gridy++;

        // Model
        gbc.weightx = 0;
        mainPanel.add(new JBLabel("Model:"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        modelTextField = new JBTextField();
        mainPanel.add(modelTextField, gbc);
        gbc.gridx = 0;
        gbc.gridy++;

        // Timeout
        gbc.weightx = 0;
        mainPanel.add(new JBLabel("Timeout (seconds):"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        timeoutTextField = new JBTextField();
        mainPanel.add(timeoutTextField, gbc);
        gbc.gridx = 0;
        gbc.gridy++;

        // Separator
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        mainPanel.add(new JSeparator(), gbc);
        gbc.gridy++;

        // Max Scenarios
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        mainPanel.add(new JBLabel("Max Scenarios:"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        maxScenariosTextField = new JBTextField();
        mainPanel.add(maxScenariosTextField, gbc);
        gbc.gridx = 0;
        gbc.gridy++;

        // Show Preview
        gbc.gridwidth = 2;
        showPreviewCheckBox = new JBCheckBox("Show preview before insertion");
        mainPanel.add(showPreviewCheckBox, gbc);
        gbc.gridy++;

        // Auto Format
        autoFormatCheckBox = new JBCheckBox("Auto-format after insertion");
        mainPanel.add(autoFormatCheckBox, gbc);
        gbc.gridy++;

        // Enable Validation
        enableValidationCheckBox = new JBCheckBox("Enable compilation check");
        mainPanel.add(enableValidationCheckBox, gbc);
        gbc.gridy++;
        
        // Separator
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        mainPanel.add(new JSeparator(), gbc);
        gbc.gridy++;
        
        // Source Code Inclusion Section
        gbc.gridwidth = 2;
        mainPanel.add(new JBLabel("<html><b>Dependency Source Code Inclusion</b></html>"), gbc);
        gbc.gridy++;
        
        gbc.gridwidth = 2;
        includeSourceCodeCheckBox = new JBCheckBox("Include dependency source code in prompts");
        mainPanel.add(includeSourceCodeCheckBox, gbc);
        gbc.gridy++;
        
        gbc.gridwidth = 2;
        includeMethodSigsCheckBox = new JBCheckBox("Include method signatures (enabled by default)");
        includeMethodSigsCheckBox.setSelected(true);
        mainPanel.add(includeMethodSigsCheckBox, gbc);
        gbc.gridy++;
        
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        mainPanel.add(new JBLabel("Max code length (chars):"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        maxCodeLengthField = new JBTextField("2000");
        mainPanel.add(maxCodeLengthField, gbc);
        gbc.gridy++;

        // Add listener to provider type change
        providerTypeComboBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                updateEndpointVisibility();
            }
        });

        return mainPanel;
    }

    /**
     * Update endpoint field visibility based on provider type
     */
    private void updateEndpointVisibility() {
        LLMProviderType selected = (LLMProviderType) providerTypeComboBox.getSelectedItem();
        boolean showEndpoint = selected == LLMProviderType.CUSTOM || 
                              selected == LLMProviderType.OPENAI;
        endpointTextField.setEnabled(showEndpoint);
    }

    @Override
    public boolean isModified() {
        if (settings == null) {
            return false;
        }
        
        return !settings.getProviderType().equals(providerTypeComboBox.getSelectedItem()) ||
               !settings.getEndpoint().equals(endpointTextField.getText()) ||
               !settings.getApiKey().equals(new String(apiKeyPasswordField.getPassword())) ||
               !settings.getModel().equals(modelTextField.getText()) ||
               settings.getTimeout() != parseInt(timeoutTextField.getText(), 30) ||
               settings.getMaxScenarios() != parseInt(maxScenariosTextField.getText(), 10) ||
               settings.isShowPreview() != showPreviewCheckBox.isSelected() ||
               settings.isAutoFormat() != autoFormatCheckBox.isSelected() ||
               settings.isEnableValidation() != enableValidationCheckBox.isSelected();
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
        settings.setTimeout(parseInt(timeoutTextField.getText(), 30));
        settings.setMaxScenarios(parseInt(maxScenariosTextField.getText(), 10));
        settings.setShowPreview(showPreviewCheckBox.isSelected());
        settings.setAutoFormat(autoFormatCheckBox.isSelected());
        settings.setEnableValidation(enableValidationCheckBox.isSelected());
        settings.setIncludeDependencySourceCode(includeSourceCodeCheckBox.isSelected());
        settings.setIncludeMethodSignatures(includeMethodSigsCheckBox.isSelected());
        settings.setMaxDependencyCodeLength(parseInt(maxCodeLengthField.getText(), 2000));
    }

    @Override
    public void reset() {
        if (settings == null) {
            return;
        }
        
        providerTypeComboBox.setSelectedItem(settings.getProviderType());
        endpointTextField.setText(settings.getEndpoint());
        apiKeyPasswordField.setText(settings.getApiKey());
        modelTextField.setText(settings.getModel());
        timeoutTextField.setText(String.valueOf(settings.getTimeout()));
        maxScenariosTextField.setText(String.valueOf(settings.getMaxScenarios()));
        showPreviewCheckBox.setSelected(settings.isShowPreview());
        autoFormatCheckBox.setSelected(settings.isAutoFormat());
        enableValidationCheckBox.setSelected(settings.isEnableValidation());
        includeSourceCodeCheckBox.setSelected(settings.isIncludeDependencySourceCode());
        includeMethodSigsCheckBox.setSelected(settings.isIncludeMethodSignatures());
        maxCodeLengthField.setText(String.valueOf(settings.getMaxDependencyCodeLength()));

        updateEndpointVisibility();
    }

    @Override
    public void disposeUIResources() {
        mainPanel = null;
        settings = null;
    }

    /**
     * Parse integer with fallback
     */
    private int parseInt(@Nullable String text, int defaultValue) {
        if (text == null || text.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
