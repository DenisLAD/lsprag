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
 * LM Studio provider implementation
 * Uses OpenAI-compatible API format with local endpoint
 * Default: http://localhost:1234/v1/chat/completions
 */
public class LMStudioProvider implements LLMProvider {

    private static final Logger LOG = LoggerFactory.getLogger(LMStudioProvider.class);
    private static final String DEFAULT_ENDPOINT = "http://localhost:1234/v1/chat/completions";
    private static final String DEFAULT_MODEL = "qwen/qwen3.5-9b";

    private final String endpoint;
    private final String model;
    private final int timeoutSeconds;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public LMStudioProvider() {
        this(DEFAULT_ENDPOINT, DEFAULT_MODEL, 120);
    }

    public LMStudioProvider(@NotNull String endpoint, 
                              @NotNull String model, 
                              int timeoutSeconds) {
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
            requestBody.put("max_tokens", 4096);

            ArrayNode messages = requestBody.putArray("messages");
            
            ObjectNode systemMsg = messages.addObject();
            systemMsg.put("role", "system");
            systemMsg.put("content", systemPrompt);
            
            ObjectNode userMsg = messages.addObject();
            userMsg.put("role", "user");
            userMsg.put("content", prompt);

            if (jsonSchema != null && !jsonSchema.isEmpty()) {
                ObjectNode responseFormat = requestBody.putObject("response_format");
                ObjectNode jsonSchemaObj = responseFormat.putObject("json_schema");
                jsonSchemaObj.put("name", "test_generation");
                jsonSchemaObj.put("schema", objectMapper.readTree(jsonSchema));
            }

            Request request = new Request.Builder()
                .url(endpoint)
                .header("Content-Type", "application/json")
                .post(RequestBody.create(
                    objectMapper.writeValueAsBytes(requestBody),
                    MediaType.parse("application/json")
                ))
                .build();

            LOG.info("Sending request to LM Studio: {}", endpoint);
            LOG.debug("Model: {}, Prompt length: {}", model, prompt.length());
            
            try (Response response = httpClient.newCall(request).execute()) {
                String responseBody = response.body() != null ? response.body().string() : "";
                
                if (!response.isSuccessful()) {
                    throw new LLMException(
                        "LM Studio API error: " + response.code() + " - " + response.message(), 
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
            throw new LLMException("Failed to call LM Studio API: " + e.getMessage(), e);
        }
    }

    /**
     * Extract content from LM Studio response
     */
    @NotNull
    private String extractContent(@NotNull JsonNode response) {
        JsonNode choices = response.get("choices");
        if (choices != null && choices.isArray() && choices.size() > 0) {
            JsonNode message = choices.get(0).get("message");
            if (message != null) {
                JsonNode contentNode = message.get("content");
                if (contentNode != null && contentNode.isTextual()) {
                    return contentNode.asText();
                }
            }
        }
        
        // Log full response for debugging
        LOG.warn("Unexpected LM Studio response format: {}", response.toString());
        return "";
    }

    @NotNull
    @Override
    public LLMProviderType getType() {
        return LLMProviderType.LM_STUDIO;
    }

    /**
     * Get endpoint URL
     */
    @NotNull
    public String getEndpoint() {
        return endpoint;
    }

    /**
     * Get model name
     */
    @NotNull
    public String getModel() {
        return model;
    }
}
