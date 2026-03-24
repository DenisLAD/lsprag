package ru.sbrf.uddk.ai.testing.lsprag.model;

public class GenerationResult {
    private final boolean success;
    private final String generatedCode;
    private final String outputPath;
    private final String errorMessage;

    private GenerationResult(boolean success, String generatedCode,
                             String outputPath, String errorMessage) {
        this.success = success;
        this.generatedCode = generatedCode;
        this.outputPath = outputPath;
        this.errorMessage = errorMessage;
    }

    public static GenerationResult success(String code, String path) {
        return new GenerationResult(true, code, path, null);
    }

    public static GenerationResult failure(String error) {
        return new GenerationResult(false, null, null, error);
    }

    public boolean isSuccess() { return success; }
    public String getGeneratedCode() { return generatedCode; }
    public String getOutputPath() { return outputPath; }
    public String getErrorMessage() { return errorMessage; }
}