package ru.sbrf.uddk.ai.testing.lsprag.llm.gigachat;

import org.jetbrains.annotations.NotNull;

public enum GigaChatModel {

    GIGACHAT("GigaChat", 4096, "chat"),
    GIGACHAT_PRO("GigaChat-Pro", 8192, "chat"),
    GIGACHAT_MAX("GigaChat-Max", 8192, "chat"),
    GIGACHAT_2_PRO("GigaChat-2-Pro", 8192, "chat"),
    GIGACHAT_2_MAX("GigaChat-2-Max", 8192, "chat"),
    GIGACHAT_4("GigaChat-4", 32768, "chat"),
    GIGACHAT_4O("GigaChat-4o", 128000, "chat"),

    // Модели в раннем доступе (с постфиксом -preview)
    GIGACHAT_PRO_PREVIEW("GigaChat-Pro-preview", 8192, "chat"),
    GIGACHAT_MAX_PREVIEW("GigaChat-Max-preview", 8192, "chat"),

    // Модели для эмбеддингов
    EMBEDDINGS("Embeddings", 4096, "embedder"),
    EMBEDDINGS_2("Embeddings-2", 4096, "embedder"),
    EMBEDDINGS_GIGA_R("EmbeddingsGigaR", 8192, "embedder"),

    // Модели для AI Check
    GIGA_CHECK_CLASSIFICATION("GigaCheckClassification", 8192, "aicheck"),
    GIGA_CHECK_DETECTION("GigaCheckDetection", 8192, "aicheck");

    private final String modelName;
    private final int maxTokens;
    private final String type;

    GigaChatModel(String modelName, int maxTokens, String type) {
        this.modelName = modelName;
        this.maxTokens = maxTokens;
        this.type = type;
    }

    @NotNull
    public String getModelName() {
        return modelName;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    @NotNull
    public String getType() {
        return type;
    }

    /**
     * Проверяет, является ли модель моделью в раннем доступе
     */
    public boolean isPreview() {
        return modelName.endsWith("-preview");
    }

    /**
     * Находит модель по имени
     */
    @NotNull
    public static GigaChatModel fromName(@NotNull String name) {
        for (GigaChatModel model : values()) {
            if (model.getModelName().equals(name)) {
                return model;
            }
        }
        // По умолчанию GigaChat
        return GIGACHAT;
    }

    /**
     * Рекомендует модель для генерации кода
     */
    @NotNull
    public static GigaChatModel recommendedForCodeGeneration() {
        return GIGACHAT_2_PRO; // Хороший баланс для кода
    }
}
