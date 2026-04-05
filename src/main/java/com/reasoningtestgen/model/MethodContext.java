package com.reasoningtestgen.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Central model for method context extracted via PSI
 * According to ANALYTICS.md Section 5.1
 * Extended with REQ 1-5 requirements
 */
public record MethodContext(
    @JsonProperty("className") String className,
    @JsonProperty("methodName") String methodName,
    @JsonProperty("returnType") String returnType,
    @JsonProperty("parameters") List<Parameter> parameters,
    @JsonProperty("annotations") List<String> annotations,
    @JsonProperty("controlFlow") ControlFlow controlFlow,
    @JsonProperty("dependencies") List<Dependency> dependencies,
    @JsonProperty("dependenciesInfo") List<DependencyInfo> dependenciesInfo,
    @JsonProperty("calledMethods") List<CalledMethodInfo> calledMethods,
    @JsonProperty("dtoStructures") List<DTOInfo> dtoStructures,
    @JsonProperty("dataTransformations") List<DataTransformation> dataTransformations,
    @JsonProperty("docContract") DocContract docContract,
    @JsonProperty("existingTests") List<ExistingTestInfo> existingTests,
    @JsonProperty("complexity") ComplexityMetrics complexity,
    @JsonProperty("isRestController") boolean isRestController,
    @JsonProperty("isSpringService") boolean isSpringService,
    @JsonProperty("isSpringRepository") boolean isSpringRepository,
    @JsonProperty("sourceCode") String sourceCode,
    @JsonProperty("coverageInfo") CoverageInfo coverageInfo
) {
    public record ControlFlow(
        @JsonProperty("nodes") List<CFGNode> nodes
    ) {
    }
    
    /**
     * Create a copy with updated coverage info
     */
    public MethodContext withCoverageInfo(CoverageInfo coverage) {
        return new MethodContext(
            className, methodName, returnType, parameters, annotations,
            controlFlow, dependencies, dependenciesInfo, calledMethods,
            dtoStructures, dataTransformations, docContract, existingTests,
            complexity, isRestController, isSpringService, isSpringRepository,
            sourceCode, coverage
        );
    }
}
