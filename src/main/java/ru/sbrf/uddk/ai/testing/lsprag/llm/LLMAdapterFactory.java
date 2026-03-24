package ru.sbrf.uddk.ai.testing.lsprag.llm;

import org.jetbrains.annotations.NotNull;
import ru.sbrf.uddk.ai.testing.lsprag.LspragSettingsState;
import ru.sbrf.uddk.ai.testing.lsprag.llm.gigachat.GigaChatConfig;
import ru.sbrf.uddk.ai.testing.lsprag.llm.gigachat.GigaChatGateway;

public class LLMAdapterFactory {

    @NotNull
    public static LLMGateway create(@NotNull LspragSettingsState settings)
            throws IllegalArgumentException {

        String type = settings.getLlmType();

        return switch (type.toLowerCase()) {
//            case "ollama" -> new OllamaGateway(settings);
//            case "openai" -> new OpenAIGateway(settings);
            case "lm studio" -> new LocalModelGateway(settings);
            case "gigachat" -> new GigaChatGateway(createGigaChatConfig(settings), settings);
//            case "custom" -> new CustomEndpointGateway(settings);
            default -> throw new IllegalArgumentException(
                    "Неподдерживаемый тип LLM: " + type +
                            ". Допустимые значения: ollama, openai, local, custom");
        };
    }

    private static GigaChatConfig createGigaChatConfig(LspragSettingsState settings) {
        GigaChatConfig config = new GigaChatConfig();

        config.setModelByName(settings.getLlmModel());
        config.setTemperature(settings.getTemperature());
        config.setMaxTokens(settings.getMaxTokens());
        //config.setMaxRetries(settings.getMaxRetryAttempts());
        config.setTimeoutSeconds(60);
        //config.setScope(settings.getGigaChatScope()); // Новое поле в settings

        return config;
    }

    /**
     * Проверяет доступность настроенного LLM
     */
    public static boolean isLLMAvailable(@NotNull LspragSettingsState settings) {
        try {
            LLMGateway gateway = create(settings);
            return gateway.isAvailable();
        } catch (Exception e) {
            return false;
        }
    }
}