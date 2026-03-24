package ru.sbrf.uddk.ai.testing.lsprag;

import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.credentialStore.Credentials;
import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.components.fields.IntegerField;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;
import ru.sbrf.uddk.ai.testing.lsprag.llm.LLMAdapterFactory;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.util.Objects;

public class SettingsConfigurable implements Configurable {

    private JPanel mainPanel;

    // LLM Settings
    private JComboBox<String> llmTypeCombo;
    private JBTextField endpointField;
    private JBTextField modelField;
    private JPasswordField apiKeyField;
    private JSlider temperatureSlider;
    private IntegerField maxTokensField;

    // Keystore Settings
    private TextFieldWithBrowseButton keystorePathField;
    private JPasswordField keystorePasswordField;
    private JCheckBox useKeystoreCheck;

    // Analysis Settings
    private IntegerField contextDepthField;
    private IntegerField maxRepairField;
    private JCheckBox useLLMPlanningCheck;

    // Output Settings
    private JBTextField outputDirField;

    // Status
    private JBLabel connectionStatus;
    private JButton testConnectionButton;

    private final LspragSettingsState settings;

    // Флаги изменений паролей
    private boolean keystorePasswordModified = false;
    private boolean apiKeyModified = false;

    public SettingsConfigurable() {
        this.settings = LspragSettingsState.getInstance();
    }

    @Nls(capitalization = Nls.Capitalization.Title)
    @Override
    public String getDisplayName() {
        return "Генератор API Тестов";
    }

    @Nullable
    @Override
    public JComponent createComponent() {
        mainPanel = new JPanel(new BorderLayout());

        // === LLM Configuration ===
        FormBuilder llmForm = FormBuilder.createFormBuilder();
        llmForm.addLabeledComponent("Провайдер LLM:", createLLMTypeCombo());
        llmForm.addComponent(new JBLabel("URL:"));
        llmForm.addComponent(endpointField = new JBTextField(), 1);
        llmForm.addComponent(new JBLabel("Имя модели:"));
        llmForm.addComponent(modelField = new JBTextField(), 1);

        llmForm.addComponent(new JBLabel("API ключ (сохраняется в безопасности):"));
        apiKeyField = new JPasswordField(20);  // ✅ Инициализация поля
        llmForm.addComponent(apiKeyField, 1);

        llmForm.addLabeledComponent("Температура:", createTemperatureSlider());
        llmForm.addLabeledComponent("Максимальное количество токенов:",
                maxTokensField = new IntegerField("maxTokens", 1, 128000));

        // Test Connection
        JPanel connectionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        testConnectionButton = new JButton("Проверка подключения");
        testConnectionButton.addActionListener(e -> testConnection());
        connectionStatus = new JBLabel("");
        connectionPanel.add(testConnectionButton);
        connectionPanel.add(connectionStatus);
        llmForm.addComponent(connectionPanel);

        // === Keystore Configuration ===
        FormBuilder keystoreForm = FormBuilder.createFormBuilder();

        useKeystoreCheck = new JCheckBox("Использовать Keystore для подписи тестов");
        useKeystoreCheck.addActionListener(e -> updateKeystoreFieldsEnabled());
        keystoreForm.addComponent(useKeystoreCheck);

        JPanel keystorePathPanel = new JPanel(new BorderLayout(5, 0));
        keystorePathField = new TextFieldWithBrowseButton();  // ✅ Инициализация поля
        keystorePathPanel.add(keystorePathField, BorderLayout.CENTER);
        setupKeystoreFileChooser();

        keystoreForm.addLabeledComponent("Путь к Keystore файлу:", keystorePathPanel);
        keystorePasswordField = new JPasswordField(20);  // ✅ Инициализация поля
        keystoreForm.addLabeledComponent("Пароль Keystore:", keystorePasswordField);

        // === Analysis Settings ===
        FormBuilder analysisForm = FormBuilder.createFormBuilder();
        analysisForm.addLabeledComponent("Глубина контекста (вызовы методов):",
                contextDepthField = new IntegerField("contextDepth", 0, 15));
        analysisForm.addLabeledComponent("Количество попыток исправить код:",
                maxRepairField = new IntegerField("Max repair", 1, 10));
        useLLMPlanningCheck = new JCheckBox("Использовать LLM планирование тест-кейсов (медленнее, но результат лучше)");
        analysisForm.addComponent(useLLMPlanningCheck);

        // === Сборка панели ===
        JPanel content = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = JBUI.insetsBottom(10);

        content.add(createSectionPanel("Конфигурация LLM", llmForm.getPanel()), gbc);
        gbc.gridy++;
        content.add(createSectionPanel("Keystore настройки", keystoreForm.getPanel()), gbc);
        gbc.gridy++;
        content.add(createSectionPanel("Анализ кода", analysisForm.getPanel()), gbc);
        gbc.gridy++;

        mainPanel.add(content, BorderLayout.NORTH);

        // ✅ === ВАЖНО: Инициализация слушателей ПОСЛЕ создания полей ===
        initPasswordListeners();

        return mainPanel;
    }

    /**
     * Инициализирует слушатели изменений паролей
     * Вызывать ТОЛЬКО после инициализации полей apiKeyField и keystorePasswordField
     */
    private void initPasswordListeners() {
        // Слушатель для keystore password
        if (keystorePasswordField != null) {
            keystorePasswordField.getDocument().addDocumentListener(new DocumentListener() {
                @Override
                public void insertUpdate(DocumentEvent e) { onKeystorePasswordChanged(); }
                @Override
                public void removeUpdate(DocumentEvent e) { onKeystorePasswordChanged(); }
                @Override
                public void changedUpdate(DocumentEvent e) { onKeystorePasswordChanged(); }

                private void onKeystorePasswordChanged() {
                    String text = new String(keystorePasswordField.getPassword());
                    // Считаем изменённым если текст не пустой и не заглушка
                    keystorePasswordModified = !text.isEmpty() && !text.equals("••••••••");
                }
            });
        }

        // Слушатель для API key
        if (apiKeyField != null) {
            apiKeyField.getDocument().addDocumentListener(new DocumentListener() {
                @Override
                public void insertUpdate(DocumentEvent e) { onApiKeyChanged(); }
                @Override
                public void removeUpdate(DocumentEvent e) { onApiKeyChanged(); }
                @Override
                public void changedUpdate(DocumentEvent e) { onApiKeyChanged(); }

                private void onApiKeyChanged() {
                    String text = new String(apiKeyField.getPassword());
                    apiKeyModified = !text.isEmpty() && !text.equals("••••••••");
                }
            });
        }
    }

    private void setupKeystoreFileChooser() {
        FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFileDescriptor();
        descriptor.setTitle("Выберите Keystore Файл");
        descriptor.setDescription("Выберите файл Keystore (.jks, .keystore, .p12)");

        keystorePathField.addBrowseFolderListener(
                "Выбор Keystore Файла",
                "Выберите файл Keystore для подписи тестов",
                null,
                descriptor
        );
    }

    private JPanel createSectionPanel(String title, JComponent content) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(IdeBorderFactory.createTitledBorder(title));
        panel.add(content, BorderLayout.CENTER);
        return panel;
    }

    private JComboBox<String> createLLMTypeCombo() {
        llmTypeCombo = new ComboBox<>(new String[]{
                "LM Studio", "GigaChat", "Ollama", "OpenAI"
        });
        llmTypeCombo.setToolTipText("Выберите провайдера LLM");
        return llmTypeCombo;
    }

    private JSlider createTemperatureSlider() {
        temperatureSlider = new JSlider(0, 100, 20);
        temperatureSlider.setMajorTickSpacing(20);
        temperatureSlider.setPaintTicks(true);
        temperatureSlider.setPaintLabels(true);
        temperatureSlider.setToolTipText("0 = детерминированный, 100 = креативный");
        return temperatureSlider;
    }

    private void updateKeystoreFieldsEnabled() {
        boolean enabled = useKeystoreCheck.isSelected();
        keystorePathField.setEnabled(enabled);
        keystorePasswordField.setEnabled(enabled);
    }

    private void testConnection() {
        connectionStatus.setText("Тестирование...");
        connectionStatus.setForeground(UIUtil.getContextHelpForeground());

        String llmType = (String) llmTypeCombo.getSelectedItem();
        String endpoint = endpointField.getText();
        String model = modelField.getText();
        double temperature = temperatureSlider.getValue() / 100.0;

        String originalType = settings.getLlmType();
        String originalEndpoint = settings.getLlmEndpoint();
        String originalModel = settings.getLlmModel();
        double originalTemp = settings.getTemperature();

        try {
            settings.setLlmType(llmType);
            settings.setLlmEndpoint(endpoint);
            settings.setLlmModel(model);
            settings.setTemperature(temperature);

            if (apiKeyField.getPassword() != null && apiKeyField.getPassword().length > 0) {
                String key = new String(apiKeyField.getPassword());
                CredentialAttributes attrs = new CredentialAttributes("lsprag.openai.apikey");
                PasswordSafe.getInstance().set(attrs, new Credentials("", key));
            }

            boolean available = LLMAdapterFactory.isLLMAvailable(settings);

            connectionStatus.setText(available ? "✓ Успех" : "✗ Ошибка подключения");
            connectionStatus.setForeground(available ?
                    UIUtil.getContextHelpForeground().darker() :
                    JBUI.CurrentTheme.Link.Foreground.ENABLED);

            if (!available) {
                Messages.showErrorDialog(
                        "Не удалось подключиться к LLM. Проверьте:\n" +
                                "- URL endpoint\n" +
                                "- Доступность сервиса (LM Studio/Ollama запущен?)\n" +
                                "- API ключ (если требуется)",
                        "Ошибка подключения"
                );
            }
        } finally {
            settings.setLlmType(originalType);
            settings.setLlmEndpoint(originalEndpoint);
            settings.setLlmModel(originalModel);
            settings.setTemperature(originalTemp);
        }
    }

    @Override
    public boolean isModified() {
        LspragSettingsState current = LspragSettingsState.getInstance();

        return !Objects.equals(current.getLlmType(), llmTypeCombo.getSelectedItem()) ||
                !Objects.equals(current.getLlmEndpoint(), endpointField.getText()) ||
                !Objects.equals(current.getLlmModel(), modelField.getText()) ||
                Math.abs(current.getTemperature() - (temperatureSlider.getValue() / 100.0)) > 0.01 ||
                current.getMaxTokens() != maxTokensField.getValue() ||
                current.getContextDepth() != contextDepthField.getValue() ||
                current.getMaxRepairAttempts() != maxRepairField.getValue() ||
                current.isUseLLMForTestPlanning() != useLLMPlanningCheck.isSelected() ||
                current.isUseKeystore() != useKeystoreCheck.isSelected() ||
                !Objects.equals(current.getKeystorePath(), keystorePathField.getText()) ||
                keystorePasswordModified ||
                apiKeyModified;
    }

    @Override
    public void apply() {
        LspragSettingsState current = LspragSettingsState.getInstance();

        current.setLlmType((String) llmTypeCombo.getSelectedItem());
        current.setLlmEndpoint(endpointField.getText());
        current.setLlmModel(modelField.getText());
        current.setTemperature(temperatureSlider.getValue() / 100.0);
        current.setMaxTokens(maxTokensField.getValue());
        current.setContextDepth(contextDepthField.getValue());
        current.setMaxRepairAttempts(maxRepairField.getValue());
        current.setUseLLMForTestPlanning(useLLMPlanningCheck.isSelected());
        current.setUseKeystore(useKeystoreCheck.isSelected());
        current.setKeystorePath(keystorePathField.getText());

        // Сохраняем пароли только если были изменены
        if (keystorePasswordModified && keystorePasswordField.getPassword() != null
                && keystorePasswordField.getPassword().length > 0) {
            String password = new String(keystorePasswordField.getPassword());
            CredentialAttributes attrs = new CredentialAttributes("lsprag.keystore.password");
            PasswordSafe.getInstance().set(attrs, new Credentials("", password));
            keystorePasswordModified = false;
        }

        if (apiKeyModified && apiKeyField.getPassword() != null
                && apiKeyField.getPassword().length > 0) {
            String key = new String(apiKeyField.getPassword());
            CredentialAttributes attrs = new CredentialAttributes("lsprag.openai.apikey");
            PasswordSafe.getInstance().set(attrs, new Credentials("", key));
            apiKeyModified = false;
        }
    }

    @Override
    public void reset() {
        LspragSettingsState current = LspragSettingsState.getInstance();

        llmTypeCombo.setSelectedItem(current.getLlmType());
        endpointField.setText(current.getLlmEndpoint());
        modelField.setText(current.getLlmModel());
        temperatureSlider.setValue((int) Math.round(current.getTemperature() * 100));
        maxTokensField.setValue(current.getMaxTokens());
        contextDepthField.setValue(current.getContextDepth());
        maxRepairField.setValue(current.getMaxRepairAttempts());
        useLLMPlanningCheck.setSelected(current.isUseLLMForTestPlanning());
        useKeystoreCheck.setSelected(current.isUseKeystore());
        keystorePathField.setText(current.getKeystorePath());
        updateKeystoreFieldsEnabled();

        // Загружаем пароли из PasswordSafe
        CredentialAttributes keystoreAttrs = new CredentialAttributes("lsprag.keystore.password");
        Credentials keystoreCreds = PasswordSafe.getInstance().get(keystoreAttrs);
        boolean hasKeystorePassword = keystoreCreds != null && keystoreCreds.getPasswordAsString() != null;
        keystorePasswordField.setText(hasKeystorePassword ? "••••••••" : "");

        CredentialAttributes apiAttrs = new CredentialAttributes("lsprag.openai.apikey");
        Credentials apiCreds = PasswordSafe.getInstance().get(apiAttrs);
        boolean hasApiKey = apiCreds != null && apiCreds.getPasswordAsString() != null;
        apiKeyField.setText(hasApiKey ? "••••••••" : "");

        // Сбрасываем флаги
        keystorePasswordModified = false;
        apiKeyModified = false;

        connectionStatus.setText("");
    }

    @Override
    public void disposeUIResources() {
        mainPanel = null;
    }
}