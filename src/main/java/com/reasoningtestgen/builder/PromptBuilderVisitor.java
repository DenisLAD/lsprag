package com.reasoningtestgen.builder;

import com.reasoningtestgen.model.CFGNode;
import com.reasoningtestgen.model.CFGNodeVisitor;
import org.jetbrains.annotations.NotNull;

/**
 * Visitor for building prompt text from CFG nodes
 * Replaces switch statements with polymorphic dispatch
 */
public class PromptBuilderVisitor implements CFGNodeVisitor<String> {

    private final StringBuilder prompt = new StringBuilder();
    private int testNum = 1;

    @Override
    public String visitIf(@NotNull CFGNode node) {
        prompt.append(String.format("%d. Тест: условие '%s' → TRUE (then branch)\n", testNum++, node.condition()));
        prompt.append(String.format("%d. Тест: условие '%s' → FALSE (else branch)\n", testNum++, node.condition()));
        return prompt.toString();
    }

    @Override
    public String visitElse(@NotNull CFGNode node) {
        prompt.append(String.format("%d. Тест: else branch at line %d\n", testNum++, node.line()));
        return prompt.toString();
    }

    @Override
    public String visitSwitch(@NotNull CFGNode node) {
        prompt.append(String.format("%d. Тест: switch '%s' → каждый case\n", testNum++, node.condition()));
        return prompt.toString();
    }

    @Override
    public String visitTry(@NotNull CFGNode node) {
        prompt.append(String.format("%d. Тест: try block at line %d\n", testNum++, node.line()));
        return prompt.toString();
    }

    @Override
    public String visitCatch(@NotNull CFGNode node) {
        if (node.catchBlock() != null) {
            prompt.append(String.format("%d. Тест: exception '%s' caught at line %d\n", 
                testNum++, node.catchBlock().exceptionType(), node.catchBlock().line()));
        }
        return prompt.toString();
    }

    @Override
    public String visitLoop(@NotNull CFGNode node) {
        prompt.append(String.format("%d. Тест: loop '%s' → iterations\n", testNum++, node.condition()));
        return prompt.toString();
    }

    @Override
    public String visitReturn(@NotNull CFGNode node) {
        prompt.append(String.format("%d. Тест: return at line %d\n", testNum++, node.line()));
        return prompt.toString();
    }

    @Override
    public String visitThrow(@NotNull CFGNode node) {
        prompt.append(String.format("%d. Тест: throw '%s' at line %d\n", testNum++, node.condition(), node.line()));
        return prompt.toString();
    }

    @Override
    public String visitPatternMatching(@NotNull CFGNode node) {
        prompt.append(String.format("%d. Тест: pattern matching '%s' → TRUE (match)\n", testNum++, node.condition()));
        prompt.append(String.format("%d. Тест: pattern matching '%s' → FALSE (no match)\n", testNum++, node.condition()));
        return prompt.toString();
    }

    @Override
    public String visitYield(@NotNull CFGNode node) {
        prompt.append(String.format("%d. Тест: yield '%s' at line %d\n", testNum++, node.condition(), node.line()));
        return prompt.toString();
    }

    @Override
    public String visitAssert(@NotNull CFGNode node) {
        prompt.append(String.format("%d. Тест: assertion '%s' → TRUE\n", testNum++, node.condition()));
        prompt.append(String.format("%d. Тест: assertion '%s' → FALSE\n", testNum++, node.condition()));
        return prompt.toString();
    }

    @Override
    public String visitLambda(@NotNull CFGNode node) {
        prompt.append(String.format("%d. Тест: lambda '%s' → execute\n", testNum++, node.condition()));
        return prompt.toString();
    }

    @Override
    public String visitMethodRef(@NotNull CFGNode node) {
        prompt.append(String.format("%d. Тест: method ref '%s' → invoke\n", testNum++, node.condition()));
        return prompt.toString();
    }

    @Override
    public String visitStreamFilter(@NotNull CFGNode node) {
        prompt.append(String.format("%d. Тест: stream filter '%s' → include\n", testNum++, node.condition()));
        prompt.append(String.format("%d. Тест: stream filter '%s' → exclude\n", testNum++, node.condition()));
        return prompt.toString();
    }

    @Override
    public String visitStreamMap(@NotNull CFGNode node) {
        prompt.append(String.format("%d. Тест: stream map '%s' → transform\n", testNum++, node.condition()));
        return prompt.toString();
    }

    @Override
    public String visitStreamForeach(@NotNull CFGNode node) {
        prompt.append(String.format("%d. Тест: stream forEach '%s' → process each\n", testNum++, node.condition()));
        return prompt.toString();
    }

    @Override
    public String visitOptionalIfPresent(@NotNull CFGNode node) {
        prompt.append(String.format("%d. Тест: optional ifPresent → value present\n", testNum++));
        return prompt.toString();
    }

    @Override
    public String visitOptionalIfEmpty(@NotNull CFGNode node) {
        prompt.append(String.format("%d. Тест: optional ifPresent → value empty\n", testNum++));
        return prompt.toString();
    }

    @Override
    public String visitTryWithResources(@NotNull CFGNode node) {
        prompt.append(String.format("%d. Тест: try-with-resources '%s' → auto-close\n", testNum++, node.condition()));
        return prompt.toString();
    }

    @Override
    public String visitSynchronized(@NotNull CFGNode node) {
        prompt.append(String.format("%d. Тест: synchronized '%s' → acquire lock\n", testNum++, node.condition()));
        return prompt.toString();
    }

    @Override
    public String visitReactiveFilter(@NotNull CFGNode node) {
        prompt.append(String.format("%d. Тест: reactive filter '%s' → include\n", testNum++, node.condition()));
        return prompt.toString();
    }

    @Override
    public String visitReactiveMap(@NotNull CFGNode node) {
        prompt.append(String.format("%d. Тест: reactive map '%s' → transform\n", testNum++, node.condition()));
        return prompt.toString();
    }

    @Override
    public String visitReactiveOnError(@NotNull CFGNode node) {
        prompt.append(String.format("%d. Тест: reactive onError '%s' → handle error\n", testNum++, node.condition()));
        return prompt.toString();
    }

    @Override
    public String visitRecordPattern(@NotNull CFGNode node) {
        prompt.append(String.format("%d. Тест: record pattern '%s' → match\n", testNum++, node.condition()));
        return prompt.toString();
    }

    @Override
    public String visitGuardedPattern(@NotNull CFGNode node) {
        prompt.append(String.format("%d. Тест: guarded pattern '%s' → when TRUE\n", testNum++, node.condition()));
        prompt.append(String.format("%d. Тест: guarded pattern '%s' → when FALSE\n", testNum++, node.condition()));
        return prompt.toString();
    }

    @Override
    public String visitAnonymousClass(@NotNull CFGNode node) {
        prompt.append(String.format("%d. Тест: anonymous class '%s' → instantiate\n", testNum++, node.condition()));
        return prompt.toString();
    }

    @Override
    public String visitCompactConstructor(@NotNull CFGNode node) {
        prompt.append(String.format("%d. Тест: compact constructor '%s' → validate\n", testNum++, node.condition()));
        return prompt.toString();
    }

    @Override
    public String visitMultiCatch(@NotNull CFGNode node) {
        if (node.catchBlock() != null) {
            prompt.append(String.format("%d. Тест: multi-catch '%s' → handle\n", testNum++, node.catchBlock().exceptionType()));
        }
        return prompt.toString();
    }

    @Override
    public String visitNullSafeCall(@NotNull CFGNode node) {
        prompt.append(String.format("%d. Тест: null-safe call '%s' → not null\n", testNum++, node.condition()));
        prompt.append(String.format("%d. Тест: null-safe call '%s' → null\n", testNum++, node.condition()));
        return prompt.toString();
    }

    @Override
    public String visitElvisOperator(@NotNull CFGNode node) {
        prompt.append(String.format("%d. Тест: elvis operator '%s' → not null\n", testNum++, node.condition()));
        prompt.append(String.format("%d. Тест: elvis operator '%s' → use default\n", testNum++, node.condition()));
        return prompt.toString();
    }

    @Override
    public String visitSafeCast(@NotNull CFGNode node) {
        prompt.append(String.format("%d. Тест: safe cast '%s' → success\n", testNum++, node.condition()));
        prompt.append(String.format("%d. Тест: safe cast '%s' → fail (null)\n", testNum++, node.condition()));
        return prompt.toString();
    }

    /**
     * Get built prompt
     */
    @NotNull
    public String getPrompt() {
        return prompt.toString();
    }

    /**
     * Reset visitor state
     */
    public void reset() {
        prompt.setLength(0);
        testNum = 1;
    }
}
