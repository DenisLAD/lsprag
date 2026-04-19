package com.reasoningtestgen.model;

/**
 * Visitor interface for CFGNode processing
 * Implements Visitor pattern to avoid switch statements on node types
 * 
 * According to Open/Closed Principle - new operations can be added without modifying CFGNode
 */
public interface CFGNodeVisitor<T> {
    
    /**
     * Visit IF node
     */
    T visitIf(CFGNode node);
    
    /**
     * Visit ELSE node
     */
    T visitElse(CFGNode node);
    
    /**
     * Visit SWITCH node
     */
    T visitSwitch(CFGNode node);
    
    /**
     * Visit TRY node
     */
    T visitTry(CFGNode node);
    
    /**
     * Visit CATCH node
     */
    T visitCatch(CFGNode node);
    
    /**
     * Visit LOOP node
     */
    T visitLoop(CFGNode node);
    
    /**
     * Visit RETURN node
     */
    T visitReturn(CFGNode node);
    
    /**
     * Visit THROW node
     */
    T visitThrow(CFGNode node);
    
    /**
     * Visit PATTERN_MATCHING node (Java 16+)
     */
    T visitPatternMatching(CFGNode node);
    
    /**
     * Visit YIELD node (Java 16+)
     */
    T visitYield(CFGNode node);
    
    /**
     * Visit ASSERT node
     */
    T visitAssert(CFGNode node);
    
    /**
     * Visit LAMBDA node
     */
    T visitLambda(CFGNode node);
    
    /**
     * Visit METHOD_REF node
     */
    T visitMethodRef(CFGNode node);
    
    /**
     * Visit STREAM_FILTER node
     */
    T visitStreamFilter(CFGNode node);
    
    /**
     * Visit STREAM_MAP node
     */
    T visitStreamMap(CFGNode node);
    
    /**
     * Visit STREAM_FOREACH node
     */
    T visitStreamForeach(CFGNode node);
    
    /**
     * Visit OPTIONAL_IF_PRESENT node
     */
    T visitOptionalIfPresent(CFGNode node);
    
    /**
     * Visit OPTIONAL_IF_EMPTY node
     */
    T visitOptionalIfEmpty(CFGNode node);
    
    /**
     * Visit TRY_WITH_RESOURCES node
     */
    T visitTryWithResources(CFGNode node);
    
    /**
     * Visit SYNCHRONIZED node
     */
    T visitSynchronized(CFGNode node);
    
    /**
     * Visit REACTIVE_FILTER node
     */
    T visitReactiveFilter(CFGNode node);
    
    /**
     * Visit REACTIVE_MAP node
     */
    T visitReactiveMap(CFGNode node);
    
    /**
     * Visit REACTIVE_ON_ERROR node
     */
    T visitReactiveOnError(CFGNode node);
    
    /**
     * Visit RECORD_PATTERN node (Java 21+)
     */
    T visitRecordPattern(CFGNode node);
    
    /**
     * Visit GUARDED_PATTERN node (Java 21+)
     */
    T visitGuardedPattern(CFGNode node);
    
    /**
     * Visit ANONYMOUS_CLASS node
     */
    T visitAnonymousClass(CFGNode node);
    
    /**
     * Visit COMPACT_CONSTRUCTOR node (Java 16+)
     */
    T visitCompactConstructor(CFGNode node);
    
    /**
     * Visit MULTI_CATCH node
     */
    T visitMultiCatch(CFGNode node);
    
    /**
     * Visit NULL_SAFE_CALL node (Kotlin interop)
     */
    T visitNullSafeCall(CFGNode node);
    
    /**
     * Visit ELVIS_OPERATOR node (Kotlin interop)
     */
    T visitElvisOperator(CFGNode node);
    
    /**
     * Visit SAFE_CAST node (Kotlin interop)
     */
    T visitSafeCast(CFGNode node);
}
