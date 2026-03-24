package ru.sbrf.uddk.ai.testing.lsprag.context;

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import ru.sbrf.uddk.ai.testing.lsprag.model.KeyToken;
import ru.sbrf.uddk.ai.testing.lsprag.model.TokenRole;
import ru.sbrf.uddk.ai.testing.lsprag.utils.PsiUtils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ContextRetriever {

    private final int maxDepth;

    public ContextRetriever(int maxDepth) {
        this.maxDepth = maxDepth;
    }

    @NotNull
    public MethodContext retrieveContext(@NotNull PsiMethod targetMethod,
                                         @NotNull List<KeyToken> keyTokens) {
        Map<String, MethodContext.MethodInfo> calledMethods = new LinkedHashMap<>();
        Set<PsiMethod> visited = new HashSet<>();

        // 1. Обрабатываем ключевые токены-вызовы
        for (KeyToken token : keyTokens) {
            if (token.getRole() == TokenRole.METHOD_CALL) {
                resolveAndAddMethod(token.getElement(), calledMethods, visited, 0);
            }
        }

        // 2. Дополнительно: все вызовы в методе для полноты контекста
        ReadAction.run(() -> {
            for (PsiMethodCallExpression call :
                    PsiTreeUtil.findChildrenOfType(targetMethod, PsiMethodCallExpression.class)) {
                resolveAndAddMethod(call.getMethodExpression(), calledMethods, visited, 0);
            }
        });

        return new MethodContext(targetMethod, keyTokens, calledMethods, maxDepth);
    }

    private void resolveAndAddMethod(PsiElement refElement,
                                     Map<String, MethodContext.MethodInfo> methods,
                                     Set<PsiMethod> visited, int currentDepth) {
        if (currentDepth > maxDepth) return;

        ReadAction.run(() -> {
            PsiElement resolved = ((PsiReference) refElement).resolve();
            if (!(resolved instanceof PsiMethod method) || !visited.add(method)) {
                return;
            }

            String signature = PsiUtils.getMethodSignature(method);
            if (methods.containsKey(signature)) return;

            List<String> params = Arrays.stream(method.getParameterList().getParameters())
                    .map(p -> p.getType().getPresentableText() + " " + p.getName())
                    .toList();

            List<String> exceptions = Arrays.stream(method.getThrowsList().getReferencedTypes())
                    .map(PsiType::getPresentableText)
                    .toList();

            String body = method.getBody() != null
                    ? PsiUtils.truncateCode(method.getBody().getText(), 500)
                    : null;

            methods.put(signature, new MethodContext.MethodInfo(
                    signature,
                    method.getReturnType().getPresentableText(),
                    params,
                    exceptions,
                    body
            ));

            // Рекурсивный анализ для глубины > 0
            if (currentDepth < maxDepth && method.getBody() != null) {
                for (PsiMethodCallExpression nestedCall :
                        PsiTreeUtil.findChildrenOfType(method.getBody(), PsiMethodCallExpression.class)) {
                    resolveAndAddMethod(nestedCall.getMethodExpression(), methods, visited, currentDepth + 1);
                }
            }
        });
    }
}