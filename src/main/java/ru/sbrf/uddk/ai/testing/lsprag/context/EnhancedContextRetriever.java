package ru.sbrf.uddk.ai.testing.lsprag.context;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.PsiReturnStatement;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import ru.sbrf.uddk.ai.testing.lsprag.analysis.ImplementationContext;
import ru.sbrf.uddk.ai.testing.lsprag.analysis.ImplementationFinder;
import ru.sbrf.uddk.ai.testing.lsprag.analysis.SpringBeanResolver;
import ru.sbrf.uddk.ai.testing.lsprag.model.KeyToken;
import ru.sbrf.uddk.ai.testing.lsprag.model.TokenRole;
import ru.sbrf.uddk.ai.testing.lsprag.utils.PsiUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class EnhancedContextRetriever {

    private final Project project;
    private final int maxDepth;
    private final int maxTotalNodes;
    private final ImplementationFinder implementationFinder;
    private final SpringBeanResolver springBeanResolver;
    private final Map<String, MethodContext.MethodInfo> methodCache = new ConcurrentHashMap<>();
    private final Map<String, ImplementationContext> interfaceCache = new ConcurrentHashMap<>();
    private int nodeCounter = 0;

    public EnhancedContextRetriever(@NotNull Project project,
                                    int maxDepth,
                                    int maxTotalNodes) {
        this.project = project;
        this.maxDepth = maxDepth;
        this.maxTotalNodes = maxTotalNodes;
        this.implementationFinder = new ImplementationFinder(project);
        this.springBeanResolver = new SpringBeanResolver(project);
    }

    @NotNull
    public MethodContext retrieveContext(@NotNull PsiMethod targetMethod,
                                         @NotNull List<KeyToken> keyTokens) {
        nodeCounter = 0;
        Map<String, MethodContext.MethodInfo> calledMethods = new LinkedHashMap<>();
        Set<PsiMethod> visited = new HashSet<>();
        Set<String> trackedFields = new HashSet<>();
        List<ImplementationContext> interfaceImplementations = new ArrayList<>();
        ReadAction.run(() -> {
            // 1. Обрабатываем ключевые токены
            for (KeyToken token : keyTokens) {
                if (token.getRole() == TokenRole.METHOD_CALL) {
                    resolveAndAddMethodWithInterfaces(
                            token.getElement(),
                            calledMethods,
                            visited,
                            interfaceImplementations,
                            0
                    );
                } else if (token.getRole() == TokenRole.VARIABLE && token.getParentCondition() instanceof PsiField) {
                    trackedFields.add(token.getName());
                }
            }

            // 2. Дополнительные вызовы для полноты контекста

            for (PsiMethodCallExpression call :
                    PsiTreeUtil.findChildrenOfType(targetMethod, PsiMethodCallExpression.class)) {
                if (nodeCounter < maxTotalNodes) {
                    resolveAndAddMethodWithInterfaces(
                            call.getMethodExpression(),
                            calledMethods,
                            visited,
                            interfaceImplementations,
                            0
                    );
                }
            }
        });

        // 3. Добавляем информацию о полях
        if (!trackedFields.isEmpty()) {
            calledMethods.put("__fields__", new MethodContext.MethodInfo(
                    "class_fields",
                    "void",
                    Collections.emptyList(),
                    Collections.emptyList(),
                    "Tracked fields: " + String.join(", ", trackedFields),
                    null
            ));
        }

        // 4. Добавляем реализации интерфейсов в контекст
        if (!interfaceImplementations.isEmpty()) {
            calledMethods.put("__interfaces__", new MethodContext.MethodInfo(
                    "interface_implementations",
                    "void",
                    Collections.emptyList(),
                    Collections.emptyList(),
                    formatInterfaceContexts(interfaceImplementations),
                    null
            ));
        }

        return new MethodContext(targetMethod, keyTokens, calledMethods, maxDepth);
    }

    private boolean isSignificantMethod(@NotNull PsiMethod method) {
        return ReadAction.compute(() -> {
            // Пропускаем абстрактные / нативные
            if (method.getBody() == null) return false;

            // Пропускаем геттеры/сеттеры (эвристика)
            String name = method.getName();
            if ((name.startsWith("get") || name.startsWith("set") || name.startsWith("is")) &&
                    method.getParameterList().getParametersCount() <= 1) {
                // но не пропускаем, если есть аннотации вроде @RequestMapping
                if (method.getAnnotations().length == 0) return false;
            }

            // Пропускаем тривиальные методы (один return или один вызов)
            PsiCodeBlock body = method.getBody();
            if (body != null) {
                String text = body.getText();
                if (text.matches("\\{\\s*return\\s+[^;]+;\\s*\\}")) return false;
                PsiStatement[] statements = body.getStatements();
                if (statements.length == 1 && statements[0] instanceof PsiReturnStatement) return false;
            }
            return true;
        });
    }

    /**
     * Основная логика с поддержкой интерфейсов
     */
    private void resolveAndAddMethodWithInterfaces(PsiElement refElement,
                                                   Map<String, MethodContext.MethodInfo> methods,
                                                   Set<PsiMethod> visited,
                                                   List<ImplementationContext> interfaceContexts,
                                                   int currentDepth) {
        ReadAction.run(() -> {
            if (currentDepth > maxDepth || nodeCounter >= maxTotalNodes) return;
            nodeCounter++;

            // Разрешение ссылки
            PsiPolyVariantReference polyRef = (PsiPolyVariantReference) refElement;
            ResolveResult[] results = ReadAction.compute(() -> ResolveCache.getInstance(project)
                    .resolveWithCaching(polyRef, PsiPolyVariantReference::multiResolve, false, false));
// Обработайте массив results, например, возьмите первый элемент
            PsiElement resolved = results.length > 0 ? results[0].getElement() : null;
            if (!(resolved instanceof PsiMethod method)) {
                return;
            }

            // Проверяем, является ли метод из интерфейса
            if (implementationFinder.isInterfaceMethod(method)) {
                // Находим все реализации
                ImplementationContext implContext = findInterfaceImplementations(method);
                if (!implContext.getImplementations().isEmpty()) {
                    interfaceContexts.add(implContext);

                    // Добавляем основную реализацию в контекст
                    ImplementationContext.ImplementationInfo primary =
                            implContext.getPrimaryImplementationInfo();

                    addImplementationToMethods(primary, methods, visited, interfaceContexts, currentDepth);
                } else {
                    // Если реализаций нет, добавляем сам интерфейс
                    if (isSignificantMethod(method)) {
                        addMethodToMethods(method, methods, visited);
                    }
                }
            } else {
                // Обычный метод с реализацией
                if (isSignificantMethod(method)) {
                addMethodToMethods(method, methods, visited);
                }

                // Рекурсивный анализ
                if (currentDepth < maxDepth && nodeCounter < maxTotalNodes && method.getBody() != null) {
                    for (PsiMethodCallExpression nestedCall :
                            PsiTreeUtil.findChildrenOfType(method.getBody(), PsiMethodCallExpression.class)) {
                        resolveAndAddMethodWithInterfaces(
                                nestedCall.getMethodExpression(),
                                methods,
                                visited,
                                interfaceContexts,
                                currentDepth + 1
                        );
                    }
                }
            }
        });
    }

    /**
     * Находит реализации для метода интерфейса
     */
    @NotNull
    private ImplementationContext findInterfaceImplementations(@NotNull PsiMethod interfaceMethod) {
        return ReadAction.compute(() -> {
            String cacheKey = getInterfaceMethodCacheKey(interfaceMethod);

            if (interfaceCache.containsKey(cacheKey)) {
                return interfaceCache.get(cacheKey);
            }

            PsiClass interfaceClass = interfaceMethod.getContainingClass();
            if (interfaceClass == null) {
                return new ImplementationContext(interfaceMethod, Collections.emptyList(), null);
            }

            // 1. Ищем все реализации интерфейса
            List<PsiClass> implementations = implementationFinder.findImplementations(interfaceClass);

            // 2. Фильтруем Spring-компоненты
            List<ImplementationContext.ImplementationInfo> implInfos = new ArrayList<>();
            PsiClass primaryImpl = null;

            for (PsiClass implClass : implementations) {
                PsiMethod implMethod = implementationFinder.findImplementationMethod(
                        interfaceMethod, implClass);

                if (implMethod != null) {
                    boolean isSpring = springBeanResolver.isSpringComponent(implClass);
                    String beanName = springBeanResolver.getBeanName(implClass);
                    int priority = isSpring ? getSpringPriority(implClass) : 0;

                    ReadAction.run(() -> {
                        ImplementationContext.ImplementationInfo info =
                                new ImplementationContext.ImplementationInfo(
                                        implClass, implMethod, isSpring, beanName, priority);
                        implInfos.add(info);
                    });

                    // Первая Spring-реализация считается основной
                    if (isSpring && primaryImpl == null) {
                        primaryImpl = implClass;
                    }
                }
            }

            // Сортируем по приоритету
            implInfos.sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));

            ImplementationContext context = new ImplementationContext(
                    interfaceMethod, implInfos, primaryImpl);

            interfaceCache.put(cacheKey, context);
            return context;
        });
    }

    /**
     * Добавляет реализацию в список методов
     */
    private void addImplementationToMethods(ImplementationContext.ImplementationInfo info,
                                            Map<String, MethodContext.MethodInfo> methods,
                                            Set<PsiMethod> visited,
                                            List<ImplementationContext> interfaceContexts,
                                            int currentDepth) {

        ReadAction.run(() -> {
            PsiMethod method = info.getImplementingMethod();

            if (!isSignificantMethod(method)) return;

            // Проверка на повторное посещение
            if (visited.contains(method)) {
                return;
            }
            visited.add(method);

            // Проверка лимита узлов
            if (nodeCounter >= maxTotalNodes) {
                return;
            }
            nodeCounter++;

            String signature = PsiUtils.getUniqueMethodSignature(method);
            if (methods.containsKey(signature)) {
                return;
            }

            // Извлекаем информацию о методе
            List<String> params = Arrays.stream(method.getParameterList().getParameters())
                    .map(PsiUtils::getParameterSignature)
                    .toList();

            List<String> exceptions = Arrays.stream(method.getThrowsList().getReferencedTypes())
                    .map(PsiType::getPresentableText)
                    .toList();

            String body = info.getBodySnippet();
            String returnTypeAnalysis = ReadAction.compute(() -> analyzeReturnType(method.getReturnType()));

            // Добавляем метку о том, что это реализация интерфейса
            String fullSignature = signature + " [implements " +
                    method.getContainingClass().getName() + "]";

            methods.put(signature, new MethodContext.MethodInfo(
                    fullSignature,
                    method.getReturnType().getPresentableText(),
                    params,
                    exceptions,
                    body,
                    returnTypeAnalysis
            ));

            // === ✅ РЕКУРСИВНЫЙ АНАЛИЗ ТЕЛА МЕТОДА ===
            if (currentDepth < maxDepth && method.getBody() != null) {
                // Находим все вызовы методов в теле текущей реализации
                Collection<PsiMethodCallExpression> nestedCalls =
                        PsiTreeUtil.findChildrenOfType(method.getBody(), PsiMethodCallExpression.class);

                for (PsiMethodCallExpression nestedCall : nestedCalls) {
                    // Рекурсивно обрабатываем каждый вложенный вызов
                    resolveAndAddMethodWithInterfaces(
                            nestedCall.getMethodExpression(),
                            methods,
                            visited,
                            interfaceContexts,
                            currentDepth + 1  // Увеличиваем глубину
                    );
                }
            }
            // === КОНЕЦ рекурсивного анализа ===
        });
    }

    /**
     * Добавляет обычный метод в список
     */
    private void addMethodToMethods(@NotNull PsiMethod method,
                                    Map<String, MethodContext.MethodInfo> methods,
                                    Set<PsiMethod> visited) {
        ReadAction.run(() -> {
            if (visited.contains(method)) {
                return;
            }
            visited.add(method);

            if (!isSignificantMethod(method)) return;

            String signature = PsiUtils.getUniqueMethodSignature(method);
            if (methods.containsKey(signature)) {
                return;
            }

            List<String> params = Arrays.stream(method.getParameterList().getParameters())
                    .map(PsiUtils::getParameterSignature)
                    .toList();

            List<String> exceptions = Arrays.stream(method.getThrowsList().getReferencedTypes())
                    .map(PsiType::getPresentableText)
                    .toList();

            String body = method.getBody() != null
                    ? PsiUtils.truncateCode(method.getBody().getText(), 400)
                    : "// abstract or native method";

            String returnTypeAnalysis = analyzeReturnType(method.getReturnType());

            methods.put(signature, new MethodContext.MethodInfo(
                    signature,
                    method.getReturnType().getPresentableText(),
                    params,
                    exceptions,
                    body,
                    returnTypeAnalysis
            ));
        });
    }

    @NotNull
    private String formatInterfaceContexts(@NotNull List<ImplementationContext> contexts) {
        StringBuilder sb = new StringBuilder();
        for (ImplementationContext ctx : contexts) {
            sb.append(ctx.formatForPrompt()).append("\n");
        }
        return sb.toString();
    }

    @NotNull
    private String getInterfaceMethodCacheKey(@NotNull PsiMethod method) {
        PsiClass clazz = method.getContainingClass();
        if (clazz == null) {
            return method.getName() + ":" + method.getTextOffset();
        }
        return clazz.getQualifiedName() + "." + method.getName();
    }

    private int getSpringPriority(@NotNull PsiClass psiClass) {
        return springBeanResolver.isSpringComponent(psiClass) ? 1 : 0;
    }

    @NotNull
    private String analyzeReturnType(PsiType returnType) {
        if (returnType == null || returnType.equals(PsiTypes.voidType())) {
            return "void";
        }

        StringBuilder analysis = new StringBuilder();

        ReadAction.run(() -> {
            if (returnType.getPresentableText().matches("String|Integer|Long|Boolean|Double|Float")) {
                analysis.append("primitive:").append(returnType.getPresentableText());
            } else if (PsiUtils.isTypeOf(returnType, "java.util.List")) {
                analysis.append("collection:List");
            } else if (PsiUtils.isTypeOf(returnType, "java.util.Map")) {
                analysis.append("collection:Map");
            } else if (PsiUtils.isTypeOf(returnType, "java.util.Optional")) {
                analysis.append("optional");
            } else if (PsiUtils.isTypeOf(returnType, "org.springframework.http.ResponseEntity")) {
                PsiType[] params = returnType instanceof PsiClassType classType ? classType.getParameters() : new PsiType[0];
                String inner = params.length > 0 ? params[0].getPresentableText() : "Object";
                analysis.append("response_entity:").append(inner);
            } else {
                analysis.append("custom:").append(returnType.getPresentableText());
            }
        });

        return analysis.toString();
    }

    public void invalidateCache(@NotNull PsiFile changedFile) {
        methodCache.clear();
        interfaceCache.clear();
    }
}