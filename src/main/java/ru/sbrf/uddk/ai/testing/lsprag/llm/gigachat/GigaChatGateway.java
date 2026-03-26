package ru.sbrf.uddk.ai.testing.lsprag.llm.gigachat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.intellij.openapi.diagnostic.Logger;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.util.EntityUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.sbrf.uddk.ai.testing.lsprag.LspragSettingsState;
import ru.sbrf.uddk.ai.testing.lsprag.llm.ClientUtils;
import ru.sbrf.uddk.ai.testing.lsprag.llm.LLMGateway;
import ru.sbrf.uddk.ai.testing.lsprag.utils.LLMResponseParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

public class GigaChatGateway implements LLMGateway {

    private static final Logger LOG = Logger.getInstance(GigaChatGateway.class);

    private final GigaChatConfig config;
    private final GigaChatTokenManager tokenManager;
    private final HttpClient httpClient;
    private final Gson gson;

    public GigaChatGateway(@NotNull GigaChatConfig config, LspragSettingsState settings) {
        this.config = config;
        this.tokenManager = new GigaChatTokenManager(config, settings);
        this.httpClient = ClientUtils.createHttpClient(settings);
        this.gson = new GsonBuilder()
                .setPrettyPrinting()
                .create();
    }

    @Override
    public String generate(String prompt) throws IOException, LLMException {
        return generate(prompt, null);
    }

    /**
     * Генерирует ответ от GigaChat с поддержкой системного промпта
     */
    public String generate(@NotNull String prompt, @Nullable String systemPrompt)
            throws IOException, LLMException {

        // Получаем токен доступа (автоматически обновляет если истёк)
        String accessToken;
        try {
            accessToken = tokenManager.getAccessToken();
        } catch (GigaChatTokenManager.GigaChatException e) {
            throw new LLMException("Failed to get GigaChat access token: " + e.getMessage(), e);
        }

        // Создаём запрос
        GigaChatRequest request = createRequest(prompt, systemPrompt);

        // Выполняем запрос с повторными попытками
        return executeWithRetries(request, accessToken);
    }

    /**
     * Создаёт запрос к GigaChat API
     */
    @NotNull
    private GigaChatRequest createRequest(@NotNull String prompt, @Nullable String systemPrompt) {
        GigaChatRequest request = new GigaChatRequest(
                config.getModel().getModelName(),
                prompt
        );

        // Добавляем системный промпт если есть
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            request.addSystemMessage(systemPrompt);
        }

        request.setTemperature(config.getTemperature());
        request.setMaxTokens(config.getMaxTokens());
        request.setTopP(1.0);
        request.setRepetitionPenalty(1.0);
        request.setStream(false);

        return request;
    }

    /**
     * Выполняет запрос с повторными попытками
     */
    @NotNull
    private String executeWithRetries(@NotNull GigaChatRequest request, @NotNull String accessToken)
            throws IOException, LLMException {

        IOException lastException = null;

        for (int attempt = 1; attempt <= config.getMaxRetries(); attempt++) {
            try {
                String response = executeRequest(request, accessToken);

                LOG.info(String.format("GigaChat request successful (attempt %d)", attempt));

                return response;

            } catch (IOException e) {
                lastException = e;
                LOG.warn(String.format("GigaChat request failed (attempt %d/%d): %s",
                        attempt, config.getMaxRetries(), e.getMessage()));

                // Проверяем, не истёк ли токен
                if (e.getMessage() != null && e.getMessage().contains("401")) {
                    try {
                        tokenManager.forceRefresh();
                        accessToken = tokenManager.getAccessToken();
                    } catch (GigaChatTokenManager.GigaChatException ex) {
                        throw new LLMException("Token refresh failed", ex);
                    }
                }

                // Ждём перед следующей попыткой (exponential backoff)
                if (attempt < config.getMaxRetries()) {
                    try {
                        long delay = TimeUnit.SECONDS.toMillis((long) Math.pow(2, attempt - 1));
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new LLMException("Request interrupted", ie);
                    }
                }
            }
        }

        throw new LLMException(
                "Failed after " + config.getMaxRetries() + " attempts: " +
                        (lastException != null ? lastException.getMessage() : "Unknown error"),
                lastException);
    }

    /**
     * Выполняет HTTP запрос к GigaChat API
     */
    @NotNull
    private String executeRequest(@NotNull GigaChatRequest request, @NotNull String accessToken)
            throws IOException, LLMException {

        String requestBody = gson.toJson(request);
        byte[] bodyBytes = requestBody.getBytes(StandardCharsets.UTF_8);

        // Создаём HTTP запрос
        HttpPost httpRequest = new HttpPost(config.getCompletionsUrl());
        httpRequest.setHeader("Content-Type", "application/json; charset=UTF-8");
        httpRequest.setHeader("Accept", "application/json; charset=UTF-8");
        if (!StringUtils.isBlank(accessToken)) {
            httpRequest.setHeader("Authorization", "Bearer " + accessToken);
        }
        httpRequest.setHeader("X-Request-ID", config.generateRqUID());
        httpRequest.setEntity(new ByteArrayEntity(bodyBytes));

        // Отправляем запрос с UTF-8 декодированием
        HttpResponse response = httpClient.execute(httpRequest);

        LOG.debug(String.format("GigaChat API response status: %d", response.getStatusLine().getStatusCode()));

        // Обрабатываем ошибки
        if (response.getStatusLine().getStatusCode() != 200) {
            handleErrorResponse(response);
        }

        // Парсим ответ
        GigaChatResponse gigaResponse = gson.fromJson(EntityUtils.toString(response.getEntity()), GigaChatResponse.class);

        // Проверяем на ошибки в теле ответа
        if (gigaResponse.hasError()) {
            throw new LLMException(
                    "GigaChat API error: " + gigaResponse.getError().getMessage(),
                    null);
        }

        // Извлекаем текст ответа
        String responseText = gigaResponse.getResponseText();

        // Очищаем от markdown и лишнего текста
        String cleanCode = LLMResponseParser.extractJavaCode(responseText);

        // Логируем использование токенов
        if (gigaResponse.getUsage() != null) {
            LOG.info(String.format("Token usage: %s", gigaResponse.getUsage().toString()));
        }

        return cleanCode;

    }

    /**
     * Обрабатывает ошибки HTTP ответа
     */
    private void handleErrorResponse(@NotNull HttpResponse response)
            throws LLMException, IOException {

        String body = EntityUtils.toString(response.getEntity());
        String errorMessage = switch (response.getStatusLine().getStatusCode()) {
            case 400 -> "Bad Request: Invalid parameters";
            case 401 -> "Unauthorized: Invalid or expired access token";
            case 403 -> "Forbidden: Access denied or pay-as-you-go required";
            case 404 -> "Not Found: Invalid model";
            case 422 -> "Validation Error: Check parameters (possibly context too large)";
            case 429 -> "Rate Limit: Too many requests";
            case 500 -> "Internal Server Error: GigaChat service issue";
            case 502 -> "Bad Gateway: GigaChat service unavailable";
            case 503 -> "Service Unavailable: GigaChat is overloaded";
            default -> "HTTP Error " + response.getStatusLine().getStatusCode();
        };

        // Пробуем распарсить ошибку из тела ответа
        try {
            GigaChatResponse.Error error = gson.fromJson(body, GigaChatResponse.class).getError();
            if (error != null && error.getMessage() != null) {
                errorMessage += " - " + error.getMessage();
            }
        } catch (Exception e) {
            // Игнорируем ошибки парсинга
        }

        LOG.error(String.format("GigaChat API error %d: %s", response.getStatusLine().getStatusCode(), body));
        throw new LLMException(errorMessage, null);
    }

    @Override
    public boolean isAvailable() {
        try {
            return tokenManager.isAvailable();
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String getModelName() {
        return config.getModel().getModelName();
    }

    /**
     * Получает текущую конфигурацию
     */
    @NotNull
    public GigaChatConfig getConfig() {
        return config;
    }

    /**
     * Получает менеджер токенов
     */
    @NotNull
    public GigaChatTokenManager getTokenManager() {
        return tokenManager;
    }
}
