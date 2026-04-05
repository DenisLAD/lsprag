package com.reasoningtestgen.llm;

import com.reasoningtestgen.settings.PluginSettings;
import okhttp3.OkHttpClient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.util.concurrent.TimeUnit;

/**
 * Factory for creating LLM providers with SSL support
 */
public class LLMProviderFactory {

    private static final Logger LOG = LoggerFactory.getLogger(LLMProviderFactory.class);

    /**
     * Create LLM provider based on settings
     */
    @NotNull
    public static LLMProvider createProvider(@NotNull PluginSettings settings) {
        // Create HTTP client with SSL if enabled
        OkHttpClient httpClient = createHttpClientWithSSL(settings);

        return switch (settings.getProviderType()) {
            case OPENAI -> new OpenAIProvider(
                settings.getApiKey(),
                settings.getModel(),
                settings.getTimeout(),
                httpClient
            );
            case ANTHROPIC -> throw new UnsupportedOperationException("Anthropic provider not yet implemented");
            case OLLAMA -> new OllamaProvider(
                settings.getModel(),
                settings.getTimeout(),
                httpClient
            );
            case LM_STUDIO -> {
                String endpoint = settings.getEndpoint().isEmpty() ?
                    "http://localhost:1234/v1/chat/completions" :
                    settings.getEndpoint();
                String model = settings.getModel().isEmpty() ?
                    "qwen/qwen3.5-9b" :
                    settings.getModel();
                yield new LMStudioProvider(endpoint, model, settings.getTimeout(), httpClient);
            }
            case GIGACHAT -> createGigaChatProvider(settings, httpClient);
            case CUSTOM -> new OpenAIProvider(
                settings.getApiKey(),
                settings.getEndpoint(),
                settings.getModel(),
                settings.getTimeout(),
                httpClient
            );
        };
    }

    /**
     * Create HTTP client with SSL support if enabled
     */
    @NotNull
    private static OkHttpClient createHttpClientWithSSL(@NotNull PluginSettings settings) {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
            .connectTimeout(settings.getTimeout(), TimeUnit.SECONDS)
            .readTimeout(settings.getTimeout(), TimeUnit.SECONDS)
            .writeTimeout(settings.getTimeout(), TimeUnit.SECONDS);

        // Configure SSL if enabled
        if (settings.isUseSSL() && !settings.getSslKeyStorePath().isEmpty()) {
            try {
                String keystorePath = settings.getSslKeyStorePath();
                String keystorePassword = settings.getSslKeyStorePassword();
                String keystoreType = "JKS"; // Default to Jks

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

                // Initialize SSLContext
                SSLContext sslContext = SSLContext.getInstance("TLS");
                sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

                builder.sslSocketFactory(sslContext.getSocketFactory(), (javax.net.ssl.X509TrustManager) tmf.getTrustManagers()[0]);
                
                LOG.info("SSL enabled with keystore: {}", keystorePath);
            } catch (Exception e) {
                LOG.error("Failed to configure SSL, falling back to default", e);
                // Fall back to default SSL if configuration fails
            }
        }

        return builder.build();
    }

    /**
     * Create GigaChat provider with appropriate auth method
     */
    @NotNull
    private static LLMProvider createGigaChatProvider(@NotNull PluginSettings settings, @NotNull OkHttpClient httpClient) {
        String model = settings.getModel().isEmpty() ? "GigaChat-Max" : settings.getModel();
        int timeout = settings.getTimeout() <= 0 ? 120 : settings.getTimeout();

        // Determine auth method from settings
        if (settings.getGigachatAuthMethod() == GigaChatProvider.AuthMethod.CERTIFICATE) {
            return new GigaChatProvider(
                settings.getSslKeyStorePath(),
                settings.getSslKeyStorePassword(),
                "JKS",
                settings.getGigachatClientId(),
                settings.getGigachatClientSecret(),
                settings.getGigachatScope(),
                model,
                timeout,
                httpClient
            );
        } else if (settings.getGigachatAuthMethod() == GigaChatProvider.AuthMethod.CLIENT_CREDENTIALS) {
            return new GigaChatProvider(
                settings.getGigachatClientId(),
                settings.getGigachatClientSecret(),
                settings.getGigachatScope(),
                model,
                timeout,
                httpClient
            );
        } else {
            // API Key auth
            return new GigaChatProvider(
                settings.getApiKey(),
                model,
                timeout,
                httpClient
            );
        }
    }
}
