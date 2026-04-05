package com.reasoningtestgen.llm;

import com.reasoningtestgen.settings.PluginSettings;
import org.jetbrains.annotations.NotNull;

/**
 * Factory for creating LLM providers
 */
public class LLMProviderFactory {

    /**
     * Create LLM provider based on settings
     */
    @NotNull
    public static LLMProvider createProvider(@NotNull PluginSettings settings) {
        return switch (settings.getProviderType()) {
            case OPENAI -> new OpenAIProvider(
                settings.getApiKey(),
                settings.getModel(),
                settings.getTimeout()
            );
            case ANTHROPIC -> throw new UnsupportedOperationException("Anthropic provider not yet implemented");
            case OLLAMA -> new OllamaProvider(
                settings.getModel(),
                settings.getTimeout()
            );
            case LM_STUDIO -> {
                String endpoint = settings.getEndpoint().isEmpty() ? 
                    "http://localhost:1234/v1/chat/completions" : 
                    settings.getEndpoint();
                String model = settings.getModel().isEmpty() ? 
                    "qwen/qwen3.5-9b" : 
                    settings.getModel();
                yield new LMStudioProvider(endpoint, model, settings.getTimeout());
            }
            case GIGACHAT -> createGigaChatProvider(settings);
            case CUSTOM -> new OpenAIProvider(
                settings.getApiKey(),
                settings.getEndpoint(),
                settings.getModel(),
                settings.getTimeout()
            );
        };
    }

    /**
     * Create GigaChat provider with appropriate auth method
     */
    @NotNull
    private static LLMProvider createGigaChatProvider(@NotNull PluginSettings settings) {
        String model = settings.getModel().isEmpty() ? "GigaChat-Max" : settings.getModel();
        int timeout = settings.getTimeout() <= 0 ? 120 : settings.getTimeout();
        
        // Determine auth method from settings
        if (settings.getGigachatAuthMethod() == GigaChatProvider.AuthMethod.CERTIFICATE) {
            return new GigaChatProvider(
                settings.getKeystorePath(),
                settings.getKeystorePassword(),
                settings.getKeystoreType(),
                settings.getGigachatClientId(),
                settings.getGigachatClientSecret(),
                settings.getGigachatScope(),
                model,
                timeout
            );
        } else if (settings.getGigachatAuthMethod() == GigaChatProvider.AuthMethod.CLIENT_CREDENTIALS) {
            return new GigaChatProvider(
                settings.getGigachatClientId(),
                settings.getGigachatClientSecret(),
                settings.getGigachatScope(),
                model,
                timeout
            );
        } else {
            // API Key auth
            return new GigaChatProvider(
                settings.getApiKey(),
                model,
                timeout
            );
        }
    }
}
