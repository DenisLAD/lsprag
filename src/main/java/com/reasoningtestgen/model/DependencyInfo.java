package com.reasoningtestgen.model;

import java.util.List;
import java.util.Map;

/**
 * Information about a dependency including Spring implementations, Lombok, and MapStruct
 */
public record DependencyInfo(
    String name,
    String type,
    boolean isExternal,
    boolean nullable,
    String sourceCode,
    List<MethodInfo> methods,
    String interfaceContract,
    // Spring implementations (REQ 2)
    List<ImplementationInfo> springImplementations,
    // Lombok generated methods
    List<LombokMethodInfo> lombokGeneratedMethods,
    // MapStruct info
    MapStructInfo mapStructInfo
) {
    /**
     * Method information record
     */
    public record MethodInfo(
        String name,
        String returnType,
        List<String> parameters,
        List<String> exceptions,
        String javadoc
    ) {
    }
    
    /**
     * Spring implementation information
     */
    public record ImplementationInfo(
        String className,
        String packageName,
        List<String> annotations, // @Service, @Component, @Repository
        List<MethodInfo> methods,
        boolean hasSourceCode,
        String sourceSnippet
    ) {
    }
    
    /**
     * Lombok generated method information
     */
    public record LombokMethodInfo(
        String methodName,
        String generatedBy, // @Getter, @Setter, @Builder, @Data, @AllArgsConstructor
        String returnType,
        List<String> parameters
    ) {
    }
    
    /**
     * MapStruct mapper information
     */
    public record MapStructInfo(
        String mapperClassName,
        String sourceType,
        String targetType,
        List<MappingInfo> mappings,
        boolean hasImplementation
    ) {
        public record MappingInfo(
            String source,
            String target,
            String qualifiedByName
        ) {
        }
    }
}
