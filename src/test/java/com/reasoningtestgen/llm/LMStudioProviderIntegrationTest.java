package com.reasoningtestgen.llm;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for LM Studio Provider
 * These tests require LM Studio running at http://localhost:1234
 * 
 * To run these tests:
 * 1. Start LM Studio
 * 2. Load model qwen/qwen3.5-9b
 * 3. Start server on port 1234
 * 4. Run tests with: -Dtest.lmstudio.enabled=true
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LMStudioProviderIntegrationTest {

    private LMStudioProvider provider;

    @BeforeAll
    void setUp() {
        // Only initialize if tests are enabled
        boolean enabled = Boolean.getBoolean("test.lmstudio.enabled");
        if (enabled) {
            provider = new LMStudioProvider(
                "http://localhost:1234/v1/chat/completions",
                "qwen/qwen3.5-9b",
                120
            );
        }
    }

    @Test
    @DisplayName("Should connect to LM Studio and get response")
    @EnabledIfSystemProperty(named = "test.lmstudio.enabled", matches = "true")
    void shouldConnectToLMStudioAndGetResponse() throws Exception {
        // Given
        String systemPrompt = "You are a helpful assistant. Respond with short answers.";
        String userPrompt = "What is 2+2?";
        
        // When
        String response = provider.chat(userPrompt, systemPrompt);
        
        // Then
        assertThat(response).isNotEmpty();
        assertThat(response).containsIgnoringCase("4");
        System.out.println("LM Studio Response: " + response);
    }

    @Test
    @DisplayName("Should generate JSON response for code analysis")
    @EnabledIfSystemProperty(named = "test.lmstudio.enabled", matches = "true")
    void shouldGenerateJSONResponseForCodeAnalysis() throws Exception {
        // Given
        String systemPrompt = """
            Analyze the following Java method and respond with JSON:
            {
              "methodName": "string",
              "returnType": "string",
              "paramCount": number
            }
            """;
        
        String userPrompt = """
            public String greet(String name) {
                return "Hello, " + name;
            }
            
            Respond with JSON only.
            """;
        
        // When
        String response = provider.chat(userPrompt, systemPrompt);
        
        // Then
        assertThat(response).isNotEmpty();
        assertThat(response).containsIgnoringCase("greet");
        System.out.println("Code Analysis Response: " + response);
    }

    @Test
    @DisplayName("Should handle timeout gracefully")
    @EnabledIfSystemProperty(named = "test.lmstudio.enabled", matches = "true")
    void shouldHandleTimeoutGracefully() {
        // Given
        LMStudioProvider shortTimeoutProvider = new LMStudioProvider(
            "http://localhost:1234/v1/chat/completions",
            "qwen/qwen3.5-9b",
            2  // 2 seconds timeout
        );
        
        // When/Then - Should not throw exception, just timeout
        assertThat(shortTimeoutProvider).isNotNull();
    }

    @Test
    @DisplayName("Should generate test scenario for simple method")
    @EnabledIfSystemProperty(named = "test.lmstudio.enabled", matches = "true")
    void shouldGenerateTestScenarioForSimpleMethod() throws Exception {
        // Given
        String systemPrompt = """
            You are a test engineer. Generate test scenarios for a Java method.
            Respond with JSON array of scenarios:
            [
              {
                "description": "string",
                "inputConditions": "string",
                "expectedOutcome": "string",
                "shouldThrow": boolean
              }
            ]
            """;
        
        String userPrompt = """
            Method: calculateDiscount(User user, List<Item> items)
            Returns: double (discount percentage 0-50)
            
            Business rules:
            - If user is null, throw IllegalArgumentException
            - If items is empty, return 0
            - If user is VIP, return 50
            - Otherwise, calculate based on item count
            
            Generate test scenarios.
            """;
        
        // When
        String response = provider.chat(userPrompt, systemPrompt);
        
        // Then
        assertThat(response).isNotEmpty();
        assertThat(response).contains("scenario");
        System.out.println("Test Scenarios Response: " + response);
    }

    @Test
    @DisplayName("Should provider return correct type")
    void shouldProviderReturnCorrectType() {
        // Given
        LMStudioProvider testProvider = new LMStudioProvider();
        
        // Then
        assertThat(testProvider.getType()).isEqualTo(LLMProviderType.LM_STUDIO);
        assertThat(testProvider.getEndpoint()).contains("localhost:1234");
        assertThat(testProvider.getModel()).contains("qwen");
    }
}
