package ru.sbrf.uddk.ai.testing.lsprag.llm.gigachat;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.intellij.openapi.diagnostic.Logger;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.util.EntityUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.sbrf.uddk.ai.testing.lsprag.LspragSettingsState;
import ru.sbrf.uddk.ai.testing.lsprag.llm.ClientUtils;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

public class GigaChatTokenManager {

    private static final Logger LOG = Logger.getInstance(GigaChatTokenManager.class);

    private final GigaChatConfig config;
    private final HttpClient httpClient;
    private final Gson gson;

    @Nullable
    private String accessToken;
    @Nullable
    private Instant expiresAt;
    private final Object lock = new Object();
    private final LspragSettingsState settings;

    public GigaChatTokenManager(@NotNull GigaChatConfig config, @NotNull LspragSettingsState settings) {
        this.config = config;
        this.httpClient = ClientUtils.createHttpClient(settings);
        this.gson = new Gson();
        this.settings = settings;
    }

    /**
     * Получает актуальный токен доступа (обновляет если истёк)
     */
    @NotNull
    public String getAccessToken() throws IOException, GigaChatException {
        synchronized (lock) {
            String token = settings.getApiToken();
            return token;
//            if (accessToken == null || isTokenExpired()) {
//                refreshToken();
//            }
//            if (accessToken == null) {
//                throw new GigaChatException("Failed to obtain access token", null);
//            }
//            return accessToken;
        }
    }

    /**
     * Проверяет, истёк ли токен (с запасом 2 минуты)
     */
    private boolean isTokenExpired() {
        if (expiresAt == null) {
            return true;
        }
        // Обновляем за 2 минуты до истечения
        return Instant.now().plusSeconds(120).isAfter(expiresAt);
    }

    /**
     * Запрашивает новый токен доступа
     */
    private void refreshToken() throws IOException, GigaChatException {
        if (!config.hasCredentials()) {
            throw new GigaChatException("Client ID or Client Secret not configured", null);
        }

        String rqUID = config.generateRqUID();
        String authKey = config.getAuthorizationKey();

        // Формируем запрос к OAuth endpoint
        String requestBody = "scope=" + config.getScope();
        byte[] bodyBytes = requestBody.getBytes(StandardCharsets.UTF_8);

        HttpPost request = new HttpPost(URI.create(config.getOauthEndpoint()));
        request.setHeader("Content-Type", "application/x-www-form-urlencoded");
        request.setHeader("Accept", "application/json");
        request.setHeader("RqUID", rqUID);
        request.setHeader("Authorization", "Basic " + authKey);
        request.setEntity(new ByteArrayEntity(bodyBytes));

        HttpResponse response = httpClient.execute(request);

        LOG.debug(String.format("GigaChat OAuth response status: %d", response.getStatusLine().getStatusCode()));

        if (response.getStatusLine().getStatusCode() != 200) {
            handleOAuthErrorResponse(response);
        }

        // Парсим ответ
        TokenResponse tokenResponse = gson.fromJson(EntityUtils.toString(response.getEntity()), TokenResponse.class);

        if (tokenResponse == null || tokenResponse.accessToken == null) {
            throw new GigaChatException("Invalid token response from GigaChat", null);
        }

        this.accessToken = tokenResponse.accessToken;

        // Устанавливаем время истечения (expires_at в миллисекундах)
        if (tokenResponse.expiresAt != null) {
            this.expiresAt = Instant.ofEpochMilli(tokenResponse.expiresAt);
            LOG.info(String.format("GigaChat token obtained, expires at: %s", expiresAt));
        } else {
            // Если expires_at не возвращён, считаем что токен действует 30 минут
            this.expiresAt = Instant.now().plusSeconds(1800);
            LOG.info("GigaChat token obtained (default 30 min expiry)");
        }

    }

    /**
     * Обрабатывает ошибки OAuth запроса
     */
    private void handleOAuthErrorResponse(@NotNull HttpResponse response)
            throws GigaChatException, IOException {

        String body = EntityUtils.toString(response.getEntity());
        String errorMessage = switch (response.getStatusLine().getStatusCode()) {
            case 400 -> "Bad Request: Invalid parameters";
            case 401 -> "Unauthorized: Invalid Client ID or Client Secret";
            case 403 -> "Forbidden: Access denied";
            case 429 -> "Rate Limit: Too many token requests (max 10/sec)";
            case 500 -> "Internal Server Error: GigaChat service issue";
            default -> "HTTP Error " + response.getStatusLine().getStatusCode();
        };

        LOG.error(String.format("GigaChat OAuth error %d: %s", response.getStatusLine().getStatusCode(), body));
        throw new GigaChatException(errorMessage + " - " + body, null);
    }

    /**
     * Принудительно обновляет токен
     */
    public void forceRefresh() throws IOException, GigaChatException {
        synchronized (lock) {
            this.accessToken = null;
            this.expiresAt = null;
            refreshToken();
        }
    }

    /**
     * Проверяет доступность сервиса
     */
    public boolean isAvailable() {
        try {
            getAccessToken();
            return true;
        } catch (Exception e) {
            LOG.warn("GigaChat availability check failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Ответ OAuth API
     */
    private static class TokenResponse {
        @SerializedName("access_token")
        String accessToken;

        @SerializedName("expires_at")
        Long expiresAt;
    }

    /**
     * Исключение GigaChat
     */
    public static class GigaChatException extends Exception {
        public GigaChatException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}