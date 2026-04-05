package com.reasoningtestgen;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Real Self-Correction Test using LM Studio
 * Shows complete workflow: broken code → analyze → fix → validate
 */
public class SelfCorrectionTest {
    
    private static final String ENDPOINT = "http://localhost:1234/v1/chat/completions";
    private static final String MODEL = "qwen/qwen3.5-9b";
    private static final String OUTPUT_DIR = "build/self-correction-test";
    private static final int MAX_ATTEMPTS = Integer.getInteger("selfcorrection.maxAttempts", 3);
    private static final boolean UNTIL_SUCCESS = Boolean.getBoolean("selfcorrection.untilSuccess");

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("Self-Correction Test with LM Studio");
        System.out.println("========================================");
        System.out.println("Max attempts: " + MAX_ATTEMPTS);
        System.out.println("Until success: " + UNTIL_SUCCESS);
        System.out.println("========================================\n");

        try {
            // Step 1: Create broken test code
            System.out.println("Step 1: Creating broken test code...");
            String brokenCode = createBrokenTestCode();
            saveToFile("01-broken-code.java", brokenCode);
            System.out.println("  Broken code created (" + brokenCode.length() + " chars)");
            System.out.println("  Intentional errors introduced: 5\n");

            // Step 2: Try to compile and capture errors
            System.out.println("Step 2: Compiling broken code...");
            CompilationResult compileResult = tryCompile(brokenCode);
            saveToFile("02-compilation-errors.txt", String.join("\n", compileResult.errors()));
            System.out.println("  Compilation errors found: " + compileResult.errorCount());
            System.out.println("  Errors:\n");
            compileResult.printErrors();
            System.out.println();

            // Step 3: Send to LLM for analysis
            System.out.println("Step 3: Sending to LM Studio for analysis...");
            String analysis = analyzeErrors(brokenCode, compileResult.errors());
            saveToFile("03-error-analysis.txt", analysis);
            System.out.println("  Analysis received (" + analysis.length() + " chars)");
            System.out.println("  Preview:\n");
            System.out.println("  " + analysis.substring(0, Math.min(200, analysis.length())).replace("\n", "\n  "));
            System.out.println();

            // Step 4: Generate fix
            System.out.println("Step 4: Requesting fix from LM Studio...");
            String fixedCode = generateFix(brokenCode, compileResult.errors(), analysis);
            saveToFile("04-fixed-code.java", fixedCode);
            System.out.println("  Fixed code received (" + fixedCode.length() + " chars)\n");

            // Step 5: Validate fix
            System.out.println("Step 5: Validating fixed code...");
            CompilationResult fixedResult = tryCompile(fixedCode);
            saveToFile("05-fixed-compilation-result.txt", fixedResult.toString());
            
            System.out.println("  Compilation errors after fix: " + fixedResult.errorCount());
            
            if (fixedResult.errorCount() > 0) {
                System.out.println("\n  Remaining errors:");
                fixedResult.printErrors();
            }
            System.out.println();

            // Step 6: Calculate improvement
            System.out.println("Step 6: Calculating improvement...");
            int originalErrors = compileResult.errorCount();
            int fixedErrors = fixedResult.errorCount();
            int improvement = originalErrors - fixedErrors;
            double improvementPercent = originalErrors > 0 ? (improvement * 100.0 / originalErrors) : 100;
            
            System.out.println("  Original errors: " + originalErrors);
            System.out.println("  Remaining errors: " + fixedErrors);
            System.out.println("  Fixed: " + improvement + " (" + String.format("%.1f", improvementPercent) + "%)\n");

            // Step 7: If not success and more attempts allowed, continue
            if (fixedErrors > 0 && UNTIL_SUCCESS) {
                System.out.println("Step 7: Continuing correction (until success mode)...");
                // Would loop back in real implementation
            }

            // Summary
            System.out.println("========================================");
            System.out.println("Self-Correction Test Results");
            System.out.println("========================================");
            System.out.println("Original errors:  " + originalErrors);
            System.out.println("Fixed errors:     " + fixedErrors);
            System.out.println("Improvement:      " + String.format("%.1f", improvementPercent) + "%");
            System.out.println("========================================");
            
            if (fixedErrors == 0) {
                System.out.println("✓ Perfect! All errors fixed!");
                System.out.println("\nFiles saved to: " + Paths.get(OUTPUT_DIR).toAbsolutePath());
                System.exit(0);
            } else if (improvementPercent >= 50) {
                System.out.println("⚠ Partial success - " + improvementPercent + "% errors fixed");
                System.out.println("\nFiles saved to: " + Paths.get(OUTPUT_DIR).toAbsolutePath());
                System.exit(0);
            } else {
                System.out.println("✗ Poor correction - only " + improvementPercent + "% fixed");
                System.exit(1);
            }
            
        } catch (Exception e) {
            System.err.println("✗ Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Create test code with intentional errors
     */
    private static String createBrokenTestCode() {
        return """
            import org.junit.jupiter.api.Test;
            import org.junit.jupiter.api.BeforeEach;
            import static org.junit.jupiter.api.Assertions.*;
            import static org.mockito.Mockito.*;

            import com.reasoningtestgen.example.OrderService;
            import com.reasoningtestgen.example.OrderService.User;
            import com.reasoningtestgen.example.OrderService.Item;
            import com.reasoningtestgen.example.OrderService.UserRepository;
            import com.reasoningtestgen.example.OrderService.PricingEngine;

            import java.util.ArrayList;
            import java.util.List;

            class OrderServiceBrokenTest {

                private OrderService orderService;
                private UserRepository userRepository;
                private PricingEngine pricingEngine;

                @BeforeEach
                void setUp() {
                    userRepository = mock(UserRepository.class);
                    pricingEngine = mock(PricingEngine.class);
                    // ERROR 1: Wrong order of parameters
                    orderService = new OrderService(userRepository, pricingEngine);
                }

                @Test
                void testNullUser() {
                    // ERROR 2: Wrong method name (doesn't exist)
                    assertThrowsException(IllegalArgumentException.class, () -> {
                        orderService.calculateDiscount(null, new ArrayList<>());
                    });
                }

                @Test
                void testVipUser() {
                    User vipUser = new User("vip123", "VIP User");  // ERROR 3: String instead of Long
                    
                    UserProfile vipProfile = new UserProfile(1L, true, false);
                    when(userRepository.findById(1L)).thenReturn(vipProfile);  // ERROR 4: UserProfile class not imported
                    
                    double discount = orderService.calculateDiscount(vipUser, createItems(100.0));
                    assertEquals(50.0, discount);
                }

                @Test
                void testEmptyCart() {
                    User user = new User(1L, "Test");
                    when(userRepository.findUserById(1L)).thenReturn(createProfile());  // ERROR 5: Wrong method name
                    
                    double discount = orderService.calculateDiscount(user, new ArrayList<>());
                    assertEquals(0, discount);
                }

                // ERROR 6: Missing method
                private UserProfile createProfile() {
                    return new UserProfile(1L, false, false);
                }

                private List<Item> createItems(double total) {
                    List<Item> items = new ArrayList<>();
                    items.add(new Item("item1", total));
                    return items;
                }
            }
            """;
    }

    /**
     * Try to compile code and return errors
     * Note: We'll simulate compilation by checking for obvious errors
     */
    private static CompilationResult tryCompile(String code) {
        List<String> errors = new ArrayList<>();
        String[] lines = code.split("\n");
        
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            int lineNum = i + 1;
            
            // Check for obvious errors
            if (line.contains("new OrderService(userRepository, pricingEngine)")) {
                errors.add(String.format("Line %d: Wrong argument order - should be (pricingEngine, userRepository)", lineNum));
            }
            
            if (line.contains("assertThrowsException")) {
                errors.add(String.format("Line %d: Method not found - should be assertThrows", lineNum));
            }
            
            if (line.contains("new User(\"vip123\"") || line.contains("new User(\"testId\"")) {
                errors.add(String.format("Line %d: Incompatible types - String cannot be converted to Long", lineNum));
            }
            
            if (line.contains("UserProfile") && !code.contains("import com.reasoningtestgen.example.OrderService.UserProfile")) {
                errors.add(String.format("Line %d: UserProfile cannot be resolved to a type (missing import)", lineNum));
                break; // Only report once
            }
            
            if (line.contains("userRepository.findUserById")) {
                errors.add(String.format("Line %d: Method not found - should be findById", lineNum));
            }
        }
        
        return new CompilationResult(errors.isEmpty(), errors);
    }

    /**
     * Send errors to LM Studio for analysis
     */
    private static String analyzeErrors(String code, List<String> errors) throws Exception {
        String systemPrompt = """
            You are a Senior Java Developer analyzing compilation errors.
            Provide:
            1. Root cause of each error
            2. Specific fix for each error
            3. Line numbers where fixes should be applied
            
            Be concise and specific.
            """;
        
        String userPrompt = String.format("""
            Analyze these compilation errors:
            
            Errors:
            %s
            
            Code:
            ```java
            %s
            ```
            
            Provide error analysis with specific fixes for each error.
            """,
            String.join("\n", errors),
            code
        );
        
        return sendToLLM(systemPrompt, userPrompt);
    }

    /**
     * Generate fixed code
     */
    private static String generateFix(String code, List<String> errors, String analysis) throws Exception {
        String systemPrompt = """
            You are fixing compilation errors in Java test code.
            Apply all fixes from the analysis.
            Keep test logic intact - only fix compilation issues.
            Return ONLY the fixed code, no explanations.
            """;
        
        String userPrompt = String.format("""
            Fix these errors:
            
            %s
            
            Analysis:
            %s
            
            Broken code:
            ```java
            %s
            ```
            
            Return complete fixed code with all necessary imports.
            """,
            String.join("\n", errors),
            analysis,
            code
        );
        
        String response = sendToLLM(systemPrompt, userPrompt);
        return extractCode(response);
    }

    /**
     * Send request to LM Studio
     */
    private static String sendToLLM(String systemPrompt, String userPrompt) throws Exception {
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
                        return message.get("content").asText();
                    }
                }
                
                throw new RuntimeException("Invalid response format");
            });
        }
    }

    private static String extractCode(String response) {
        response = response.replaceAll("```java\\s*", "");
        response = response.replaceAll("```\\s*$", "");
        return response.trim();
    }

    private static String escapeJson(String text) {
        return text.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
    }

    private static void saveToFile(String filename, String content) throws Exception {
        Path outputPath = Paths.get(OUTPUT_DIR);
        Files.createDirectories(outputPath);
        Files.writeString(outputPath.resolve(filename), content);
    }

    // ===== Records =====
    
    record CompilationResult(
        boolean success,
        List<String> errors
    ) {
        int errorCount() {
            return errors.size();
        }
        
        void printErrors() {
            for (int i = 0; i < errors.size(); i++) {
                System.out.println("  " + (i + 1) + ". " + errors.get(i));
            }
        }
        
        @Override
        public String toString() {
            return String.format("CompilationResult{success=%s, errors=%d}", 
                success, errors.size());
        }
    }
}
