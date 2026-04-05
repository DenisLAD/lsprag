package com.reasoningtestgen.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.util.Timeout;
import org.apache.hc.client5.http.config.RequestConfig;

/**
 * Simple LM Studio integration test using Apache HTTP Client 5
 * Does not require JUnit - can be run standalone
 */
public class LMStudioApacheTest {
    
    private static int testsPassed = 0;
    private static int testsFailed = 0;
    private static final String ENDPOINT = "http://localhost:1234/v1/chat/completions";
    private static final String MODEL = "qwen/qwen3.5-9b";
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("LM Studio Integration Tests");
        System.out.println("Using Apache HTTP Client 5");
        System.out.println("Endpoint: " + ENDPOINT);
        System.out.println("Model: " + MODEL);
        System.out.println("========================================\n");

        try {
            // Test 1: Connectivity
            testConnectivity();
            
            // Test 2: Simple chat
            testSimpleChat();
            
            // Test 3: JSON response for code analysis
            testJSONResponseForCode();
            
            // Test 4: Test scenario generation
            testTestScenarioGeneration();
            
            // Test 5: Provider integration with OkHttp
            testProviderIntegration();

            // Summary
            System.out.println("\n========================================");
            System.out.println("Test Results");
            System.out.println("========================================");
            System.out.println("Total:   " + (testsPassed + testsFailed));
            System.out.println("Passed:  " + testsPassed);
            System.out.println("Failed:  " + testsFailed);
            System.out.println("========================================");
            
            if (testsFailed > 0) {
                System.exit(1);
            } else {
                System.out.println("\n✓ All LM Studio tests passed!");
                System.exit(0);
            }
        } catch (Exception e) {
            System.err.println("\n✗ Unexpected error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void testConnectivity() {
        System.out.println("Test 1: LM Studio Connectivity (Apache HTTP Client 5)");
        try {
            String response = sendRequest("Reply with just: OK", "You are a test assistant.", 30);
            
            assertNotNull(response, "response");
            assertTrue(response.length() > 0, "response not empty");
            
            System.out.println("  Response: " + response.substring(0, Math.min(50, response.length())));
            passTest();
        } catch (Exception e) {
            failTest(e, "Failed to connect to LM Studio. Make sure it's running on " + ENDPOINT);
        }
    }

    private static void testSimpleChat() {
        System.out.println("\nTest 2: Simple Chat");
        try {
            String systemPrompt = "You are a math tutor. Give short answers.";
            String userPrompt = "What is 15 * 7?";
            
            String response = sendRequest(userPrompt, systemPrompt, 60);
            
            assertNotNull(response, "response");
            assertTrue(response.length() > 0, "response not empty");
            
            System.out.println("  Response: " + response.substring(0, Math.min(100, response.length())));
            
            passTest();
        } catch (Exception e) {
            failTest(e);
        }
    }

    private static void testJSONResponseForCode() {
        System.out.println("\nTest 3: JSON Response for Code Analysis");
        try {
            String systemPrompt = "Analyze the Java method and respond with JSON only: {\"methodName\": \"string\", \"returnType\": \"string\", \"paramCount\": number}";
            
            String userPrompt = "public String processUser(String name, int age) { if (age < 18) { return \"Minor\"; } return \"Hello, \" + name; } Respond with valid JSON only.";
            
            String response = sendRequest(userPrompt, systemPrompt, 60);
            
            assertNotNull(response, "response");
            assertTrue(response.contains("processUser") || response.contains("methodName"), 
                      "response contains method name");
            
            System.out.println("  Response: " + response.substring(0, Math.min(200, response.length())));
            
            passTest();
        } catch (Exception e) {
            failTest(e);
        }
    }

    private static void testTestScenarioGeneration() {
        System.out.println("\nTest 4: Test Scenario Generation");
        try {
            String systemPrompt = "You are a test engineer. Generate 3 test scenarios for a Java method. Respond with JSON array: [{\"description\": \"string\", \"type\": \"HAPPY|ERROR|BOUNDARY\", \"input\": \"string\", \"expected\": \"string\"}]";
            
            String userPrompt = "Method: calculateDiscount(User user, List<Item> items). Rules: If user null throw exception, If items empty return 0, If VIP return 50.";
            
            String response = sendRequest(userPrompt, systemPrompt, 90);
            
            assertNotNull(response, "response");
            assertTrue(response.length() > 50, "response has meaningful content");
            assertTrue(response.contains("HAPPY") || response.contains("ERROR") || response.contains("BOUNDARY"),
                      "response contains test scenario types");
            
            System.out.println("  Response: " + response.substring(0, Math.min(300, response.length())));
            
            passTest();
        } catch (Exception e) {
            failTest(e);
        }
    }

    private static void testProviderIntegration() {
        System.out.println("\nTest 5: Provider Integration (OkHttp)");
        try {
            LMStudioProvider provider = new LMStudioProvider(ENDPOINT, MODEL, 120);
            
            // Verify provider configuration
            assertEqual(MODEL, provider.getModel(), "model");
            assertEqual(ENDPOINT, provider.getEndpoint(), "endpoint");
            assertTrue(provider.getType() == LLMProviderType.LM_STUDIO, "provider type is LM_STUDIO");
            
            // Test actual request using OkHttp (via LMStudioProvider)
            String response = provider.chat("Say: Integration test passed", "Be brief.");
            
            assertNotNull(response, "response");
            assertTrue(response.length() > 0, "response not empty");
            
            System.out.println("  Response: " + response.substring(0, Math.min(100, response.length())));
            
            passTest();
        } catch (Exception e) {
            failTest(e, "LMStudioProvider integration failed");
        }
    }

    // ===== Helper Methods using Apache HTTP Client 5 =====

    private static String sendRequest(String userPrompt, String systemPrompt, int timeoutSeconds) throws Exception {
        RequestConfig requestConfig = RequestConfig.custom()
            .setConnectionRequestTimeout(Timeout.ofSeconds(timeoutSeconds))
            .setResponseTimeout(Timeout.ofSeconds(timeoutSeconds))
            .build();

        String requestBody = String.format(
            "{\"model\":\"%s\",\"messages\":[{\"role\":\"system\",\"content\":\"%s\"},{\"role\":\"user\",\"content\":\"%s\"}],\"temperature\":0.3,\"max_tokens\":1000}",
            MODEL,
            escapeJson(systemPrompt),
            escapeJson(userPrompt)
        );

        try (CloseableHttpClient client = HttpClients.custom()
                .setDefaultRequestConfig(requestConfig)
                .build()) {
            
            HttpPost post = new HttpPost(ENDPOINT);
            post.setEntity(new StringEntity(requestBody, ContentType.APPLICATION_JSON));
            post.setHeader("Content-Type", "application/json");
            
            return client.execute(post, response -> {
                int statusCode = response.getCode();
                String responseBody = org.apache.hc.core5.http.io.entity.EntityUtils.toString(response.getEntity());
                
                if (statusCode != 200) {
                    throw new RuntimeException("HTTP " + statusCode + ": " + responseBody);
                }
                
                // Parse response
                JsonNode json = objectMapper.readTree(responseBody);
                JsonNode choices = json.get("choices");
                
                if (choices != null && choices.isArray() && choices.size() > 0) {
                    JsonNode message = choices.get(0).get("message");
                    if (message != null) {
                        return message.get("content").asText();
                    }
                }
                
                throw new RuntimeException("Invalid response format");
            });
        }
    }

    private static String escapeJson(String text) {
        return text.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
    }

    private static void assertEqual(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(String.format(
                "Expected '%s' but got '%s' for %s", expected, actual, message
            ));
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("Condition not true: " + message);
        }
    }

    private static void assertNotNull(Object obj, String message) {
        if (obj == null) {
            throw new AssertionError("Object is null: " + message);
        }
    }

    private static void passTest() {
        testsPassed++;
        System.out.println("  ✓ PASSED");
    }

    private static void failTest(Exception e, String... additionalMessage) {
        testsFailed++;
        String message = additionalMessage.length > 0 ? additionalMessage[0] : "";
        System.out.println("  ✗ FAILED: " + e.getMessage());
        if (!message.isEmpty()) {
            System.out.println("  " + message);
        }
        if (e.getCause() != null) {
            System.out.println("  Cause: " + e.getCause().getMessage());
        }
    }
}
