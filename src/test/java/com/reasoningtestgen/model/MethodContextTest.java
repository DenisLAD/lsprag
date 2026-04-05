package com.reasoningtestgen.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for MethodContext model and related records
 */
class MethodContextTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Nested
    @DisplayName("MethodContext")
    class MethodContextTests {

        @Test
        @DisplayName("Should create with all fields")
        void shouldCreateWithAllFields() {
            // Given
            List<Parameter> parameters = List.of(
                new Parameter("user", "User", true),
                new Parameter("items", "List<Item>", false)
            );
            
            List<Dependency> dependencies = List.of(
                new Dependency("pricingEngine", "PricingEngine", true, false)
            );
            
            DocContract docContract = new DocContract(
                Map.of("user", "The user"),
                "Discount percentage",
                List.of("IllegalArgumentException"),
                List.of("Must validate user")
            );
            
            ComplexityMetrics complexity = new ComplexityMetrics(4, 2, 3, 1);
            
            List<CFGNode> cfgNodes = List.of(
                new CFGNode(CFGNode.NodeType.IF, "user == null", 35, 36, 38, null)
            );
            
            // When
            MethodContext context = new MethodContext(
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
                List.of(),
                complexity
            );
            
            // Then
            assertThat(context.className()).isEqualTo("OrderService");
            assertThat(context.methodName()).isEqualTo("calculateDiscount");
            assertThat(context.returnType()).isEqualTo("double");
            assertThat(context.parameters()).hasSize(2);
            assertThat(context.dependencies()).hasSize(1);
            assertThat(context.docContract()).isNotNull();
            assertThat(context.complexity().cyclomatic()).isEqualTo(4);
        }

        @Test
        @DisplayName("Should serialize and deserialize")
        void shouldSerializeAndDeserialize() throws Exception {
            // Given
            MethodContext context = createSampleContext();
            
            // When
            String json = objectMapper.writeValueAsString(context);
            MethodContext deserialized = objectMapper.readValue(json, MethodContext.class);
            
            // Then
            assertThat(deserialized.className()).isEqualTo(context.className());
            assertThat(deserialized.methodName()).isEqualTo(context.methodName());
            assertThat(deserialized.parameters()).hasSize(context.parameters().size());
        }
    }

    @Nested
    @DisplayName("Parameter")
    class ParameterTests {

        @Test
        @DisplayName("Should create with all fields")
        void shouldCreateWithAllFields() {
            Parameter param = new Parameter("name", "String", true);
            
            assertThat(param.name()).isEqualTo("name");
            assertThat(param.type()).isEqualTo("String");
            assertThat(param.nullable()).isTrue();
        }

        @Test
        @DisplayName("Should distinguish nullable vs non-nullable")
        void shouldDistinguishNullable() {
            Parameter nullable = new Parameter("user", "User", true);
            Parameter nonNullable = new Parameter("id", "Long", false);
            
            assertThat(nullable.nullable()).isTrue();
            assertThat(nonNullable.nullable()).isFalse();
        }
    }

    @Nested
    @DisplayName("CFGNode")
    class CFGNodeTests {

        @Test
        @DisplayName("Should create IF node")
        void shouldCreateIFNode() {
            CFGNode node = new CFGNode(CFGNode.NodeType.IF, "user == null", 35, 36, 38, null);
            
            assertThat(node.type()).isEqualTo(CFGNode.NodeType.IF);
            assertThat(node.condition()).isEqualTo("user == null");
            assertThat(node.line()).isEqualTo(35);
            assertThat(node.thenLine()).isEqualTo(36);
            assertThat(node.elseLine()).isEqualTo(38);
        }

        @Test
        @DisplayName("Should create CATCH node")
        void shouldCreateCATCHNode() {
            CFGNode.CatchInfo catchInfo = new CFGNode.CatchInfo("Exception", 42);
            CFGNode node = new CFGNode(
                CFGNode.NodeType.CATCH,
                null,
                40,
                null,
                null,
                catchInfo
            );
            
            assertThat(node.type()).isEqualTo(CFGNode.NodeType.CATCH);
            assertThat(node.catchBlock()).isNotNull();
            assertThat(node.catchBlock().exceptionType()).isEqualTo("Exception");
            assertThat(node.catchBlock().line()).isEqualTo(42);
        }

        @Test
        @DisplayName("Should create SWITCH node")
        void shouldCreateSWITCHNode() {
            CFGNode node = new CFGNode(
                CFGNode.NodeType.SWITCH,
                "status -> case PENDING:",
                50,
                null,
                null,
                null
            );
            
            assertThat(node.type()).isEqualTo(CFGNode.NodeType.SWITCH);
            assertThat(node.condition()).contains("case PENDING");
        }

        @Test
        @DisplayName("Should create ternary IF node")
        void shouldCreateTernaryNode() {
            CFGNode node = new CFGNode(
                CFGNode.NodeType.IF,
                "ternary: flag ? ... : ...",
                60,
                null,
                null,
                null
            );
            
            assertThat(node.condition()).startsWith("ternary:");
            assertThat(node.type()).isEqualTo(CFGNode.NodeType.IF);
        }
    }

    @Nested
    @DisplayName("Dependency")
    class DependencyTests {

        @Test
        @DisplayName("Should identify external dependencies")
        void shouldIdentifyExternalDependencies() {
            Dependency external = new Dependency("service", "ExternalService", true, false);
            Dependency internal = new Dependency("field", "String", false, false);
            
            assertThat(external.isExternal()).isTrue();
            assertThat(internal.isExternal()).isFalse();
        }

        @Test
        @DisplayName("Should identify nullable dependencies")
        void shouldIdentifyNullableDependencies() {
            Dependency nullable = new Dependency("user", "User", true, true);
            Dependency nonNullable = new Dependency("id", "Long", false, false);
            
            assertThat(nullable.nullable()).isTrue();
            assertThat(nonNullable.nullable()).isFalse();
        }
    }

    @Nested
    @DisplayName("DocContract")
    class DocContractTests {

        @Test
        @DisplayName("Should handle full documentation")
        void shouldHandleFullDocumentation() {
            DocContract contract = new DocContract(
                Map.of("param1", "Description 1", "param2", "Description 2"),
                "Return value description",
                List.of("IOException", "SQLException"),
                List.of("Must always validate", "Never return null")
            );
            
            assertThat(contract.params()).hasSize(2);
            assertThat(contract.returns()).isEqualTo("Return value description");
            assertThat(contract.throwsList()).hasSize(2);
            assertThat(contract.businessRules()).hasSize(2);
        }

        @Test
        @DisplayName("Should handle empty documentation")
        void shouldHandleEmptyDocumentation() {
            DocContract contract = new DocContract(
                Map.of(),
                null,
                List.of(),
                List.of()
            );
            
            assertThat(contract.params()).isEmpty();
            assertThat(contract.returns()).isNull();
            assertThat(contract.throwsList()).isEmpty();
            assertThat(contract.businessRules()).isEmpty();
        }
    }

    @Nested
    @DisplayName("ComplexityMetrics")
    class ComplexityMetricsTests {

        @Test
        @DisplayName("Should calculate all metrics")
        void shouldCalculateAllMetrics() {
            ComplexityMetrics metrics = new ComplexityMetrics(9, 2, 9, 1);
            
            assertThat(metrics.cyclomatic()).isEqualTo(9);
            assertThat(metrics.nestingDepth()).isEqualTo(2);
            assertThat(metrics.branchCount()).isEqualTo(9);
            assertThat(metrics.loopCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("Should handle simple method")
        void shouldHandleSimpleMethod() {
            ComplexityMetrics metrics = new ComplexityMetrics(1, 0, 0, 0);
            
            assertThat(metrics.cyclomatic()).isEqualTo(1);
            assertThat(metrics.nestingDepth()).isEqualTo(0);
        }
    }

    private MethodContext createSampleContext() {
        return new MethodContext(
            "TestService",
            "testMethod",
            "String",
            List.of(new Parameter("input", "String", false)),
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
