package ru.sbrf.uddk.ai.testing.lsprag.llm.gigachat;

import com.google.gson.annotations.SerializedName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class GigaChatRequest {

    @SerializedName("model")
    private String model;

    @SerializedName("messages")
    private List<Message> messages;

    @SerializedName("temperature")
    private Double temperature;

    @SerializedName("max_tokens")
    private Integer maxTokens;

    @SerializedName("top_p")
    private Double topP;

    @SerializedName("repetition_penalty")
    private Double repetitionPenalty;

    @SerializedName("stream")
    private Boolean stream;

    @SerializedName("functions")
    @Nullable
    private List<Function> functions;

    @SerializedName("function_call")
    @Nullable
    private Object functionCall;

    @SerializedName("attachments")
    @Nullable
    private List<String> attachments;

    public GigaChatRequest() {
        this.messages = new ArrayList<>();
        this.stream = false;
    }

    public GigaChatRequest(@NotNull String model, @NotNull String prompt) {
        this();
        this.model = model;
        this.messages.add(new Message("user", prompt));
    }

    public void addSystemMessage(@NotNull String content) {
        messages.add(0, new Message("system", content));
    }

    public void addUserMessage(@NotNull String content) {
        messages.add(new Message("user", content));
    }

    public void addAssistantMessage(@NotNull String content) {
        messages.add(new Message("assistant", content));
    }

    // Getters and Setters
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public List<Message> getMessages() { return messages; }
    public void setMessages(List<Message> messages) { this.messages = messages; }

    public Double getTemperature() { return temperature; }
    public void setTemperature(Double temperature) { this.temperature = temperature; }

    public Integer getMaxTokens() { return maxTokens; }
    public void setMaxTokens(Integer maxTokens) { this.maxTokens = maxTokens; }

    public Double getTopP() { return topP; }
    public void setTopP(Double topP) { this.topP = topP; }

    public Double getRepetitionPenalty() { return repetitionPenalty; }
    public void setRepetitionPenalty(Double repetitionPenalty) { this.repetitionPenalty = repetitionPenalty; }

    public Boolean getStream() { return stream; }
    public void setStream(Boolean stream) { this.stream = stream; }

    @Nullable
    public List<Function> getFunctions() { return functions; }
    public void setFunctions(List<Function> functions) { this.functions = functions; }

    @Nullable
    public Object getFunctionCall() { return functionCall; }
    public void setFunctionCall(Object functionCall) { this.functionCall = functionCall; }

    @Nullable
    public List<String> getAttachments() { return attachments; }
    public void setAttachments(List<String> attachments) { this.attachments = attachments; }

    /**
     * Сообщение для Chat API
     */
    public static class Message {
        @SerializedName("role")
        private String role;

        @SerializedName("content")
        private String content;

        @SerializedName("attachments")
        @Nullable
        private List<String> attachments;

        public Message() {}

        public Message(@NotNull String role, @NotNull String content) {
            this.role = role;
            this.content = content;
        }

        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }

        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }

        @Nullable
        public List<String> getAttachments() { return attachments; }
        public void setAttachments(List<String> attachments) { this.attachments = attachments; }
    }

    /**
     * Описание функции для function calling
     */
    public static class Function {
        @SerializedName("name")
        private String name;

        @SerializedName("description")
        private String description;

        @SerializedName("parameters")
        private Object parameters;

        @SerializedName("few_shot_examples")
        @Nullable
        private List<FewShotExample> fewShotExamples;

        public Function() {}

        public Function(String name, String description, Object parameters) {
            this.name = name;
            this.description = description;
            this.parameters = parameters;
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public Object getParameters() { return parameters; }
        public void setParameters(Object parameters) { this.parameters = parameters; }

        @Nullable
        public List<FewShotExample> getFewShotExamples() { return fewShotExamples; }
        public void setFewShotExamples(List<FewShotExample> fewShotExamples) {
            this.fewShotExamples = fewShotExamples;
        }
    }

    /**
     * Пример для few-shot обучения функции
     */
    public static class FewShotExample {
        @SerializedName("request")
        private String request;

        @SerializedName("params")
        private Object params;

        public FewShotExample() {}

        public FewShotExample(String request, Object params) {
            this.request = request;
            this.params = params;
        }

        public String getRequest() { return request; }
        public void setRequest(String request) { this.request = request; }

        public Object getParams() { return params; }
        public void setParams(Object params) { this.params = params; }
    }
}
