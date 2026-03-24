package ru.sbrf.uddk.ai.testing.lsprag.llm.gigachat;

import com.google.gson.annotations.SerializedName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class GigaChatResponse {

    @SerializedName("id")
    private String id;

    @SerializedName("object")
    private String object;

    @SerializedName("created")
    private Long created;

    @SerializedName("model")
    private String model;

    @SerializedName("choices")
    private List<Choice> choices;

    @SerializedName("usage")
    private Usage usage;

    @SerializedName("error")
    @Nullable
    private Error error;

    // Getters
    public String getId() {
        return id;
    }

    public String getObject() {
        return object;
    }

    public Long getCreated() {
        return created;
    }

    public String getModel() {
        return model;
    }

    public List<Choice> getChoices() {
        return choices;
    }

    public Usage getUsage() {
        return usage;
    }

    @Nullable
    public Error getError() {
        return error;
    }

    /**
     * Получает текст ответа из первого choice
     */
    @NotNull
    public String getResponseText() {
        if (choices == null || choices.isEmpty()) {
            return "";
        }
        Choice choice = choices.get(0);
        return choice.getMessage() != null ? choice.getMessage().getContent() : "";
    }

    /**
     * Проверяет наличие ошибки
     */
    public boolean hasError() {
        return error != null;
    }

    /**
     * Выбор из ответа
     */
    public static class Choice {
        @SerializedName("index")
        private Integer index;

        @SerializedName("message")
        private Message message;

        @SerializedName("finish_reason")
        private String finishReason;

        public Integer getIndex() {
            return index;
        }

        public Message getMessage() {
            return message;
        }

        public String getFinishReason() {
            return finishReason;
        }
    }

    /**
     * Сообщение в ответе
     */
    public static class Message {
        @SerializedName("role")
        private String role;

        @SerializedName("content")
        private String content;

        @SerializedName("function_call")
        @Nullable
        private FunctionCall functionCall;

        @SerializedName("functions_state_id")
        @Nullable
        private String functionsStateId;

        public String getRole() {
            return role;
        }

        public String getContent() {
            return content;
        }

        @Nullable
        public FunctionCall getFunctionCall() {
            return functionCall;
        }

        @Nullable
        public String getFunctionsStateId() {
            return functionsStateId;
        }
    }

    /**
     * Вызов функции в ответе
     */
    public static class FunctionCall {
        @SerializedName("name")
        private String name;

        @SerializedName("arguments")
        private Object arguments;

        public String getName() {
            return name;
        }

        public Object getArguments() {
            return arguments;
        }
    }

    /**
     * Статистика использования токенов
     */
    public static class Usage {
        @SerializedName("prompt_tokens")
        private Integer promptTokens;

        @SerializedName("completion_tokens")
        private Integer completionTokens;

        @SerializedName("total_tokens")
        private Integer totalTokens;

        @SerializedName("precached_prompt_tokens")
        private Integer precachedPromptTokens;

        public Integer getPromptTokens() {
            return promptTokens;
        }

        public Integer getCompletionTokens() {
            return completionTokens;
        }

        public Integer getTotalTokens() {
            return totalTokens;
        }

        public Integer getPrecachedPromptTokens() {
            return precachedPromptTokens;
        }

        @Override
        public String toString() {
            return String.format("Usage{prompt=%d, completion=%d, total=%d, cached=%d}",
                    promptTokens, completionTokens, totalTokens, precachedPromptTokens);
        }
    }

    /**
     * Ошибка API
     */
    public static class Error {
        @SerializedName("status")
        private Integer status;

        @SerializedName("message")
        private String message;

        public Integer getStatus() {
            return status;
        }

        public String getMessage() {
            return message;
        }

        @Override
        public String toString() {
            return String.format("Error{status=%d, message=%s}", status, message);
        }
    }
}