package com.reasoningtestgen.settings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.reasoningtestgen.llm.GigaChatProvider;
import com.reasoningtestgen.llm.LLMProviderType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Plugin settings persistent state
 * According to ANALYTICS.md Section 9 - Settings
 */
@State(
    name = "com.reasoningtestgen.settings.PluginSettings",
    storages = @Storage("ReasoningTestGenerator.xml")
)
public class PluginSettings implements PersistentStateComponent<PluginSettings.State> {

    private State state = new State();

    public static PluginSettings getInstance() {
        return ApplicationManager.getApplication().getService(PluginSettings.class);
    }

    @Nullable
    @Override
    public State getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull State state) {
        this.state = state;
    }

    // Getters
    public LLMProviderType getProviderType() {
        return state.providerType;
    }

    public String getEndpoint() {
        return state.endpoint;
    }

    public String getApiKey() {
        return state.apiKey;
    }

    public String getModel() {
        return state.model;
    }

    public int getTimeout() {
        return state.timeout;
    }

    public int getMaxScenarios() {
        return state.maxScenarios;
    }

    public boolean isShowPreview() {
        return state.showPreview;
    }

    public boolean isAutoFormat() {
        return state.autoFormat;
    }

    public boolean isEnableValidation() {
        return state.enableValidation;
    }
    
    public boolean isSavePromptHistory() {
        return state.savePromptHistory;
    }
    
    // Self-correction settings
    public int getMaxCorrectionAttempts() {
        return state.maxCorrectionAttempts;
    }
    
    public boolean isCorrectUntilSuccess() {
        return state.correctUntilSuccess;
    }
    
    // GigaChat-specific settings
    @NotNull
    public GigaChatProvider.AuthMethod getGigachatAuthMethod() {
        try {
            return GigaChatProvider.AuthMethod.valueOf(state.gigachatAuthMethod);
        } catch (Exception e) {
            return GigaChatProvider.AuthMethod.API_KEY;
        }
    }
    
    @NotNull
    public String getGigachatClientId() {
        return state.gigachatClientId != null ? state.gigachatClientId : "";
    }
    
    @NotNull
    public String getGigachatClientSecret() {
        return state.gigachatClientSecret != null ? state.gigachatClientSecret : "";
    }
    
    @NotNull
    public String getGigachatScope() {
        return state.gigachatScope != null ? state.gigachatScope : "GIGACHAT_API_PERS";
    }
    
    @NotNull
    public String getKeystorePath() {
        return state.keystorePath != null ? state.keystorePath : "";
    }
    
    @NotNull
    public String getKeystorePassword() {
        return state.keystorePassword != null ? state.keystorePassword : "";
    }
    
    @NotNull
    public String getKeystoreType() {
        return state.keystoreType != null ? state.keystoreType : "JKS";
    }

    // Setters
    public void setProviderType(LLMProviderType providerType) {
        state.providerType = providerType;
    }

    public void setEndpoint(String endpoint) {
        state.endpoint = endpoint;
    }

    public void setApiKey(String apiKey) {
        state.apiKey = apiKey;
    }

    public void setModel(String model) {
        state.model = model;
    }

    public void setTimeout(int timeout) {
        state.timeout = timeout;
    }

    public void setMaxScenarios(int maxScenarios) {
        state.maxScenarios = maxScenarios;
    }

    public void setShowPreview(boolean showPreview) {
        state.showPreview = showPreview;
    }

    public void setAutoFormat(boolean autoFormat) {
        state.autoFormat = autoFormat;
    }

    public void setEnableValidation(boolean enableValidation) {
        state.enableValidation = enableValidation;
    }
    
    public void setSavePromptHistory(boolean savePromptHistory) {
        state.savePromptHistory = savePromptHistory;
    }
    
    public void setMaxCorrectionAttempts(int maxAttempts) {
        state.maxCorrectionAttempts = maxAttempts;
    }
    
    public void setCorrectUntilSuccess(boolean correctUntilSuccess) {
        state.correctUntilSuccess = correctUntilSuccess;
    }
    
    public void setGigachatAuthMethod(GigaChatProvider.AuthMethod method) {
        state.gigachatAuthMethod = method.name();
    }
    
    public void setGigachatClientId(String clientId) {
        state.gigachatClientId = clientId;
    }
    
    public void setGigachatClientSecret(String clientSecret) {
        state.gigachatClientSecret = clientSecret;
    }
    
    public void setGigachatScope(String scope) {
        state.gigachatScope = scope;
    }
    
    public void setKeystorePath(String path) {
        state.keystorePath = path;
    }
    
    public void setKeystorePassword(String password) {
        state.keystorePassword = password;
    }
    
    public void setKeystoreType(String type) {
        state.keystoreType = type;
    }
    
    // Source code inclusion settings
    public boolean isIncludeDependencySourceCode() {
        return state.includeDependencySourceCode;
    }
    
    public void setIncludeDependencySourceCode(boolean include) {
        state.includeDependencySourceCode = include;
    }
    
    public boolean isIncludeMethodSignatures() {
        return state.includeMethodSignatures;
    }
    
    public void setIncludeMethodSignatures(boolean include) {
        state.includeMethodSignatures = include;
    }
    
    public int getMaxDependencyCodeLength() {
        return state.maxDependencyCodeLength;
    }
    
    public void setMaxDependencyCodeLength(int length) {
        state.maxDependencyCodeLength = length;
    }
    
    // Deep analysis settings (REQ 1-3, 6)
    public int getAnalysisDepth() {
        return state.analysisDepth;
    }
    
    public void setAnalysisDepth(int depth) {
        state.analysisDepth = depth;
    }
    
    public int getImplementationSearchDepth() {
        return state.implementationSearchDepth;
    }
    
    public void setImplementationSearchDepth(int depth) {
        state.implementationSearchDepth = depth;
    }
    
    public boolean isIncludeCalledMethods() {
        return state.includeCalledMethods;
    }
    
    public void setIncludeCalledMethods(boolean include) {
        state.includeCalledMethods = include;
    }
    
    public boolean isIncludeDTOStructures() {
        return state.includeDTOStructures;
    }
    
    public void setIncludeDTOStructures(boolean include) {
        state.includeDTOStructures = include;
    }
    
    public boolean isIncludeDataTransformations() {
        return state.includeDataTransformations;
    }
    
    public void setIncludeDataTransformations(boolean include) {
        state.includeDataTransformations = include;
    }

    /**
     * Persistent state storage
     */
    public static class State {
        public LLMProviderType providerType = LLMProviderType.LM_STUDIO;
        public String endpoint = "http://localhost:1234/v1/chat/completions";
        public String apiKey = "";
        public String model = "qwen/qwen3.5-9b";
        public int timeout = 120;
        public int maxScenarios = 10;
        public boolean showPreview = true;
        public boolean autoFormat = true;
        public boolean enableValidation = true;
        public boolean savePromptHistory = true;
        
        // Self-correction settings
        public int maxCorrectionAttempts = 3;
        public boolean correctUntilSuccess = false;
        
        // GigaChat settings
        public String gigachatAuthMethod = "API_KEY";
        public String gigachatClientId = "";
        public String gigachatClientSecret = "";
        public String gigachatScope = "GIGACHAT_API_PERS";
        public String keystorePath = "";
        public String keystorePassword = "";
        public String keystoreType = "JKS";
        
        // Source code inclusion settings
        public boolean includeDependencySourceCode = false;
        public boolean includeMethodSignatures = true;
        public int maxDependencyCodeLength = 2000;
        
        // Deep analysis settings (REQ 1-3, 6)
        public int analysisDepth = 2;
        public int implementationSearchDepth = 3;
        public boolean includeCalledMethods = true;
        public boolean includeDTOStructures = true;
        public boolean includeDataTransformations = true;
    }
}
