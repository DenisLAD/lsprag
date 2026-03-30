package ru.sbrf.uddk.ai.testing.lsprag.llm.gigachat;

import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.credentialStore.Credentials;
import com.intellij.ide.passwordSafe.PasswordSafe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Base64;

public class GigaChatConfig {

    private static final String CREDENTIAL_CLIENT_ID = "lsprag.gigachat.clientid";
    private static final String CREDENTIAL_CLIENT_SECRET = "lsprag.gigachat.clientsecret";

    // OAuth endpoint для получения токена
    private static final String OAUTH_ENDPOINT = "https://ngw.devices.sberbank.ru:9443/api/v2/oauth";

    // API endpoint для запросов к моделям
    private static final String API_ENDPOINT = "https://gigachat.devices.sberbank.ru/api/v1";

    private String clientId;
    private String clientSecret;
    private String clientToken;
    private String scope;
    private GigaChatModel model;
    private double temperature;
    private int maxTokens;
    private int timeoutSeconds;
    private int maxRetries;
    private String rqUID;

    public GigaChatConfig() {
        this.scope = "GIGACHAT_API_PERS"; // По умолчанию для физ. лиц
        this.model = GigaChatModel.GIGACHAT_2_PRO;
        this.temperature = 0.2;
        this.maxTokens = 4096;
        this.timeoutSeconds = 60;
        this.maxRetries = 3;
        loadCredentials();
    }

    /**
     * Загружает Client ID и Client Secret из PasswordSafe
     */
    private void loadCredentials() {
        CredentialAttributes clientIdAttrs = new CredentialAttributes(CREDENTIAL_CLIENT_ID);
        Credentials clientIdCreds = PasswordSafe.getInstance().get(clientIdAttrs);
        this.clientId = clientIdCreds != null ? clientIdCreds.getPasswordAsString() : null;

        CredentialAttributes clientSecretAttrs = new CredentialAttributes(CREDENTIAL_CLIENT_SECRET);
        Credentials clientSecretCreds = PasswordSafe.getInstance().get(clientSecretAttrs);
        this.clientSecret = clientSecretCreds != null ? clientSecretCreds.getPasswordAsString() : null;
    }

    /**
     * Сохраняет Client ID в PasswordSafe
     */
    public void saveClientId(@NotNull String clientId) {
        CredentialAttributes attrs = new CredentialAttributes(CREDENTIAL_CLIENT_ID);
        PasswordSafe.getInstance().set(attrs, new Credentials("", clientId));
        this.clientId = clientId;
    }

    /**
     * Сохраняет Client Secret в PasswordSafe
     */
    public void saveClientSecret(@NotNull String clientSecret) {
        CredentialAttributes attrs = new CredentialAttributes(CREDENTIAL_CLIENT_SECRET);
        PasswordSafe.getInstance().set(attrs, new Credentials("", clientSecret));
        this.clientSecret = clientSecret;
    }

    /**
     * Проверяет наличие credentials
     */
    public boolean hasCredentials() {
        return clientId != null && !clientId.trim().isEmpty() &&
                clientSecret != null && !clientSecret.trim().isEmpty();
    }

    /**
     * Генерирует Basic Auth ключ для OAuth запроса
     * Authorization key = Base64(Client ID + ":" + Client Secret)
     */
    @NotNull
    public String getAuthorizationKey() {
        if (!hasCredentials()) {
            throw new IllegalStateException("Client ID or Client Secret not configured");
        }
        String credentials = clientId + ":" + clientSecret;
        return Base64.getEncoder().encodeToString(credentials.getBytes());
    }

    /**
     * Генерирует уникальный RqUID для запроса (UUID v4)
     */
    @NotNull
    public String generateRqUID() {
        this.rqUID = java.util.UUID.randomUUID().toString();
        return this.rqUID;
    }

    @Nullable
    public String getClientId() { return clientId; }
    @Nullable
    public String getClientSecret() { return clientSecret; }

    @NotNull
    public String getScope() { return scope; }
    public void setScope(@NotNull String scope) {
        this.scope = scope;
    }

    @NotNull
    public GigaChatModel getModel() { return model; }
    public void setModel(@NotNull GigaChatModel model) { this.model = model; }

    public void setModelByName(@NotNull String modelName) {
        this.model = GigaChatModel.fromName(modelName);
    }

    public double getTemperature() { return temperature; }
    public void setTemperature(double temperature) {
        this.temperature = Math.max(0.0, Math.min(2.0, temperature));
    }

    public int getMaxTokens() { return maxTokens; }
    public void setMaxTokens(int maxTokens) {
        this.maxTokens = Math.max(1, Math.min(model.getMaxTokens(), maxTokens));
    }

    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = Math.max(10, timeoutSeconds);
    }

    public int getMaxRetries() { return maxRetries; }
    public void setMaxRetries(int maxRetries) {
        this.maxRetries = Math.max(0, Math.min(5, maxRetries));
    }

    @NotNull
    public String getOauthEndpoint() { return OAUTH_ENDPOINT; }
    @NotNull
    public String getApiEndpoint() { return API_ENDPOINT; }

    @NotNull
    public String getCompletionsUrl() {
        return "/chat/completions";
    }

    @NotNull
    public String getModelsUrl() {
        return API_ENDPOINT + "/models";
    }

    @NotNull
    public String getTokensCountUrl() {
        return API_ENDPOINT + "/tokens/count";
    }
}
