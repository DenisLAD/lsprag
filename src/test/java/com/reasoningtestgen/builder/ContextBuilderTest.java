package com.reasoningtestgen.builder;

import com.reasoningtestgen.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for ContextBuilder
 */
class ContextBuilderTest {

    private ContextBuilder contextBuilder;

    @BeforeEach
    void setUp() {
        contextBuilder = new ContextBuilder();
    }

    @Nested
    @DisplayName("Prompt Bundle")
    class PromptBundleTests {

        @Test
        @DisplayName("Should build prompt bundle with all components")
        void shouldBuildPromptBundle() {
            MethodContext context = createSampleMethodContext();
            
            PromptBundle bundle = contextBuilder.buildPromptBundle(context);
            
            assertThat(bundle).isNotNull();
            assertThat(bundle.systemPrompt()).isNotEmpty();
            assertThat(bundle.userPrompt()).isNotEmpty();
            assertThat(bundle.examples()).isNotNull();
        }

        @Test
        @DisplayName("System prompt should contain role and guidelines")
        void systemPromptShouldContainRoleAndGuidelines() {
            MethodContext context = createSampleMethodContext();
            
            PromptBundle bundle = contextBuilder.buildPromptBundle(context);
            
            assertThat(bundle.systemPrompt())
                .contains("Senior Test Engineer")
                .contains("happy path")
                .contains("edge cases");
        }

        @Test
        @DisplayName("User prompt should contain method signature")
        void userPromptShouldContainMethodSignature() {
            MethodContext context = createSampleMethodContext();
            
            PromptBundle bundle = contextBuilder.buildPromptBundle(context);
            
            assertThat(bundle.userPrompt())
                .contains("OrderService")
                .contains("calculateDiscount")
                .contains("User")
                .contains("List<Item>");
        }

        @Test
        @DisplayName("User prompt should contain control flow graph")
        void userPromptShouldContainCFG() {
            MethodContext context = createSampleMethodContext();
            
            PromptBundle bundle = contextBuilder.buildPromptBundle(context);
            
            assertThat(bundle.userPrompt())
                .contains("Граф потока управления")
                .contains("if (user == null)");
        }

        @Test
        @DisplayName("User prompt should contain dependencies")
        void userPromptShouldContainDependencies() {
            MethodContext context = createSampleMethodContext();
            
            PromptBundle bundle = contextBuilder.buildPromptBundle(context);
            
            assertThat(bundle.userPrompt())
                .contains("Зависимости")
                .contains("pricingEngine");
        }

        @Test
        @DisplayName("User prompt should contain complexity metrics")
        void userPromptShouldContainComplexity() {
            MethodContext context = createSampleMethodContext();
            
            PromptBundle bundle = contextBuilder.buildPromptBundle(context);
            
            assertThat(bundle.userPrompt())
                .contains("Метрики сложности")
                .contains("Цикломатическая сложность: 4");
        }

        @Test
        @DisplayName("Should serialize context to JSON")
        void shouldSerializeContextToJson() {
            MethodContext context = createSampleMethodContext();
            
            String json = contextBuilder.contextToJson(context);
            
            assertThat(json).isNotEmpty();
            assertThat(json).contains("OrderService");
            assertThat(json).contains("calculateDiscount");
        }

        @Test
        @DisplayName("Should handle empty existing tests")
        void shouldHandleEmptyExistingTests() {
            MethodContext context = createMethodContextWithNoTests();
            
            PromptBundle bundle = contextBuilder.buildPromptBundle(context);
            
            assertThat(bundle).isNotNull();
            assertThat(bundle.examples()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCasesTests {

        @Test
        @DisplayName("Should handle method with no parameters")
        void shouldHandleMethodWithNoParameters() {
            MethodContext context = new MethodContext(
                "SimpleService",
                "doSomething",
                "void",
                List.of(),
                List.of(),
                new MethodContext.ControlFlow(List.of()),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                new DocContract(Map.of(), null, List.of(), List.of()),
                List.of(),
                new ComplexityMetrics(1, 0, 0, 0)
            );
            
            PromptBundle bundle = contextBuilder.buildPromptBundle(context);
            
            assertThat(bundle.userPrompt()).contains("Параметры:");
        }

        @Test
        @DisplayName("Should handle method with complex CFG")
        void shouldHandleMethodWithComplexCFG() {
            List<CFGNode> cfgNodes = List.of(
                new CFGNode(CFGNode.NodeType.IF, "user == null", 10, 11, 14, null),
                new CFGNode(CFGNode.NodeType.IF, "items.isEmpty()", 20, 21, 24, null),
                new CFGNode(CFGNode.NodeType.SWITCH, "status -> case PENDING:", 30, null, null, null)
            );
            
            MethodContext context = new MethodContext(
                "ComplexService",
                "complexMethod",
                "String",
                List.of(new Parameter("user", "User", false)),
                List.of(),
                new MethodContext.ControlFlow(cfgNodes),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                new DocContract(Map.of(), null, List.of(), List.of()),
                List.of(),
                new ComplexityMetrics(5, 2, 5, 0)
            );
            
            PromptBundle bundle = contextBuilder.buildPromptBundle(context);
            
            assertThat(bundle.userPrompt())
                .contains("if (user == null)")
                .contains("if (items.isEmpty())")
                .contains("switch status -> case PENDING:");
        }
    }

    // ===== Helper Methods =====

    private MethodContext createSampleMethodContext() {
        List<Parameter> parameters = List.of(
            new Parameter("user", "User", true),
            new Parameter("items", "List<Item>", false)
        );
        
        List<Dependency> dependencies = List.of(
            new Dependency("pricingEngine", "PricingEngine", true, false)
        );
        
        DocContract docContract = new DocContract(
            Map.of("user", "The user requesting discount"),
            "Discount percentage from 0 to 50",
            List.of("IllegalArgumentException if user is null"),
            List.of("Must always validate user")
        );
        
        List<ExistingTestInfo> existingTests = List.of(
            new ExistingTestInfo("shouldApplyFullDiscountForVIP", 
                List.of("assertEquals"), "JUNIT5", List.of())
        );
        
        ComplexityMetrics complexity = new ComplexityMetrics(4, 2, 3, 1);
        
        List<CFGNode> cfgNodes = List.of(
            new CFGNode(CFGNode.NodeType.IF, "user == null", 10, 11, 14, null)
        );
        
        return new MethodContext(
            "OrderService",
            "calculateDiscount",
            "double",
            parameters,
            List.of("@Transactional"),
            new MethodContext.ControlFlow(cfgNodes),
            dependencies,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            docContract,
            existingTests,
            complexity
        );
    }
    
    private MethodContext createMethodContextWithNoTests() {
        return new MethodContext(
            "SimpleService",
            "doSomething",
            "void",
            List.of(),
            List.of(),
            new MethodContext.ControlFlow(List.of()),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            new DocContract(Map.of(), null, List.of(), List.of()),
            List.of(),
            new ComplexityMetrics(1, 0, 0, 0)
        );
    }
}
