package ru.sbrf.uddk.ai.testing.lsprag.extraction;

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiConditionalExpression;
import com.intellij.psi.PsiDoWhileStatement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiForStatement;
import com.intellij.psi.PsiIfStatement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiSwitchStatement;
import com.intellij.psi.PsiWhileStatement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import ru.sbrf.uddk.ai.testing.lsprag.model.KeyToken;
import ru.sbrf.uddk.ai.testing.lsprag.model.TokenRole;

import java.util.ArrayList;
import java.util.List;

public class KeyTokenExtractor {

    @NotNull
    public List<KeyToken> extract(@NotNull PsiMethod method) {
        List<KeyToken> tokens = new ArrayList<>();

        // 1. Извлекаем условные конструкции
        extractConditionals(method, tokens);

        // 2. Извлекаем все вызовы методов (для контекста)
        extractMethodCalls(method, tokens);

        return tokens;
    }

    private void extractConditionals(PsiElement element, List<KeyToken> tokens) {
        ReadAction.run(() -> {
            // if-statements
            for (PsiIfStatement ifStmt : PsiTreeUtil.findChildrenOfType(element, PsiIfStatement.class)) {
                PsiExpression condition = ifStmt.getCondition();
                if (condition != null) {
                    collectReferencesInExpression(condition, ifStmt, tokens);
                }
            }

            // switch-statements
            for (PsiSwitchStatement switchStmt : PsiTreeUtil.findChildrenOfType(element, PsiSwitchStatement.class)) {
                PsiExpression expression = switchStmt.getExpression();
                if (expression != null) {
                    collectReferencesInExpression(expression, switchStmt, tokens);
                }
            }

            // ternary expressions
            for (PsiConditionalExpression ternary : PsiTreeUtil.findChildrenOfType(element, PsiConditionalExpression.class)) {
                collectReferencesInExpression(ternary.getCondition(), ternary, tokens);
            }

            // loops with conditions
            for (PsiWhileStatement whileStmt : PsiTreeUtil.findChildrenOfType(element, PsiWhileStatement.class)) {
                PsiExpression condition = whileStmt.getCondition();
                if (condition != null) {
                    collectReferencesInExpression(condition, whileStmt, tokens);
                }
            }

            for (PsiDoWhileStatement doWhile : PsiTreeUtil.findChildrenOfType(element, PsiDoWhileStatement.class)) {
                PsiExpression condition = doWhile.getCondition();
                if (condition != null) {
                    collectReferencesInExpression(condition, doWhile, tokens);
                }
            }


            for (PsiForStatement forStmt : PsiTreeUtil.findChildrenOfType(element, PsiForStatement.class)) {
                PsiExpression condition = forStmt.getCondition();
                if (condition != null) {
                    collectReferencesInExpression(condition, forStmt, tokens);
                }
            }
        });
    }

    private void collectReferencesInExpression(PsiExpression expr, PsiElement parentCondition, List<KeyToken> tokens) {
        if (expr == null) return;

        for (PsiReferenceExpression ref : PsiTreeUtil.findChildrenOfType(expr, PsiReferenceExpression.class)) {
            TokenRole role = (ref instanceof PsiMethodCallExpression) ? TokenRole.METHOD_CALL : TokenRole.VARIABLE;
            tokens.add(new KeyToken(ref, ref.getReferenceName(), role, parentCondition));
        }
    }

    private void extractMethodCalls(PsiElement element, List<KeyToken> tokens) {
        ReadAction.run(() -> {
            for (PsiMethodCallExpression call : PsiTreeUtil.findChildrenOfType(element, PsiMethodCallExpression.class)) {
                PsiReferenceExpression ref = call.getMethodExpression();
                // Добавляем только если ещё не добавлен как CONDITION
                if (tokens.stream().noneMatch(t -> t.getElement() == ref && t.getRole() == TokenRole.METHOD_CALL)) {
                    tokens.add(new KeyToken(ref, ref.getReferenceName(), TokenRole.METHOD_CALL, null));
                }
            }
        });
    }
}
