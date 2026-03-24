package ru.sbrf.uddk.ai.testing.lsprag.model;

import java.util.HashMap;
import java.util.Map;

public class TestCase {
    private final String id;
    private final String description;
    private final String expectedStatus;
    private final Map<String, Object> input;
    private final String httpMethod;
    private final String endpoint;

    public TestCase(String id, String description, String expectedStatus,
                    String httpMethod, String endpoint) {
        this.id = id;
        this.description = description;
        this.expectedStatus = expectedStatus;
        this.httpMethod = httpMethod;
        this.endpoint = endpoint;
        this.input = new HashMap<>();
    }

    public void addInput(String key, Object value) {
        input.put(key, value);
    }

    public String getId() {
        return id;
    }

    public String getDescription() {
        return description;
    }

    public String getExpectedStatus() {
        return expectedStatus;
    }

    public Map<String, Object> getInput() {
        return new HashMap<>(input);
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public String getEndpoint() {
        return endpoint;
    }

    @Override
    public String toString() {
        return String.format("[%s] %s → %s %s (expect %s)",
                id, description, httpMethod, endpoint, expectedStatus);
    }
}
