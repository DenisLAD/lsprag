package com.reasoningtestgen.test;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiManager;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.reasoningtestgen.extractor.PSIExtractor;
import com.reasoningtestgen.model.MethodContext;
import com.reasoningtestgen.model.CFGNode;
import com.reasoningtestgen.builder.ContextBuilder;
import com.reasoningtestgen.model.PromptBundle;

import java.util.List;

/**
 * Test CFG extraction and context building
 */
public class CFGExtractionTest extends LightJavaCodeInsightFixtureTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    /**
     * Test CFG extraction from calculateDiscount method
     */
    public void testCalculateDiscountCFG() {
        // Configure test file
        myFixture.configureByText("OrderService.java", """
            package com.example.service;
            
            import java.util.Optional;
            
            @Service
            public class OrderService {
                
                private final UserRepository userRepository;
                
                public OrderService(UserRepository userRepository) {
                    this.userRepository = userRepository;
                }
                
                /**
                 * Calculate discount for user
                 * @param userId The user ID
                 * @param amount Order amount
                 * @return Final amount after discount
                 * @throws IllegalArgumentException if user not found
                 */
                public double calculateDiscount(Long userId, double amount) {
                    if (amount <= 0) {
                        throw new IllegalArgumentException("Amount must be positive");
                    }
                    
                    Optional<User> userOpt = userRepository.findById(userId);
                    
                    if (userOpt.isEmpty()) {
                        throw new IllegalArgumentException("User not found");
                    }
                    
                    User user = userOpt.get();
                    
                    if (user.isBlocked()) {
                        throw new IllegalStateException("User is blocked");
                    }
                    
                    double discount = user.isVip() ? 0.5 : 0.1;
                    
                    if (amount > 1000) {
                        discount += 0.1;
                    } else if (amount > 500) {
                        discount += 0.05;
                    }
                    
                    if (discount > 0.6) {
                        discount = 0.6;
                    }
                    
                    double finalAmount = amount * (1 - discount);
                    return Math.round(finalAmount * 100.0) / 100.0;
                }
            }
            
            @interface Service {}
            class UserRepository {
                public Optional<User> findById(Long id) { return Optional.empty(); }
            }
            class User {
                public boolean isBlocked() { return false; }
                public boolean isVip() { return false; }
            }
        """);

        // Get PSI elements
        PsiFile file = myFixture.getFile();
        assertTrue("File should be PsiJavaFile", file instanceof PsiJavaFile);
        
        PsiJavaFile javaFile = (PsiJavaFile) file;
        PsiClass[] classes = javaFile.getClasses();
        
        assertEquals("Should have 4 classes", 4, classes.length);
        
        PsiClass orderServiceClass = classes[0];
        assertEquals("OrderService", orderServiceClass.getName());
        
        // Get calculateDiscount method
        PsiMethod method = orderServiceClass.findMethodsByName("calculateDiscount", false)[0];
        assertNotNull("Method should exist", method);

        // Extract context
        PSIExtractor extractor = new PSIExtractor();
        MethodContext context = extractor.extract(method);

        // Verify basic info
        assertEquals("OrderService", context.className());
        assertEquals("calculateDiscount", context.methodName());
        assertEquals("double", context.returnType());
        
        // Verify parameters
        assertEquals(2, context.parameters().size());
        assertEquals("userId", context.parameters().get(0).name());
        assertEquals("Long", context.parameters().get(0).type());
        assertEquals("amount", context.parameters().get(1).name());
        assertEquals("double", context.parameters().get(1).type());

        // Verify CFG
        assertNotNull("CFG should not be null", context.controlFlow());
        List<CFGNode> cfgNodes = context.controlFlow().nodes();
        
        System.out.println("\n===== CFG NODES =====");
        System.out.println("Total nodes: " + cfgNodes.size());
        
        for (CFGNode node : cfgNodes) {
            System.out.println(String.format(
                "  [%s] Line %d: %s (then: %s, else: %s)",
                node.type(),
                node.line(),
                node.condition(),
                node.thenLine() != null ? node.thenLine().toString() : "null",
                node.elseLine() != null ? node.elseLine().toString() : "null"
            ));
        }

        // Expected nodes:
        // 1. if (amount <= 0)
        // 2. if (userOpt.isEmpty())
        // 3. if (user.isBlocked())
        // 4. ternary: user.isVip()
        // 5. if (amount > 1000)
        // 6. if (amount > 500) - else if
        // 7. if (discount > 0.6)
        
        assertTrue("Should have at least 5 CFG nodes", cfgNodes.size() >= 5);
        
        // Verify IF statements
        long ifCount = cfgNodes.stream()
            .filter(node -> node.type() == CFGNode.NodeType.IF)
            .count();
        
        System.out.println("IF nodes: " + ifCount);
        assertTrue("Should have at least 4 IF nodes", ifCount >= 4);
        
        // Verify specific conditions
        boolean hasAmountCheck = cfgNodes.stream()
            .anyMatch(node -> node.condition() != null && 
                            node.condition().contains("amount"));
        assertTrue("Should have amount check", hasAmountCheck);
        
        boolean hasVipCheck = cfgNodes.stream()
            .anyMatch(node -> node.condition() != null && 
                            (node.condition().contains("isVip") || 
                             node.condition().contains("vip")));
        System.out.println("Has VIP check: " + hasVipCheck);
        
        boolean hasBlockedCheck = cfgNodes.stream()
            .anyMatch(node -> node.condition() != null && 
                            node.condition().contains("isBlocked"));
        System.out.println("Has blocked check: " + hasBlockedCheck);
    }

    /**
     * Test context builder prompt generation
     */
    public void testContextBuilderPrompt() {
        // Configure test file
        myFixture.configureByText("TestService.java", """
            public class TestService {
                public String process(String input) {
                    if (input == null) {
                        return "default";
                    }
                    if (input.length() > 10) {
                        return input.substring(0, 10);
                    }
                    return input;
                }
            }
        """);

        // Get method
        PsiFile file = myFixture.getFile();
        PsiClass clazz = ((PsiJavaFile) file).getClasses()[0];
        PsiMethod method = clazz.findMethodsByName("process", false)[0];

        // Extract context
        PSIExtractor extractor = new PSIExtractor();
        MethodContext context = extractor.extract(method);

        // Build prompt
        ContextBuilder builder = new ContextBuilder();
        PromptBundle promptBundle = builder.buildPromptBundle(context);

        System.out.println("\n===== PROMPT PREVIEW =====");
        System.out.println("System prompt length: " + promptBundle.systemPrompt().length());
        System.out.println("User prompt length: " + promptBundle.userPrompt().length());
        
        // Check for key sections
        String fullPrompt = promptBundle.systemPrompt() + "\n\n" + promptBundle.userPrompt();
        
        assertTrue("Should contain CFG section", fullPrompt.contains("CFG"));
        assertTrue("Should contain control flow", fullPrompt.contains("Control Flow"));
        assertTrue("Should contain parameters", fullPrompt.contains("Parameters"));
        
        // Print CFG section
        int cfgStart = fullPrompt.indexOf("## 🗺️ Control Flow Graph");
        if (cfgStart != -1) {
            int cfgEnd = fullPrompt.indexOf("\n\n", cfgStart + 10);
            String cfgSection = fullPrompt.substring(cfgStart, Math.min(cfgEnd, cfgStart + 500));
            System.out.println("\n===== CFG SECTION =====");
            System.out.println(cfgSection);
        }
    }

    /**
     * Test switch statement CFG
     */
    public void testSwitchStatementCFG() {
        myFixture.configureByText("StatusProcessor.java", """
            public class StatusProcessor {
                public String process(String status) {
                    return switch (status) {
                        case "NEW" -> "Created";
                        case "PAID" -> "Paid";
                        case "SHIPPED" -> "Shipped";
                        default -> "Unknown";
                    };
                }
            }
        """);

        PsiFile file = myFixture.getFile();
        PsiClass clazz = ((PsiJavaFile) file).getClasses()[0];
        PsiMethod method = clazz.findMethodsByName("process", false)[0];

        PSIExtractor extractor = new PSIExtractor();
        MethodContext context = extractor.extract(method);

        List<CFGNode> cfgNodes = context.controlFlow().nodes();
        
        System.out.println("\n===== SWITCH CFG =====");
        System.out.println("Total nodes: " + cfgNodes.size());
        
        for (CFGNode node : cfgNodes) {
            System.out.println(String.format(
                "  [%s] Line %d: %s",
                node.type(),
                node.line(),
                node.condition()
            ));
        }
        
        // Should have SWITCH nodes
        long switchCount = cfgNodes.stream()
            .filter(node -> node.type() == CFGNode.NodeType.SWITCH)
            .count();
        
        System.out.println("SWITCH nodes: " + switchCount);
        assertTrue("Should have SWITCH nodes", switchCount > 0);
    }
}
