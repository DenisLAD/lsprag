package com.reasoningtestgen.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.*;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * OpenAI API provider implementation
 */
public class OpenAIProvider implements LLMProvider {

    private static final Logger LOG = LoggerFactory.getLogger(OpenAIProvider.class);
    private static final String DEFAULT_ENDPOINT = "https://api.openai.com/v1/chat/completions";

    private final String apiKey;
    private final String endpoint;
    private final String model;
    private final int timeoutSeconds;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public OpenAIProvider(@NotNull String apiKey,
                           @NotNull String model,
                           int timeoutSeconds) {
        this(apiKey, DEFAULT_ENDPOINT, model, timeoutSeconds);
    }

    public OpenAIProvider(@NotNull String apiKey,
                           @NotNull String model,
                           int timeoutSeconds,
                           @NotNull OkHttpClient client) {
        this(apiKey, DEFAULT_ENDPOINT, model, timeoutSeconds, client);
    }

    public OpenAIProvider(@NotNull String apiKey,
                           @NotNull String endpoint,
                           @NotNull String model,
                           int timeoutSeconds) {
        this.apiKey = apiKey;
        this.endpoint = endpoint;
        this.model = model;
        this.timeoutSeconds = timeoutSeconds;
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .build();
        this.objectMapper = new ObjectMapper();
    }

    public OpenAIProvider(@NotNull String apiKey,
                           @NotNull String endpoint,
                           @NotNull String model,
                           int timeoutSeconds,
                           @NotNull OkHttpClient client) {
        this.apiKey = apiKey;
        this.endpoint = endpoint;
        this.model = model;
        this.timeoutSeconds = timeoutSeconds;
        this.httpClient = client;
        this.objectMapper = new ObjectMapper();
    }

    @NotNull
    @Override
    public String chat(@NotNull String prompt, @NotNull String systemPrompt) throws LLMException {
        return chatWithJsonSchema(prompt, systemPrompt, null);
    }

    @NotNull
    @Override
    public String chatWithJsonSchema(@NotNull String prompt, 
                                      @NotNull String systemPrompt, 
                                      @NotNull String jsonSchema) throws LLMException {
        try {
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", model);
            requestBody.put("temperature", 0.3);
            
            if (jsonSchema != null && !jsonSchema.isEmpty()) {
                ObjectNode responseFormat = requestBody.putObject("response_format");
                ObjectNode jsonSchemaObj = responseFormat.putObject("json_schema");
                jsonSchemaObj.put("name", "test_generation");
                jsonSchemaObj.put("schema", jsonSchema);
            }

            ArrayNode messages = requestBody.putArray("messages");
            
            ObjectNode systemMsg = messages.addObject();
            systemMsg.put("role", "system");
            systemMsg.put("content", systemPrompt);
            
            ObjectNode userMsg = messages.addObject();
            userMsg.put("role", "user");
            userMsg.put("content", prompt);

            Request request = new Request.Builder()
                .url(endpoint)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .post(RequestBody.create(
                    objectMapper.writeValueAsBytes(requestBody),
                    MediaType.parse("application/json")
                ))
                .build();

            LOG.debug("Sending request to OpenAI endpoint");
            
            try (Response response = httpClient.newCall(request).execute()) {
                String responseBody = response.body() != null ? response.body().string() : "";
                
                if (!response.isSuccessful()) {
                    throw new LLMException(
                        "LLM API error: " + response.code(), 
                        response.code(), 
                        responseBody
                    );
                }

                JsonNode jsonResponse = objectMapper.readTree(responseBody);
                return extractContent(jsonResponse);
            }
        } catch (LLMException e) {
            throw e;
        } catch (Exception e) {
            throw new LLMException("Failed to call LLM API", e);
        }
    }

    /**
     * Extract content from OpenAI response
     */
    @NotNull
    private String extractContent(@NotNull JsonNode response) {
        JsonNode choices = response.get("choices");
        if (choices != null && choices.isArray() && choices.size() > 0) {
            JsonNode message = choices.get(0).get("message");
            if (message != null) {
                return message.get("content").asText();
            }
        }
        return "";
    }

    @NotNull
    @Override
    public LLMProviderType getType() {
        return LLMProviderType.OPENAI;
    }
}
