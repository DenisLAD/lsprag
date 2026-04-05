package com.reasoningtestgen.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.*;
import java.util.concurrent.TimeUnit;

/**
 * GigaChat API provider implementation
 * Supports multiple authentication methods:
 * - API Key (for individuals)
 * - Client ID + Client Secret (for businesses)
 * - Certificates with JKS keystore
 * 
 * OAuth 2.0 token management with auto-refresh
 */
public class GigaChatProvider implements LLMProvider {

    private static final Logger LOG = LoggerFactory.getLogger(GigaChatProvider.class);
    private static final String TOKEN_ENDPOINT = "https://ngw.devices.sberbank.ru:9443/api/v2/oauth";
    private static final String CHAT_ENDPOINT = "https://gigachat.devices.sberbank.ru/api/v1/chat/completions";
    private static final String CHAT_ENDPOINT_CORP = "https://api.giga.chat/v1/chat/completions";
    
    private final AuthMethod authMethod;
    private final String apiKey; // For individuals
    private final String clientId;
    private final String clientSecret;
    private final String scope; // GIGACHAT_API_PERS, GIGACHAT_API_B2B, GIGACHAT_API_CORP
    private final String model;
    private final int timeoutSeconds;
    
    // Certificate auth
    private final String keystorePath;
    private final String keystorePassword;
    private final String keystoreType; // JKS or PKCS12
    
    private volatile String accessToken;
    private volatile long tokenExpiresAt;
    private final ObjectMapper objectMapper;
    private OkHttpClient httpClient;

    public enum AuthMethod {
        API_KEY,
        CLIENT_CREDENTIALS,
        CERTIFICATE
    }

    /**
     * Constructor for API Key auth (individuals)
     */
    public GigaChatProvider(@NotNull String apiKey,
                              @NotNull String model,
                              int timeoutSeconds) {
        this.authMethod = AuthMethod.API_KEY;
        this.apiKey = apiKey;
        this.clientId = null;
        this.clientSecret = null;
        this.scope = "GIGACHAT_API_PERS";
        this.model = model;
        this.timeoutSeconds = timeoutSeconds;
        this.keystorePath = null;
        this.keystorePassword = null;
        this.keystoreType = "JKS";
        this.objectMapper = new ObjectMapper();
        initHttpClient();
    }

    /**
     * Constructor for Client ID + Secret auth (businesses)
     */
    public GigaChatProvider(@NotNull String clientId,
                              @NotNull String clientSecret,
                              @NotNull String scope,
                              @NotNull String model,
                              int timeoutSeconds) {
        this.authMethod = AuthMethod.CLIENT_CREDENTIALS;
        this.apiKey = null;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.scope = scope;
        this.model = model;
        this.timeoutSeconds = timeoutSeconds;
        this.keystorePath = null;
        this.keystorePassword = null;
        this.keystoreType = "JKS";
        this.objectMapper = new ObjectMapper();
        initHttpClient();
    }

    /**
     * Constructor for Certificate auth with JKS keystore
     */
    public GigaChatProvider(@NotNull String keystorePath,
                              @NotNull String keystorePassword,
                              @NotNull String keystoreType,
                              @NotNull String clientId,
                              @NotNull String clientSecret,
                              @NotNull String scope,
                              @NotNull String model,
                              int timeoutSeconds) {
        this.authMethod = AuthMethod.CERTIFICATE;
        this.apiKey = null;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.scope = scope;
        this.model = model;
        this.timeoutSeconds = timeoutSeconds;
        this.keystorePath = keystorePath;
        this.keystorePassword = keystorePassword;
        this.keystoreType = keystoreType;
        this.objectMapper = new ObjectMapper();
        initHttpClientWithCertificates();
    }

    /**
     * Initialize HTTP client without certificates
     */
    private void initHttpClient() {
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .build();
    }

    /**
     * Initialize HTTP client with JKS/PKCS12 certificates
     */
    private void initHttpClientWithCertificates() {
        try {
            // Load keystore
            KeyStore keyStore = KeyStore.getInstance(keystoreType);
            try (FileInputStream fis = new FileInputStream(keystorePath)) {
                keyStore.load(fis, keystorePassword.toCharArray());
            }

            // Initialize KeyManager
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(keyStore, keystorePassword.toCharArray());

            // Initialize TrustManager
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(keyStore);

            // Create SSL Context
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

            this.httpClient = new OkHttpClient.Builder()
                .sslSocketFactory(sslContext.getSocketFactory(), (javax.net.ssl.X509TrustManager) tmf.getTrustManagers()[0])
                .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .build();

            LOG.info("GigaChat HTTP client initialized with {} keystore: {}", keystoreType, keystorePath);
        } catch (Exception e) {
            LOG.error("Failed to initialize GigaChat HTTP client with certificates", e);
            throw new RuntimeException("Failed to initialize GigaChat client: " + e.getMessage(), e);
        }
    }

    /**
     * Get access token (with caching and auto-refresh)
     */
    @NotNull
    private synchronized String getAccessToken() throws LLMException {
        // Check if token is still valid (with 5 minute buffer)
        if (accessToken != null && System.currentTimeMillis() < (tokenExpiresAt - 300000)) {
            return accessToken;
        }

        LOG.info("Requesting new GigaChat access token");
        
        try {
            String authHeaderValue;
            
            if (authMethod == AuthMethod.API_KEY) {
                // For API key, use it directly as Authorization header
                authHeaderValue = "Bearer " + apiKey;
                
                RequestBody body = RequestBody.create(
                    "scope=" + scope,
                    MediaType.parse("application/x-www-form-urlencoded")
                );
                
                Request request = new Request.Builder()
                    .url(TOKEN_ENDPOINT)
                    .header("Authorization", authHeaderValue)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Accept", "application/json")
                    .header("RqUID", java.util.UUID.randomUUID().toString())
                    .post(body)
                    .build();
                
                accessToken = requestToken(request);
            } else {
                // For Client Credentials or Certificate auth
                String credentials = clientId + ":" + clientSecret;
                String basicAuth = java.util.Base64.getEncoder().encodeToString(credentials.getBytes());
                
                RequestBody body = RequestBody.create(
                    "scope=" + scope,
                    MediaType.parse("application/x-www-form-urlencoded")
                );
                
                Request request = new Request.Builder()
                    .url(TOKEN_ENDPOINT)
                    .header("Authorization", "Basic " + basicAuth)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Accept", "application/json")
                    .header("RqUID", java.util.UUID.randomUUID().toString())
                    .post(body)
                    .build();
                
                accessToken = requestToken(request);
            }
            
            return accessToken;
        } catch (LLMException e) {
            throw e;
        } catch (Exception e) {
            throw new LLMException("Failed to get GigaChat access token", e);
        }
    }

    /**
     * Request token from OAuth endpoint
     */
    @NotNull
    private String requestToken(@NotNull Request request) throws IOException, LLMException {
        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            
            if (!response.isSuccessful()) {
                throw new LLMException(
                    "GigaChat token request failed: " + response.code(),
                    response.code(),
                    responseBody
                );
            }
            
            JsonNode json = objectMapper.readTree(responseBody);
            String token = json.get("access_token").asText();
            long expiresAt = json.has("expires_at") ? json.get("expires_at").asLong() * 1000 : 
                           System.currentTimeMillis() + (30 * 60 * 1000); // 30 minutes default
            
            this.accessToken = token;
            this.tokenExpiresAt = expiresAt;
            
            LOG.info("GigaChat access token received, expires at: {}", new java.util.Date(expiresAt));
            
            return token;
        }
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
            // Get access token
            String token = getAccessToken();
            
            // Determine endpoint based on scope
            String endpoint = (scope.equals("GIGACHAT_API_CORP")) ? CHAT_ENDPOINT_CORP : CHAT_ENDPOINT;
            
            // Build request
            com.fasterxml.jackson.databind.node.ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", model);
            requestBody.put("temperature", 0.3);
            requestBody.put("max_tokens", 4096);
            
            var messages = requestBody.putArray("messages");
            
            var systemMsg = messages.addObject();
            systemMsg.put("role", "system");
            systemMsg.put("content", systemPrompt);
            
            var userMsg = messages.addObject();
            userMsg.put("role", "user");
            userMsg.put("content", prompt);
            
            Request request = new Request.Builder()
                .url(endpoint)
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .post(RequestBody.create(
                    objectMapper.writeValueAsBytes(requestBody),
                    MediaType.parse("application/json")
                ))
                .build();
            
            LOG.debug("Sending request to GigaChat: {}", endpoint);
            
            try (Response response = httpClient.newCall(request).execute()) {
                String responseBody = response.body() != null ? response.body().string() : "";
                
                if (!response.isSuccessful()) {
                    // If token expired, retry once
                    if (response.code() == 401) {
                        LOG.warn("GigaChat token expired, refreshing and retrying");
                        this.accessToken = null;
                        token = getAccessToken();
                        
                        Request retryRequest = new Request.Builder()
                            .url(endpoint)
                            .header("Authorization", "Bearer " + token)
                            .header("Content-Type", "application/json")
                            .post(RequestBody.create(
                                objectMapper.writeValueAsBytes(requestBody),
                                MediaType.parse("application/json")
                            ))
                            .build();
                        
                        try (Response retryResponse = httpClient.newCall(retryRequest).execute()) {
                            String retryBody = retryResponse.body() != null ? retryResponse.body().string() : "";
                            if (!retryResponse.isSuccessful()) {
                                throw new LLMException(
                                    "GigaChat API error: " + retryResponse.code(),
                                    retryResponse.code(),
                                    retryBody
                                );
                            }
                            return extractContent(objectMapper.readTree(retryBody));
                        }
                    }
                    
                    throw new LLMException(
                        "GigaChat API error: " + response.code(),
                        response.code(),
                        responseBody
                    );
                }
                
                return extractContent(objectMapper.readTree(responseBody));
            }
        } catch (LLMException e) {
            throw e;
        } catch (Exception e) {
            throw new LLMException("Failed to call GigaChat API: " + e.getMessage(), e);
        }
    }

    /**
     * Extract content from GigaChat response
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
        
        LOG.warn("Unexpected GigaChat response format: {}", response.toString());
        return "";
    }

    @NotNull
    @Override
    public LLMProviderType getType() {
        return LLMProviderType.GIGACHAT;
    }

    /**
     * Get current auth method
     */
    @NotNull
    public AuthMethod getAuthMethod() {
        return authMethod;
    }

    /**
     * Get model name
     */
    @NotNull
    public String getModel() {
        return model;
    }

    /**
     * Get current token (for debugging)
     */
    @Nullable
    public String getCurrentToken() {
        return accessToken;
    }

    /**
     * Force token refresh
     */
    public synchronized void refreshToken() throws LLMException {
        this.accessToken = null;
        this.tokenExpiresAt = 0;
        getAccessToken();
    }
}
