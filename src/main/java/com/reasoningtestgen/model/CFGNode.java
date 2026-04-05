package com.reasoningtestgen.model;

import java.util.List;

/**
 * Represents a control flow graph node
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
        IF, ELSE, SWITCH, TRY, CATCH, LOOP, RETURN, THROW
    }

    public record CatchInfo(
        String exceptionType,
        int line
    ) {
    }
}
