package com.reasoningtestgen.model;

import java.util.List;

/**
 * Represents a control flow graph node
 * Extended with Java 16+ features: pattern matching, lambda, assertions, etc.
 */
public record CFGNode(
    NodeType type,
    String condition,
    int line,
    Integer thenLine,
    Integer elseLine,
    CatchInfo catchBlock
) {
    public enum NodeType {
        // Basic control flow
        IF, ELSE, SWITCH, TRY, CATCH, LOOP, RETURN, THROW,
        
        // Java 16+ features
        PATTERN_MATCHING,       // instanceof with pattern
        YIELD,                  // switch expression yield
        ASSERT,                 // assert statement
        
        // Functional programming
        LAMBDA,                 // Lambda expression body
        METHOD_REF,             // Method reference
        STREAM_FILTER,          // stream.filter(), stream.peek()
        STREAM_MAP,             // stream.map()
        STREAM_FOREACH,         // stream.forEach()
        
        // Optional
        OPTIONAL_IF_PRESENT,    // optional.ifPresent()
        OPTIONAL_IF_EMPTY,      // optional.ifPresentOrElse() - empty case
        
        // Try-with-resources
        TRY_WITH_RESOURCES,
        
        // Synchronization
        SYNCHRONIZED,
        
        // Reactive (future)
        REACTIVE_FILTER,        // reactor/mono/filter
        REACTIVE_MAP,           // reactor/mono/map
        REACTIVE_ON_ERROR,      // onErrorResume, onErrorReturn
        
        // Edge cases (Java 21+)
        RECORD_PATTERN,         // instanceof Point(int x, int y)
        GUARDED_PATTERN,        // case Point(int x, int y) when x > 0
        ANONYMOUS_CLASS,        // new Interface() { ... }
        COMPACT_CONSTRUCTOR,    // record MyRecord(int x) { ... }
        
        // Enhanced multi-catch
        MULTI_CATCH,            // catch (IOException | SQLException e)
        
        // Kotlin interop
        NULL_SAFE_CALL,         // user?.getName()
        ELVIS_OPERATOR,         // value ?: default
        SAFE_CAST,              // obj as? String
    }

    public record CatchInfo(
        String exceptionType,
        int line
    ) {
    }
    
    /**
     * Get human-readable type name
     */
    public String getTypeName() {
        return switch (type) {
            case IF -> "IF";
            case ELSE -> "ELSE";
            case SWITCH -> "SWITCH";
            case TRY -> "TRY";
            case CATCH -> "CATCH";
            case LOOP -> "LOOP";
            case RETURN -> "RETURN";
            case THROW -> "THROW";
            case PATTERN_MATCHING -> "PATTERN_MATCH";
            case YIELD -> "YIELD";
            case ASSERT -> "ASSERT";
            case LAMBDA -> "LAMBDA";
            case METHOD_REF -> "METHOD_REF";
            case STREAM_FILTER -> "STREAM_FILTER";
            case STREAM_MAP -> "STREAM_MAP";
            case STREAM_FOREACH -> "STREAM_FOREACH";
            case OPTIONAL_IF_PRESENT -> "OPTIONAL_IF_PRESENT";
            case OPTIONAL_IF_EMPTY -> "OPTIONAL_IF_EMPTY";
            case TRY_WITH_RESOURCES -> "TRY_WITH_RESOURCES";
            case SYNCHRONIZED -> "SYNCHRONIZED";
            case REACTIVE_FILTER -> "REACTIVE_FILTER";
            case REACTIVE_MAP -> "REACTIVE_MAP";
            case REACTIVE_ON_ERROR -> "REACTIVE_ON_ERROR";
            case RECORD_PATTERN -> "RECORD_PATTERN";
            case GUARDED_PATTERN -> "GUARDED_PATTERN";
            case ANONYMOUS_CLASS -> "ANONYMOUS_CLASS";
            case COMPACT_CONSTRUCTOR -> "COMPACT_CONSTRUCTOR";
            case MULTI_CATCH -> "MULTI_CATCH";
            case NULL_SAFE_CALL -> "NULL_SAFE_CALL";
            case ELVIS_OPERATOR -> "ELVIS_OPERATOR";
            case SAFE_CAST -> "SAFE_CAST";
        };
    }
}
