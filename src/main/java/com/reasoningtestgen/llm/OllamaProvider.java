package com.reasoningtestgen.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.*;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Ollama (local LLM) provider implementation
 */
public class OllamaProvider implements LLMProvider {

    private static final Logger LOG = LoggerFactory.getLogger(OllamaProvider.class);
    private static final String DEFAULT_ENDPOINT = "http://localhost:11434/api/chat";

    private final String endpoint;
    private final String model;
    private final int timeoutSeconds;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public OllamaProvider(@NotNull String model, int timeoutSeconds) {
        this(DEFAULT_ENDPOINT, model, timeoutSeconds);
    }

    public OllamaProvider(@NotNull String endpoint, 
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
            requestBody.put("stream", false);

            ObjectNode messages = objectMapper.createObjectNode();
            
            ObjectNode systemMsg = objectMapper.createObjectNode();
            systemMsg.put("role", "system");
            systemMsg.put("content", systemPrompt);
            
            ObjectNode userMsg = objectMapper.createObjectNode();
            userMsg.put("role", "user");
            userMsg.put("content", prompt);
            
            requestBody.putArray("messages").add(systemMsg).add(userMsg);

            if (jsonSchema != null && !jsonSchema.isEmpty()) {
                requestBody.put("format", jsonSchema);
            }

            Request request = new Request.Builder()
                .url(endpoint)
                .header("Content-Type", "application/json")
                .post(RequestBody.create(
                    objectMapper.writeValueAsBytes(requestBody),
                    MediaType.parse("application/json")
                ))
                .build();

            LOG.debug("Sending request to Ollama endpoint");
            
            try (Response response = httpClient.newCall(request).execute()) {
                String responseBody = response.body() != null ? response.body().string() : "";
                
                if (!response.isSuccessful()) {
                    throw new LLMException(
                        "Ollama API error: " + response.code(), 
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
            throw new LLMException("Failed to call Ollama API", e);
        }
    }

    /**
     * Extract content from Ollama response
     */
    @NotNull
    private String extractContent(@NotNull JsonNode response) {
        JsonNode message = response.get("message");
        if (message != null) {
            return message.get("content").asText();
        }
        return "";
    }

    @NotNull
    @Override
    public LLMProviderType getType() {
        return LLMProviderType.OLLAMA;
    }
}
