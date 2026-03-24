package ru.sbrf.uddk.ai.testing.lsprag.model;

public class GeneratedTestData {
    private final String rawResponse;
    private final String javaCode;

    public GeneratedTestData(String rawResponse, String javaCode) {
        this.rawResponse = rawResponse;
        this.javaCode = javaCode;
    }

    public String getRawResponse() { return rawResponse; }
    public String getJavaCode() { return javaCode; }
}
