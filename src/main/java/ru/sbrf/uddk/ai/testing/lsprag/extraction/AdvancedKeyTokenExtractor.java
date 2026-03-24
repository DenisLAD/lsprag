package ru.sbrf.uddk.ai.testing.lsprag.extraction;

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiAssertStatement;
import com.intellij.psi.PsiCatchSection;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiConditionalExpression;
import com.intellij.psi.PsiDoWhileStatement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiForStatement;
import com.intellij.psi.PsiIfStatement;
import com.intellij.psi.PsiInstanceOfExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiPolyadicExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiSwitchStatement;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.PsiWhileStatement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.sbrf.uddk.ai.testing.lsprag.model.KeyToken;
import ru.sbrf.uddk.ai.testing.lsprag.model.TokenRole;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Расширенный экстрактор ключевых токенов с поддержкой:
 * - assert-выражений
 * - catch-блоков
 * - группировки условий по логическим операторам (CFG-подобная структура)
 * - отслеживания полей класса
 */
public class AdvancedKeyTokenExtractor {

    private final PsiMethod targetMethod;
    private final Map<String, KeyToken> tokenRegistry = new HashMap<>();
    private final List<ConditionGroup> conditionGroups = new ArrayList<>();

    public AdvancedKeyTokenExtractor(@NotNull PsiMethod method) {
        this.targetMethod = method;
    }

    @NotNull
    public ExtractionResult extract() {
        return ReadAction.compute(() -> {
            collectFromConditionals();
            collectFromAsserts();
            collectFromCatchBlocks();
            collectMethodCalls();
            collectClassFields();
            buildConditionGroups();

            return new ExtractionResult(new ArrayList<>(tokenRegistry.values()), conditionGroups);
        });
    }

    private void collectFromConditionals() {
        for (PsiIfStatement ifStmt : PsiTreeUtil.findChildrenOfType(targetMethod, PsiIfStatement.class)) {
            processCondition(ifStmt.getCondition(), ifStmt, "if");
        }
        for (PsiSwitchStatement switchStmt : PsiTreeUtil.findChildrenOfType(targetMethod, PsiSwitchStatement.class)) {
            processCondition(switchStmt.getExpression(), switchStmt, "switch");
        }
        for (PsiConditionalExpression ternary : PsiTreeUtil.findChildrenOfType(targetMethod, PsiConditionalExpression.class)) {
            processCondition(ternary.getCondition(), ternary, "ternary");
        }
        for (PsiWhileStatement whileStmt : PsiTreeUtil.findChildrenOfType(targetMethod, PsiWhileStatement.class)) {
            PsiExpression condition = whileStmt.getCondition();
            if (condition != null) {
                processCondition(condition, whileStmt, "while");
            }
        }
        for (PsiDoWhileStatement doWhile : PsiTreeUtil.findChildrenOfType(targetMethod, PsiDoWhileStatement.class)) {
            PsiExpression condition = doWhile.getCondition();
            if (condition != null) {
                processCondition(condition, doWhile, "do-while");
            }
        }
        for (PsiForStatement forStmt : PsiTreeUtil.findChildrenOfType(targetMethod, PsiForStatement.class)) {
            PsiExpression condition = forStmt.getCondition();
            if (condition != null) {
                processCondition(condition, forStmt, "for");
            }
        }
    }

    private void processCondition(@Nullable PsiExpression condition, @NotNull PsiElement parent, @NotNull String type) {
        if (condition == null) return;

        if (condition instanceof PsiPolyadicExpression polyadic) {
            extractFromPolyadic(polyadic, parent, type);
        } else {
            collectReferencesInExpression(condition, parent, TokenRole.CONDITION);
        }
    }

    private void extractFromPolyadic(@NotNull PsiPolyadicExpression expr,
                                     @NotNull PsiElement parent,
                                     @NotNull String contextType) {
        String operator = expr.getOperationTokenType().toString();
        List<PsiExpression> operands = Arrays.asList(expr.getOperands());

        ConditionGroup group = new ConditionGroup(operator, contextType, parent);

        for (PsiExpression operand : operands) {
            if (operand instanceof PsiPolyadicExpression nested) {
                extractFromPolyadic(nested, parent, contextType);
            } else {
                List<KeyToken> tokens = collectReferencesInExpression(operand, parent, TokenRole.CONDITION);
                group.addTokens(tokens);
            }
        }
        conditionGroups.add(group);
    }

    private void collectFromAsserts() {
        for (PsiAssertStatement assertStmt : PsiTreeUtil.findChildrenOfType(targetMethod, PsiAssertStatement.class)) {
            PsiExpression assertExpr = assertStmt.getAssertCondition();
            if (assertExpr != null) {
                collectReferencesInExpression(assertExpr, assertStmt, TokenRole.CONDITION);
            }
        }
    }

    /**
     * Исправленная версия: анализ catch-блоков
     * PsiCatchSection не имеет getCatchParameter(), используем getParameter()
     */
    private void collectFromCatchBlocks() {
        for (PsiCatchSection catchSection : PsiTreeUtil.findChildrenOfType(targetMethod, PsiCatchSection.class)) {
            // ИСПРАВЛЕНИЕ: используем getParameter() вместо getCatchParameter()
            PsiParameter exceptionParam = catchSection.getParameter();
            if (exceptionParam != null) {
                tokenRegistry.put("ex:" + exceptionParam.getName(),
                        new KeyToken(exceptionParam, exceptionParam.getName(), TokenRole.VARIABLE, catchSection));
            }

            // Анализируем условия внутри catch
            for (PsiIfStatement ifStmt : PsiTreeUtil.findChildrenOfType(catchSection, PsiIfStatement.class)) {
                PsiExpression condition = ifStmt.getCondition();
                if (condition != null) {
                    // Особая обработка instanceof в catch
                    if (condition instanceof PsiInstanceOfExpression instanceOf) {
                        // ИСПРАВЛЕНИЕ: getCheckType() возвращает PsiTypeElement, нужно getType()
                        PsiTypeElement typeElement = instanceOf.getCheckType();
                        if (typeElement != null) {
                            PsiType checkType = typeElement.getType();
                            if (checkType != null) {
                                tokenRegistry.put("instanceof:" + checkType.getPresentableText(),
                                        new KeyToken(instanceOf, checkType.getPresentableText(),
                                                TokenRole.CONDITION, catchSection));
                            }
                        }
                    }
                    collectReferencesInExpression(condition, ifStmt, TokenRole.CONDITION);
                }
            }
        }
    }

    private void collectClassFields() {
        PsiClass containingClass = targetMethod.getContainingClass();
        if (containingClass == null) return;

        Map<String, PsiField> classFields = Arrays.stream(containingClass.getFields())
                .collect(Collectors.toMap(PsiField::getName, f -> f));

        for (PsiReferenceExpression ref : PsiTreeUtil.findChildrenOfType(targetMethod, PsiReferenceExpression.class)) {
            String name = ref.getReferenceName();
            if (name != null && classFields.containsKey(name)) {
                PsiField field = classFields.get(name);
                tokenRegistry.put("field:" + name,
                        new KeyToken(ref, name, TokenRole.VARIABLE, field));
            }
        }
    }

    private void collectMethodCalls() {
        for (PsiMethodCallExpression call : PsiTreeUtil.findChildrenOfType(targetMethod, PsiMethodCallExpression.class)) {
            PsiReferenceExpression ref = call.getMethodExpression();
            String key = "call:" + ref.getReferenceName() + ":" + ref.getTextOffset();
            if (!tokenRegistry.containsKey(key)) {
                tokenRegistry.put(key, new KeyToken(ref, ref.getReferenceName(), TokenRole.METHOD_CALL, null));
            }
        }
    }

    private List<KeyToken> collectReferencesInExpression(@Nullable PsiExpression expr,
                                                         @NotNull PsiElement parent,
                                                         @NotNull TokenRole defaultRole) {
        List<KeyToken> collected = new ArrayList<>();
        if (expr == null) return collected;

        for (PsiReferenceExpression ref : PsiTreeUtil.findChildrenOfType(expr, PsiReferenceExpression.class)) {
            String name = ref.getReferenceName();
            if (name == null) continue;

            TokenRole role = (ref instanceof PsiMethodCallExpression) ? TokenRole.METHOD_CALL : defaultRole;
            String key = role + ":" + name + ":" + ref.getTextOffset();

            if (!tokenRegistry.containsKey(key)) {
                KeyToken token = new KeyToken(ref, name, role, parent);
                tokenRegistry.put(key, token);
                collected.add(token);
            }
        }
        return collected;
    }

    private void buildConditionGroups() {
        // Дополнительная пост-обработка для связывания условий
    }

    public static class ExtractionResult {
        private final List<KeyToken> tokens;
        private final List<ConditionGroup> conditionGroups;

        public ExtractionResult(List<KeyToken> tokens, List<ConditionGroup> conditionGroups) {
            this.tokens = tokens;
            this.conditionGroups = conditionGroups;
        }

        public List<KeyToken> getTokens() {
            return tokens;
        }

        public List<ConditionGroup> getConditionGroups() {
            return conditionGroups;
        }
    }

    public static class ConditionGroup {
        private final String logicalOperator;
        private final String contextType;
        private final PsiElement parent;
        private final List<KeyToken> tokens = new ArrayList<>();

        public ConditionGroup(String logicalOperator, String contextType, PsiElement parent) {
            this.logicalOperator = logicalOperator;
            this.contextType = contextType;
            this.parent = parent;
        }

        public void addTokens(List<KeyToken> newTokens) {
            tokens.addAll(newTokens);
        }

        public String getLogicalOperator() {
            return logicalOperator;
        }

        public String getContextType() {
            return contextType;
        }

        public List<KeyToken> getTokens() {
            return tokens;
        }

        @Override
        public String toString() {
            return String.format("ConditionGroup{%s %s: %s}",
                    contextType, logicalOperator,
                    tokens.stream().map(KeyToken::getName).collect(Collectors.joining(", ")));
        }
    }
}