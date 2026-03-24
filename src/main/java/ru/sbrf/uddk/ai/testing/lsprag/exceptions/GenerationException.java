package ru.sbrf.uddk.ai.testing.lsprag.exceptions;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GenerationException extends Exception {

    private final GenerationStage failedStage;
    private final String userMessage;

    public GenerationException(@NotNull String message, @Nullable Throwable cause) {
        this(message, GenerationStage.UNKNOWN, cause);
    }

    public GenerationException(@NotNull String message,
                               @NotNull GenerationStage stage,
                               @Nullable Throwable cause) {
        super(message, cause);
        this.failedStage = stage;
        this.userMessage = buildUserMessage(message, stage);
    }

    @NotNull
    private String buildUserMessage(@NotNull String technicalMessage, @NotNull GenerationStage stage) {
        return switch (stage) {
            case TOKEN_EXTRACTION -> "Ошибка анализа кода: " + technicalMessage;
            case CONTEXT_RETRIEVAL -> "Не удалось получить контекст методов: " + technicalMessage;
            case TEST_PLANNING -> "Ошибка планирования тест-кейсов: " + technicalMessage;
            case LLM_REQUEST -> "Ошибка запроса к LLM: " + technicalMessage;
            case CODE_GENERATION -> "Не удалось сгенерировать код теста: " + technicalMessage;
            case SELF_CORRECTION -> "Не удалось исправить ошибки в коде: " + technicalMessage;
            case FILE_WRITE -> "Ошибка записи файла: " + technicalMessage;
            default -> "Ошибка генерации: " + technicalMessage;
        };
    }

    @NotNull
    public GenerationStage getFailedStage() {
        return failedStage;
    }

    @NotNull
    public String getUserMessage() {
        return userMessage;
    }

    /**
     * Этапы генерации для точной обработки ошибок
     */
    public enum GenerationStage {
        TOKEN_EXTRACTION,
        CONTEXT_RETRIEVAL,
        TEST_PLANNING,
        LLM_REQUEST,
        CODE_GENERATION,
        SELF_CORRECTION,
        FILE_WRITE,
        UNKNOWN
    }
}
