package com.reasoningtestgen.model;

import java.util.List;

/**
 * Information about a DTO/POJO class
 */
public record DTOInfo(
    String className,
    String packageName,
    List<FieldInfo> fields,
    List<String> annotations,
    boolean isNested,
    String parentDTO // if this DTO contains references to other DTOs
) {
    /**
     * Field information record
     */
    public record FieldInfo(
        String name,
        String type,
        List<String> annotations,
        boolean isPrimitive,
        String getterSignature,
        String setterSignature
    ) {
    }
}
