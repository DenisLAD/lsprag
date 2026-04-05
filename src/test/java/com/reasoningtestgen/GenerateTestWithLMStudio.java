package com.reasoningtestgen;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reasoningtestgen.builder.ContextBuilder;
import com.reasoningtestgen.model.MethodContext;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.util.Timeout;
import org.apache.hc.client5.http.config.RequestConfig;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Generate test for OrderService using LM Studio
 * Shows the complete workflow
 */
public class GenerateTestWithLMStudio {

    private static final String ENDPOINT = "http://localhost:1234/v1/chat/completions";
    private static final String MODEL = "qwen/qwen3.5-9b";
    private static final String OUTPUT_FILE = "build/plugin-test-output/OrderServiceTest-generated.java";

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("Generating Test with LM Studio");
        System.out.println("========================================\n");

        try {
            // Step 1: Load prompts
            System.out.println("Step 1: Loading prompts...");
            String systemPrompt = Files.readString(
                Paths.get("build/plugin-test-output/01-system-prompt.txt")
            );
            String userPrompt = Files.readString(
                Paths.get("build/plugin-test-output/02-user-prompt.txt")
            );
            System.out.println("  ✓ Prompts loaded\n");

            // Step 2: Enhance user prompt with specific request
            String enhancedUserPrompt = userPrompt + "\n\n" + """
                Please generate a complete JUnit 5 test class for this method.
                
                Requirements:
                - Use JUnit 5 (@Test, @BeforeEach, @DisplayName)
                - Use Mockito for mocking (PricingEngine, UserRepository)
                - Use AssertJ for assertions
                - Test all scenarios:
                  1. Null user -> IllegalArgumentException
                  2. Null items -> IllegalArgumentException  
                  3. Empty items list -> return 0
                  4. VIP user -> return 50
                  5. Premium user with cart > 1000 -> return 25
                  6. Premium user with cart <= 1000 -> return 10
                  7. Regular user with cart > 2000 -> return 15
                  8. Regular user with cart > 500 -> return 5
                  9. Regular user with cart <= 500 -> return 0
                - Use nested test classes for organization
                - Follow naming: should_{expected}_when_{condition}
                
                Return ONLY the Java code, no explanations or markdown.
                Start with imports and end with closing brace.
                """;
            
            System.out.println("Step 2: Sending to LM Studio...");
            System.out.println("  Model: " + MODEL);
            System.out.println("  This may take 30-60 seconds...\n");
            
            // Step 3: Call LM Studio
            String generatedCode = callLMStudio(systemPrompt, enhancedUserPrompt);
            
            // Step 4: Save generated test
            Path outputPath = Paths.get(OUTPUT_FILE);
            Files.createDirectories(outputPath.getParent());
            Files.writeString(outputPath, generatedCode);
            
            System.out.println("\nStep 3: Test generated and saved!");
            System.out.println("  File: " + outputPath.toAbsolutePath());
            System.out.println("  Size: " + generatedCode.length() + " chars");
            System.out.println("  Lines: " + generatedCode.split("\n").length);
            
            // Step 5: Quality check
            System.out.println("\nStep 4: Quality check...");
            checkTestQuality(generatedCode);
            
            System.out.println("\n========================================");
            System.out.println("Generation Complete!");
            System.out.println("========================================");
            
        } catch (Exception e) {
            System.err.println("✗ Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static String callLMStudio(String systemPrompt, String userPrompt) throws Exception {
        RequestConfig requestConfig = RequestConfig.custom()
            .setConnectionRequestTimeout(Timeout.ofSeconds(120))
            .setResponseTimeout(Timeout.ofSeconds(120))
            .build();

        String requestBody = String.format(
            "{\"model\":\"%s\",\"messages\":[{\"role\":\"system\",\"content\":\"%s\"},{\"role\":\"user\",\"content\":\"%s\"}],\"temperature\":0.3,\"max_tokens\":4096}",
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
                String responseBody = EntityUtils.toString(response.getEntity());
                
                if (statusCode != 200) {
                    throw new RuntimeException("HTTP " + statusCode + ": " + responseBody);
                }
                
                ObjectMapper mapper = new ObjectMapper();
                var json = mapper.readTree(responseBody);
                var choices = json.get("choices");
                
                if (choices != null && choices.isArray() && choices.size() > 0) {
                    var message = choices.get(0).get("message");
                    if (message != null) {
                        String content = message.get("content").asText();
                        // Clean up markdown code blocks if present
                        return cleanCodeBlocks(content);
                    }
                }
                
                throw new RuntimeException("Invalid response format");
            });
        }
    }

    private static String cleanCodeBlocks(String code) {
        // Remove ```java ... ``` markdown blocks
        code = code.replaceAll("```java\\s*", "");
        code = code.replaceAll("```\\s*$", "");
        return code.trim();
    }

    private static String escapeJson(String text) {
        return text.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
    }

    private static void checkTestQuality(String code) {
        System.out.println("\n--- Generated Test Quality ---\n");
        
        int score = 0;
        int maxScore = 100;
        
        // Check 1: Has imports
        if (code.contains("import ")) {
            System.out.println("✓ Has imports");
            score += 10;
        } else {
            System.out.println("✗ Missing imports");
        }
        
        // Check 2: Uses JUnit 5
        if (code.contains("@Test") && code.contains("org.junit.jupiter")) {
            System.out.println("✓ Uses JUnit 5");
            score += 15;
        } else {
            System.out.println("✗ Not using JUnit 5");
        }
        
        // Check 3: Uses Mockito
        if (code.contains("@Mock") || code.contains("Mockito")) {
            System.out.println("✓ Uses Mockito");
            score += 10;
        } else {
            System.out.println("✗ Missing Mockito");
        }
        
        // Check 4: Has test for null user
        if (code.contains("null") && (code.contains("IllegalArgumentException") || code.contains("assertThrows"))) {
            System.out.println("✓ Tests null user scenario");
            score += 10;
        } else {
            System.out.println("✗ Missing null user test");
        }
        
        // Check 5: Has test for VIP
        if (code.toLowerCase().contains("vip")) {
            System.out.println("✓ Tests VIP user");
            score += 10;
        } else {
            System.out.println("✗ Missing VIP test");
        }
        
        // Check 6: Has test for empty cart
        if (code.contains("empty") || code.contains("isEmpty")) {
            System.out.println("✓ Tests empty cart");
            score += 10;
        } else {
            System.out.println("✗ Missing empty cart test");
        }
        
        // Check 7: Uses assertions
        if (code.contains("assertEquals") || code.contains("assertThat")) {
            System.out.println("✓ Has assertions");
            score += 10;
        } else {
            System.out.println("✗ Missing assertions");
        }
        
        // Check 8: Has @BeforeEach setup
        if (code.contains("@BeforeEach")) {
            System.out.println("✓ Has setup method");
            score += 10;
        } else {
            System.out.println("✗ Missing setup method");
        }
        
        // Check 9: Multiple test methods
        long testCount = code.split("@Test").length - 1;
        if (testCount >= 5) {
            System.out.println("✓ Has " + testCount + " test methods");
            score += 15;
        } else {
            System.out.println("⚠ Only " + testCount + " test methods (need 5+)");
        }
        
        // Check 10: Code compiles (basic check)
        if (code.contains("class") && code.contains("{") && code.contains("}")) {
            System.out.println("✓ Code structure looks valid");
            score += 10;
        } else {
            System.out.println("✗ Code structure issues");
        }
        
        System.out.println("\n--- Quality Score: " + score + "/" + maxScore + " ---");
        
        if (score >= 80) {
            System.out.println("✓ Excellent - Production ready");
        } else if (score >= 60) {
            System.out.println("⚠ Good - Minor improvements needed");
        } else if (score >= 40) {
            System.out.println("⚠ Fair - Needs improvements");
        } else {
            System.out.println("✗ Poor - Major revisions needed");
        }
    }
}
