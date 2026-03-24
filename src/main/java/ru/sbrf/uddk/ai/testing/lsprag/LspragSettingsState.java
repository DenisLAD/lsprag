package ru.sbrf.uddk.ai.testing.lsprag;

import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.credentialStore.Credentials;
import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@State(name = "LspragSettings", storages = @Storage("lsprag.xml"))
public class LspragSettingsState implements PersistentStateComponent<LspragSettingsState> {

    // LLM settings
    private String llmType = "LM Studio";
    private String llmEndpoint = "http://localhost:1234/v1";
    private String llmModel = "default";
    private double temperature = 0.2;
    private int maxTokens = 4096;

    // Analysis settings
    private int contextDepth = 1;
    private int maxRepairAttempts = 3;
    private boolean useLLMForTestPlanning = false;

    // Keystore settings
    private boolean useKeystore = false;
    private String keystorePath = "";

    // Output settings
    private String testOutputDir = "src/test/java";

    public static LspragSettingsState getInstance() {
        return ApplicationManager.getApplication()
                .getService(LspragSettingsState.class);
    }

    @Nullable
    @Override
    public LspragSettingsState getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull LspragSettingsState state) {
        this.llmType = state.llmType;
        this.llmEndpoint = state.llmEndpoint;
        this.llmModel = state.llmModel;
        this.temperature = state.temperature;
        this.maxTokens = state.maxTokens;
        this.contextDepth = state.contextDepth;
        this.maxRepairAttempts = state.maxRepairAttempts;
        this.useLLMForTestPlanning = state.useLLMForTestPlanning;
        this.testOutputDir = state.testOutputDir;
        // === ИСПРАВЛЕНИЕ: загружаем keystore настройки ===
        this.useKeystore = state.useKeystore;
        this.keystorePath = state.keystorePath != null ? state.keystorePath : "";
    }

    // === LLM Getters/Setters ===
    public String getLlmType() {
        return llmType;
    }

    public void setLlmType(String llmType) {
        this.llmType = llmType;
    }

    public String getLlmEndpoint() {
        return llmEndpoint;
    }

    public void setLlmEndpoint(String llmEndpoint) {
        this.llmEndpoint = llmEndpoint;
    }

    public String getLlmModel() {
        return llmModel;
    }

    public void setLlmModel(String llmModel) {
        this.llmModel = llmModel;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = Math.max(0.0, Math.min(2.0, temperature));
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(int maxTokens) {
        this.maxTokens = Math.max(1, Math.min(128000, maxTokens));
    }

    // === Analysis Getters/Setters ===
    public int getContextDepth() {
        return contextDepth;
    }

    public void setContextDepth(int contextDepth) {
        this.contextDepth = Math.max(0, Math.min(15, contextDepth));
    }

    public int getMaxRepairAttempts() {
        return maxRepairAttempts;
    }

    public void setMaxRepairAttempts(int maxRepairAttempts) {
        this.maxRepairAttempts = Math.max(1, Math.min(10, maxRepairAttempts));
    }

    public boolean isUseLLMForTestPlanning() {
        return useLLMForTestPlanning;
    }

    public void setUseLLMForTestPlanning(boolean use) {
        this.useLLMForTestPlanning = use;
    }

    // === Output Getters/Setters ===
    public String getTestOutputDir() {
        return testOutputDir;
    }

    public void setTestOutputDir(String testOutputDir) {
        this.testOutputDir = testOutputDir;
    }

    // === Keystore Getters/Setters ===
    public boolean isUseKeystore() {
        return useKeystore;
    }

    public void setUseKeystore(boolean useKeystore) {
        this.useKeystore = useKeystore;
    }

    public String getKeystorePath() {
        return keystorePath != null ? keystorePath : "";
    }

    public void setKeystorePath(String keystorePath) {
        this.keystorePath = keystorePath != null ? keystorePath : "";
    }

    public String getApiToken() {
        CredentialAttributes clientIdAttrs = new CredentialAttributes("lsprag.openai.apikey");
        Credentials clientIdCreds = PasswordSafe.getInstance().get(clientIdAttrs);
        return clientIdCreds != null ? clientIdCreds.getPasswordAsString() : null;
    }
}