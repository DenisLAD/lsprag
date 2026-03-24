package ru.sbrf.uddk.ai.testing.lsprag.llm;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.credentialStore.Credentials;
import com.intellij.ide.passwordSafe.PasswordSafe;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.util.EntityUtils;
import org.jetbrains.annotations.NotNull;
import ru.sbrf.uddk.ai.testing.lsprag.LspragSettingsState;

import javax.net.ssl.SSLContext;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.logging.Level;
import java.util.logging.Logger;

import static ru.sbrf.uddk.ai.testing.lsprag.llm.ClientUtils.createHttpClient;

public class LocalModelGateway implements LLMGateway {

    private static final Logger LOGGER = Logger.getLogger(LocalModelGateway.class.getName());

    private final LspragSettingsState settings;
    private final HttpClient httpClient;
    private final Gson gson;

    public LocalModelGateway(LspragSettingsState settings) {
        this.settings = settings;
        this.httpClient = createHttpClient(settings);
        this.gson = new GsonBuilder().create();
    }




    @Override
    public String generate(String prompt) throws IOException, LLMException {
        String endpoint = settings.getLlmEndpoint(); // предполагается, что эндпоинт уже содержит базовый URL, например http://localhost:1234
        String model = settings.getLlmModel();       // идентификатор модели, используемой в LM Studio

        // Формируем тело запроса в формате Chat Completion API
        JsonObject requestBody = getJsonObject(prompt);

        // Дополнительные параметры можно добавить по необходимости
        // requestBody.addProperty("top_p", settings.getTopP());

        HttpPost request = new HttpPost(URI.create(endpoint + "/v1/chat/completions"));
        request.setHeader("Content-Type", "application/json; charset=UTF-8");
        request.setHeader("Accept", "application/json; charset=UTF-8");
        request.setEntity(new StringEntity(gson.toJson(requestBody), StandardCharsets.UTF_8));

        System.out.println("Sending request to LM Studio: " + prompt);

        try {
            HttpResponse response = httpClient.execute(request);

            int statusCode = response.getStatusLine().getStatusCode();
            String responseBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);

            if (statusCode != 200) {
                LOGGER.severe("LM Studio API error: status " + statusCode + ", body: " + responseBody);
                throw new LLMException("LM Studio API error (HTTP " + statusCode + "): " + responseBody, null);
            }

            JsonObject result = gson.fromJson(responseBody, JsonObject.class);

            // Извлекаем ответ модели из choices[0].message.content
            if (result.has("choices") && result.getAsJsonArray("choices").size() > 0) {
                JsonObject firstChoice = result.getAsJsonArray("choices").get(0).getAsJsonObject();
                if (firstChoice.has("message")) {
                    JsonObject message = firstChoice.getAsJsonObject("message");
                    if (message.has("content")) {
                        String content = message.get("content").getAsString();
                        System.out.println("Received response: " + content);
                        return content;
                    }
                }
            }

            // Если структура не соответствует ожидаемой, выбрасываем исключение
            LOGGER.severe("Unexpected response structure: " + responseBody);
            throw new LLMException("Unexpected response from LM Studio: " + responseBody, null);

        } catch (IOException | JsonParseException e) {
            LOGGER.log(Level.SEVERE, "Error communicating with LM Studio", e);
            throw new LLMException("Failed to get response from LM Studio: " + e.getMessage(), e);
        }
    }

    private @NotNull JsonObject getJsonObject(String prompt) {
        JsonObject requestBody = new JsonObject();
//        requestBody.addProperty("model", model);

        JsonArray messages = new JsonArray();
        JsonObject userMessage = new JsonObject();
        userMessage.addProperty("role", "user");
        userMessage.addProperty("content", prompt);
        messages.add(userMessage);
        requestBody.add("messages", messages);

        requestBody.addProperty("temperature", settings.getTemperature());
        requestBody.addProperty("max_tokens", settings.getMaxTokens());
        requestBody.addProperty("stream", false);
        requestBody.addProperty("model", settings.getLlmModel());
        return requestBody;
    }

    @Override
    public boolean isAvailable() {
        // Можно добавить простую проверку доступности, например, HEAD-запрос к эндпоинту.
        // Для простоты возвращаем true.
        return true;
    }

    @Override
    public String getModelName() {
        return "local/" + settings.getLlmModel();
    }
}