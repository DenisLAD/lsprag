package com.reasoningtestgen.extractor;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.javadoc.PsiDocTagValue;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.reasoningtestgen.model.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;
import java.util.Arrays;

/**
 * Extracts extended context from PSI method using IntelliJ Platform APIs
 * According to ANALYTICS.md Section 5.1
 */
public class PSIExtractor {

    private static final Logger LOG = LoggerFactory.getLogger(PSIExtractor.class);

    /**
     * Extract complete method context from a PsiMethod
     * Must be called within a ReadAction
     */
    @NotNull
    public MethodContext extract(@NotNull PsiMethod method) {
        return ReadAction.compute(() -> {
            PsiClass containingClass = method.getContainingClass();
            String className = containingClass != null ? containingClass.getName() : "Unknown";
            String methodName = method.getName();
            String returnType = method.getReturnType() != null ? method.getReturnType().getPresentableText() : "void";
            
            List<Parameter> parameters = extractParameters(method);
            List<String> annotations = extractAnnotations(method);
            List<Dependency> dependencies = extractDependencies(method, containingClass);
            List<DependencyInfo> dependenciesInfo = extractDependenciesWithSource(
                method, containingClass, false, 0
            );
            List<CalledMethodInfo> calledMethods = extractCalledMethods(method, 1); // REQ 1: depth=1
            List<DTOInfo> dtoStructures = extractDTOStructures(method, 2); // REQ 5: depth=2
            List<DataTransformation> dataTransformations = extractDataTransformations(method); // REQ 3
            DocContract docContract = extractDocContract(method);
            List<ExistingTestInfo> existingTests = findExistingTests(containingClass, method.getProject());
            ComplexityMetrics complexity = calculateComplexity(method);
            List<CFGNode> cfgNodes = buildCFG(method);
            
            MethodContext.ControlFlow controlFlow = new MethodContext.ControlFlow(cfgNodes);
            
            return new MethodContext(
                className,
                methodName,
                returnType,
                parameters,
                annotations,
                controlFlow,
                dependencies,
                dependenciesInfo,
                calledMethods,
                dtoStructures,
                dataTransformations,
                docContract,
                existingTests,
                complexity,
                isRestController(containingClass),
                isSpringService(containingClass),
                isSpringRepository(containingClass),
                extractMethodSource(method),
                null // coverageInfo will be filled by CoverageAnalysisService
            );
        });
    }

    /**
     * Extract complete method context with source code inclusion
     * Must be called within a ReadAction
     */
    @NotNull
    public MethodContext extractWithSource(@NotNull PsiMethod method,
                                             boolean includeSourceCode,
                                             int maxCodeLength,
                                             int analysisDepth) {
        return ReadAction.compute(() -> {
            PsiClass containingClass = method.getContainingClass();
            String className = containingClass != null ? containingClass.getName() : "Unknown";
            String methodName = method.getName();
            String returnType = method.getReturnType() != null ? method.getReturnType().getPresentableText() : "void";
            
            List<Parameter> parameters = extractParameters(method);
            List<String> annotations = extractAnnotations(method);
            List<Dependency> dependencies = extractDependencies(method, containingClass);
            List<DependencyInfo> dependenciesInfo = extractDependenciesWithSource(
                method, containingClass, includeSourceCode, maxCodeLength
            );
            List<CalledMethodInfo> calledMethods = extractCalledMethods(method, analysisDepth); // REQ 1
            List<DTOInfo> dtoStructures = extractDTOStructures(method, 2); // REQ 5
            List<DataTransformation> dataTransformations = extractDataTransformations(method); // REQ 3
            DocContract docContract = extractDocContract(method);
            List<ExistingTestInfo> existingTests = findExistingTests(containingClass, method.getProject());
            ComplexityMetrics complexity = calculateComplexity(method);
            List<CFGNode> cfgNodes = buildCFG(method);
            
            MethodContext.ControlFlow controlFlow = new MethodContext.ControlFlow(cfgNodes);
            
            return new MethodContext(
                className,
                methodName,
                returnType,
                parameters,
                annotations,
                controlFlow,
                dependencies,
                dependenciesInfo,
                calledMethods,
                dtoStructures,
                dataTransformations,
                docContract,
                existingTests,
                complexity,
                isRestController(containingClass),
                isSpringService(containingClass),
                isSpringRepository(containingClass),
                extractMethodSource(method),
                null // coverageInfo will be filled by CoverageAnalysisService
            );
        });
    }

    /**
     * Extract method parameters
     */
    @NotNull
    private List<Parameter> extractParameters(@NotNull PsiMethod method) {
        PsiParameterList parameterList = method.getParameterList();
        List<Parameter> parameters = new ArrayList<>();
        
        for (PsiParameter param : parameterList.getParameters()) {
            String name = param.getName();
            String type = param.getType().getPresentableText();
            boolean nullable = isNullable(param);
            parameters.add(new Parameter(name, type, nullable));
        }
        
        return parameters;
    }

    /**
     * Check if parameter/element has nullable annotation
     */
    private boolean isNullable(@NotNull PsiModifierListOwner owner) {
        PsiModifierList modifierList = owner.getModifierList();
        if (modifierList == null) return false;
        
        String[] nullableAnnotations = {
            "org.jetbrains.annotations.Nullable",
            "javax.annotation.Nullable",
            "jakarta.annotation.Nullable",
            "androidx.annotation.Nullable"
        };
        
        for (String annotation : nullableAnnotations) {
            if (modifierList.findAnnotation(annotation) != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * Extract method annotations
     */
    @NotNull
    private List<String> extractAnnotations(@NotNull PsiMethod method) {
        return Arrays.stream(method.getAnnotations())
            .map(PsiAnnotation::getQualifiedName)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    /**
     * Extract dependencies from method and containing class
     */
    @NotNull
    private List<Dependency> extractDependencies(@NotNull PsiMethod method, @Nullable PsiClass containingClass) {
        Set<Dependency> dependencies = new HashSet<>();
        
        // Extract from class fields
        if (containingClass != null) {
            for (PsiField field : containingClass.getFields()) {
                String name = field.getName();
                String type = field.getType().getPresentableText();
                boolean isExternal = isExternalDependency(field);
                boolean nullable = isNullable(field);
                dependencies.add(new Dependency(name, type, isExternal, nullable));
            }
        }
        
        // Extract from method parameters
        for (PsiParameter param : method.getParameterList().getParameters()) {
            String type = param.getType().getPresentableText();
            boolean isExternal = isComplexType(type);
            dependencies.add(new Dependency(param.getName(), type, isExternal, isNullable(param)));
        }
        
        return new ArrayList<>(dependencies);
    }

    /**
     * Check if field is an external dependency (has injection annotations)
     */
    private boolean isExternalDependency(@NotNull PsiField field) {
        String[] injectionAnnotations = {
            "org.springframework.beans.factory.annotation.Autowired",
            "javax.inject.Inject",
            "jakarta.inject.Inject",
            "com.google.inject.Inject"
        };
        
        PsiModifierList modifierList = field.getModifierList();
        if (modifierList == null) return false;
        
        for (String annotation : injectionAnnotations) {
            if (modifierList.findAnnotation(annotation) != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if type is complex (not primitive)
     */
    private boolean isComplexType(@NotNull String type) {
        return !type.matches("^(int|long|double|float|boolean|char|byte|short|void|String)$");
    }

    /**
     * Extract documentation contract from JavaDoc
     */
    @NotNull
    private DocContract extractDocContract(@NotNull PsiMethod method) {
        Map<String, String> params = new HashMap<>();
        String returns = null;
        List<String> throwsList = new ArrayList<>();
        List<String> businessRules = new ArrayList<>();
        
        PsiDocComment docComment = method.getDocComment();
        if (docComment != null && docComment.getText() != null) {
            // Extract @param tags
            for (PsiDocTag tag : docComment.getTags()) {
                String tagName = tag.getName();
                if ("param".equals(tagName)) {
                    String text = tag.getText();
                    if (text != null && !text.isEmpty()) {
                        String paramName = text.split("\\s+")[0];
                        params.put(paramName, text);
                    }
                } else if ("return".equals(tagName)) {
                    String text = tag.getText();
                    if (text != null && !text.isEmpty()) {
                        returns = text;
                    }
                } else if ("throws".equals(tagName) || "exception".equals(tagName)) {
                    String text = tag.getText();
                    if (text != null && !text.isEmpty()) {
                        throwsList.add(text);
                    }
                }
            }
            
            // Extract business rules from description
            String description = docComment.getText();
            if (description != null && !description.isEmpty()) {
                businessRules = extractBusinessRules(description);
            }
        }
        
        return new DocContract(params, returns, throwsList, businessRules);
    }

    /**
     * Simple NLP-like extraction of business rules from JavaDoc
     */
    @NotNull
    private List<String> extractBusinessRules(@NotNull String description) {
        List<String> rules = new ArrayList<>();
        String[] keywords = {"must", "should", "always", "never", "required", "ensure"};
        
        String[] sentences = description.split("[.!?]");
        for (String sentence : sentences) {
            String lower = sentence.toLowerCase().trim();
            for (String keyword : keywords) {
                if (lower.contains(keyword)) {
                    rules.add(sentence.trim());
                    break;
                }
            }
        }
        
        return rules;
    }

    /**
     * Find existing tests for the containing class
     */
    @NotNull
    private List<ExistingTestInfo> findExistingTests(@Nullable PsiClass containingClass, 
                                                      @NotNull com.intellij.openapi.project.Project project) {
        if (containingClass == null) {
            return Collections.emptyList();
        }
        
        String className = containingClass.getName();
        if (className == null) {
            return Collections.emptyList();
        }
        
        String testClassName = className + "Test";
        List<ExistingTestInfo> existingTests = new ArrayList<>();
        
        // Search for test classes in test source roots
        PsiManager psiManager = PsiManager.getInstance(project);
        com.intellij.psi.search.GlobalSearchScope testScope = 
            com.intellij.psi.search.GlobalSearchScope.projectScope(project);
        
        PsiClass[] testClasses = com.intellij.psi.JavaPsiFacade.getInstance(project)
            .findClasses(testClassName, testScope);
        
        for (PsiClass testClass : testClasses) {
            for (PsiMethod testMethod : testClass.getMethods()) {
                if (isTestMethod(testMethod)) {
                    List<String> assertions = extractAssertions(testMethod);
                    List<String> imports = extractImports(testClass.getContainingFile());
                    String framework = detectFramework(testClass);
                    
                    existingTests.add(new ExistingTestInfo(
                        testMethod.getName(),
                        assertions,
                        framework,
                        imports
                    ));
                    
                    // Limit to 3 examples
                    if (existingTests.size() >= 3) {
                        return existingTests;
                    }
                }
            }
        }
        
        return existingTests;
    }

    /**
     * Check if method is a test method
     */
    private boolean isTestMethod(@NotNull PsiMethod method) {
        PsiModifierList modifierList = method.getModifierList();
        if (modifierList == null) return false;
        
        String[] testAnnotations = {
            "org.junit.Test",
            "org.junit.jupiter.api.Test",
            "org.testng.annotations.Test"
        };
        
        for (String annotation : testAnnotations) {
            if (modifierList.findAnnotation(annotation) != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * Extract assertion methods used in test
     */
    @NotNull
    private List<String> extractAssertions(@NotNull PsiMethod method) {
        Set<String> assertions = new HashSet<>();
        
        method.accept(new JavaRecursiveElementVisitor() {
            @Override
            public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
                super.visitMethodCallExpression(expression);
                String methodName = expression.getMethodExpression().getReferenceName();
                if (methodName != null && isAssertion(methodName)) {
                    assertions.add(methodName);
                }
            }
        });
        
        return new ArrayList<>(assertions);
    }

    /**
     * Check if method name is an assertion
     */
    private boolean isAssertion(@NotNull String methodName) {
        return methodName.startsWith("assert") || 
               methodName.startsWith("verify") ||
               methodName.startsWith("should") ||
               methodName.startsWith("expect");
    }

    /**
     * Extract imports from file
     */
    @NotNull
    private List<String> extractImports(@NotNull PsiFile file) {
        List<String> imports = new ArrayList<>();
        if (file instanceof PsiJavaFile) {
            PsiJavaFile javaFile = (PsiJavaFile) file;
            for (PsiImportStatementBase importStmt : javaFile.getImportList().getImportStatements()) {
                String qualifiedName = importStmt.getImportReference() != null ? 
                    importStmt.getImportReference().getQualifiedName() : null;
                if (qualifiedName != null) {
                    imports.add(qualifiedName);
                }
            }
        }
        return imports;
    }

    /**
     * Detect test framework used in test class
     */
    @NotNull
    private String detectFramework(@NotNull PsiClass testClass) {
        PsiFile file = testClass.getContainingFile();
        List<String> imports = extractImports(file);
        
        for (String importStmt : imports) {
            if (importStmt.contains("jupiter")) {
                return "JUNIT5";
            } else if (importStmt.contains("org.junit")) {
                return "JUNIT4";
            } else if (importStmt.contains("testng")) {
                return "TESTNG";
            }
        }
        return "UNKNOWN";
    }

    /**
     * Calculate cyclomatic and other complexity metrics
     */
    @NotNull
    private ComplexityMetrics calculateComplexity(@NotNull PsiMethod method) {
        final int[] cyclomaticComplexity = {1};
        final int[] maxNestingDepth = {0};
        final int[] branchCount = {0};
        final int[] loopCount = {0};
        final int[] currentDepth = {0};
        
        method.accept(new JavaRecursiveElementVisitor() {
            @Override
            public void visitIfStatement(@NotNull PsiIfStatement statement) {
                super.visitIfStatement(statement);
                cyclomaticComplexity[0]++;
                branchCount[0]++;
                currentDepth[0]++;
                maxNestingDepth[0] = Math.max(maxNestingDepth[0], currentDepth[0]);
                currentDepth[0]--;
            }

            @Override
            public void visitWhileStatement(@NotNull PsiWhileStatement statement) {
                super.visitWhileStatement(statement);
                cyclomaticComplexity[0]++;
                loopCount[0]++;
                currentDepth[0]++;
                maxNestingDepth[0] = Math.max(maxNestingDepth[0], currentDepth[0]);
                currentDepth[0]--;
            }

            @Override
            public void visitForStatement(@NotNull PsiForStatement statement) {
                super.visitForStatement(statement);
                cyclomaticComplexity[0]++;
                loopCount[0]++;
                currentDepth[0]++;
                maxNestingDepth[0] = Math.max(maxNestingDepth[0], currentDepth[0]);
                currentDepth[0]--;
            }

            @Override
            public void visitForeachStatement(@NotNull PsiForeachStatement statement) {
                super.visitForeachStatement(statement);
                cyclomaticComplexity[0]++;
                loopCount[0]++;
                currentDepth[0]++;
                maxNestingDepth[0] = Math.max(maxNestingDepth[0], currentDepth[0]);
                currentDepth[0]--;
            }

            @Override
            public void visitSwitchStatement(@NotNull PsiSwitchStatement statement) {
                super.visitSwitchStatement(statement);
                PsiCodeBlock block = statement.getBody();
                if (block != null) {
                    int caseCount = block.getStatements().length;
                    cyclomaticComplexity[0] += caseCount;
                    branchCount[0] += caseCount;
                }
            }

            @Override
            public void visitCatchSection(@NotNull PsiCatchSection section) {
                super.visitCatchSection(section);
                cyclomaticComplexity[0]++;
                branchCount[0]++;
            }

            @Override
            public void visitConditionalExpression(@NotNull PsiConditionalExpression expression) {
                super.visitConditionalExpression(expression);
                cyclomaticComplexity[0]++;
                branchCount[0]++;
            }
        });
        
        return new ComplexityMetrics(
            cyclomaticComplexity[0],
            maxNestingDepth[0],
            branchCount[0],
            loopCount[0]
        );
    }

    /**
     * Build simplified Control Flow Graph from method PSI
     * According to ANALYTICS.md Section 8.1
     */
    @NotNull
    public List<CFGNode> buildCFG(@NotNull PsiMethod method) {
        List<CFGNode> cfgNodes = new ArrayList<>();

        method.accept(new JavaRecursiveElementVisitor() {
            @Override
            public void visitIfStatement(@NotNull PsiIfStatement statement) {
                super.visitIfStatement(statement);
                String condition = statement.getCondition() != null ?
                    statement.getCondition().getText() : "unknown";
                int line = getLineNumber(statement);
                Integer thenLine = statement.getThenBranch() != null ?
                    getLineNumber(statement.getThenBranch()) : null;
                Integer elseLine = statement.getElseBranch() != null ?
                    getLineNumber(statement.getElseBranch()) : null;

                cfgNodes.add(new CFGNode(
                    CFGNode.NodeType.IF,
                    condition,
                    line,
                    thenLine,
                    elseLine,
                    null
                ));
            }

            @Override
            public void visitTryStatement(@NotNull PsiTryStatement statement) {
                super.visitTryStatement(statement);
                int line = getLineNumber(statement);

                // Try-with-resources detection (Java 7+)
                // Check if try statement text contains "try (" which indicates resources
                String tryText = statement.getText();
                if (tryText.startsWith("try (")) {
                    cfgNodes.add(new CFGNode(
                        CFGNode.NodeType.TRY_WITH_RESOURCES,
                        "try-with-resources detected",
                        line,
                        null,
                        null,
                        null
                    ));
                    LOG.debug("Found try-with-resources at line {}", line);
                }

                // Multi-catch handling (Java 7+)
                for (PsiCatchSection catchSection : statement.getCatchSections()) {
                    PsiParameter param = catchSection.getParameter();
                    String exceptionType = param != null ?
                        param.getType().getCanonicalText() : "Exception";
                    int catchLine = getLineNumber(catchSection);

                    // Check if multi-catch (IOException | SQLException) by text
                    String catchText = catchSection.getText();
                    if (catchText.contains("|")) {
                        cfgNodes.add(new CFGNode(
                            CFGNode.NodeType.MULTI_CATCH,
                            "multi-catch: " + exceptionType,
                            catchLine,
                            null,
                            null,
                            new CFGNode.CatchInfo(exceptionType, catchLine)
                        ));
                        LOG.debug("Found multi-catch at line {}: {}", catchLine, exceptionType);
                    } else {
                        cfgNodes.add(new CFGNode(
                            CFGNode.NodeType.CATCH,
                            null,
                            line,
                            null,
                            null,
                            new CFGNode.CatchInfo(exceptionType, catchLine)
                        ));
                    }
                }
            }

            @Override
            public void visitSynchronizedStatement(@NotNull PsiSynchronizedStatement statement) {
                super.visitSynchronizedStatement(statement);
                
                int line = getLineNumber(statement);
                String syncText = statement.getText();

                cfgNodes.add(new CFGNode(
                    CFGNode.NodeType.SYNCHRONIZED,
                    "synchronized: " + (syncText.length() > 50 ? syncText.substring(0, 50) + "..." : syncText),
                    line,
                    null,
                    null,
                    null
                ));
                
                LOG.debug("Found synchronized block at line {}", line);
            }

            @Override
            public void visitMethodReferenceExpression(@NotNull PsiMethodReferenceExpression expression) {
                super.visitMethodReferenceExpression(expression);
                
                // Method reference: ClassName::methodName or instance::methodName
                int line = getLineNumber(expression);
                String refText = expression.getText();
                
                cfgNodes.add(new CFGNode(
                    CFGNode.NodeType.METHOD_REF,
                    "method ref: " + refText,
                    line,
                    null,
                    null,
                    null
                ));
                
                LOG.debug("Found method reference at line {}: {}", line, refText);
            }

            @Override
            public void visitNewExpression(@NotNull PsiNewExpression expression) {
                super.visitNewExpression(expression);
                
                // Anonymous class: new Interface() { ... }
                PsiClass anonymousClass = expression.getAnonymousClass();
                if (anonymousClass != null) {
                    int line = getLineNumber(expression);
                    String className = anonymousClass.getName() != null ? 
                        anonymousClass.getName() : "anonymous";
                    
                    cfgNodes.add(new CFGNode(
                        CFGNode.NodeType.ANONYMOUS_CLASS,
                        "anonymous class: new " + className + "() {...}",
                        line,
                        null,
                        null,
                        null
                    ));
                    
                    // Recursively extract branches from anonymous class body
                    extractBranchesFromElement(anonymousClass, cfgNodes);
                    
                    LOG.debug("Found anonymous class at line {}", line);
                }
            }

            @Override
            public void visitSwitchStatement(@NotNull PsiSwitchStatement statement) {
                super.visitSwitchStatement(statement);
                String condition = statement.getExpression() != null ?
                    statement.getExpression().getText() : "unknown";
                int line = getLineNumber(statement);
                PsiCodeBlock block = statement.getBody();

                if (block != null) {
                    // Extract individual case branches
                    PsiStatement[] statements = block.getStatements();
                    for (PsiStatement stmt : statements) {
                        if (stmt instanceof PsiSwitchLabelStatement) {
                            PsiSwitchLabelStatement label = (PsiSwitchLabelStatement) stmt;
                            // Get case value from label text
                            String caseValue = label.getText().replace("case", "").replace("default", "default").trim();
                            if (caseValue.isEmpty()) caseValue = "default";

                            // Check for guarded pattern (Java 21+)
                            // Example: case Point(int x, int y) when x > 0 -> ...
                            if (caseValue.contains(" when ")) {
                                cfgNodes.add(new CFGNode(
                                    CFGNode.NodeType.GUARDED_PATTERN,
                                    condition + " -> " + caseValue,
                                    getLineNumber(label),
                                    null,
                                    null,
                                    null
                                ));
                                LOG.debug("Found guarded pattern at line {}: {}", getLineNumber(label), caseValue);
                            } else {
                                cfgNodes.add(new CFGNode(
                                    CFGNode.NodeType.SWITCH,
                                    condition + " -> case " + caseValue,
                                    getLineNumber(label),
                                    null,
                                    null,
                                    null
                                ));
                            }
                        }
                    }
                } else {
                    // Fallback if no body
                    cfgNodes.add(new CFGNode(
                        CFGNode.NodeType.SWITCH,
                        condition,
                        line,
                        null,
                        null,
                        null
                    ));
                }
            }

            @Override
            public void visitConditionalExpression(@NotNull PsiConditionalExpression expression) {
                super.visitConditionalExpression(expression);
                // Handle ternary operator: condition ? trueBranch : falseBranch
                String condition = expression.getCondition() != null ?
                    expression.getCondition().getText() : "unknown";
                int line = getLineNumber(expression);

                cfgNodes.add(new CFGNode(
                    CFGNode.NodeType.IF, // Ternary is essentially an if-else
                    "ternary: " + condition + " ? ... : ...",
                    line,
                    null, // true branch inline
                    null, // false branch inline
                    null
                ));
            }

            @Override
            public void visitInstanceOfExpression(@NotNull PsiInstanceOfExpression expression) {
                super.visitInstanceOfExpression(expression);
                
                // Pattern matching instanceof (Java 16+)
                // Example: if (obj instanceof String s) { ... }
                PsiPattern pattern = expression.getPattern();
                if (pattern != null) {
                    String patternText = pattern.getText();
                    String condition = "instanceof " + patternText;
                    int line = getLineNumber(expression);

                    // Check for record pattern (Java 21+)
                    // Example: if (obj instanceof Point(int x, int y))
                    // Using string check since PsiRecordPattern may not be available in all IDEA versions
                    if (patternText.contains("(") && patternText.contains(")")) {
                        cfgNodes.add(new CFGNode(
                            CFGNode.NodeType.RECORD_PATTERN,
                            "record pattern: " + patternText,
                            line,
                            null,
                            null,
                            null
                        ));
                        LOG.debug("Found record pattern instanceof at line {}: {}", line, patternText);
                    } else {
                        cfgNodes.add(new CFGNode(
                            CFGNode.NodeType.PATTERN_MATCHING,
                            condition,
                            line,
                            null,
                            null,
                            null
                        ));
                        LOG.debug("Found pattern matching instanceof at line {}: {}", line, patternText);
                    }
                }
            }

            @Override
            public void visitAssertStatement(@NotNull PsiAssertStatement statement) {
                super.visitAssertStatement(statement);
                
                String condition = statement.getAssertCondition().getText();
                int line = getLineNumber(statement);

                cfgNodes.add(new CFGNode(
                    CFGNode.NodeType.ASSERT,
                    "assert: " + condition,
                    line,
                    null,
                    null,
                    null
                ));
                
                LOG.debug("Found assert statement at line {}: {}", line, condition);
            }

            @Override
            public void visitLambdaExpression(@NotNull PsiLambdaExpression expression) {
                super.visitLambdaExpression(expression);
                
                // Extract branches from lambda body
                PsiElement body = expression.getBody();
                if (body != null) {
                    int line = getLineNumber(expression);
                    String lambdaParams = expression.getParameterList() != null ? 
                        expression.getParameterList().getText() : "";
                    
                    cfgNodes.add(new CFGNode(
                        CFGNode.NodeType.LAMBDA,
                        "lambda: (" + lambdaParams + ") -> {...}",
                        line,
                        null,
                        null,
                        null
                    ));
                    
                    // Recursively extract branches from lambda body
                    extractBranchesFromElement(body, cfgNodes);
                    
                    LOG.debug("Found lambda expression at line {} with params: {}", line, lambdaParams);
                }
            }

            @Override
            public void visitYieldStatement(@NotNull PsiYieldStatement statement) {
                super.visitYieldStatement(statement);
                
                PsiExpression valueExpr = statement.getExpression();
                String value = valueExpr != null ? valueExpr.getText() : "unknown";
                int line = getLineNumber(statement);

                cfgNodes.add(new CFGNode(
                    CFGNode.NodeType.YIELD,
                    "yield: " + value,
                    line,
                    null,
                    null,
                    null
                ));
                
                LOG.debug("Found yield statement at line {}: {}", line, value);
            }

            @Override
            public void visitWhileStatement(@NotNull PsiWhileStatement statement) {
                super.visitWhileStatement(statement);
                String condition = statement.getCondition() != null ? 
                    statement.getCondition().getText() : "loop condition";
                int line = getLineNumber(statement);

                cfgNodes.add(new CFGNode(
                    CFGNode.NodeType.LOOP,
                    "while (" + condition + ")",
                    line,
                    null,
                    null,
                    null
                ));
            }

            @Override
            public void visitForStatement(@NotNull PsiForStatement statement) {
                super.visitForStatement(statement);
                PsiStatement init = statement.getInitialization();
                PsiExpression condition = statement.getCondition();
                PsiStatement update = statement.getUpdate();
                
                String forCondition = "";
                if (init != null) forCondition += init.getText();
                forCondition += "; ";
                if (condition != null) forCondition += condition.getText();
                forCondition += "; ";
                if (update != null) forCondition += update.getText();
                
                int line = getLineNumber(statement);

                cfgNodes.add(new CFGNode(
                    CFGNode.NodeType.LOOP,
                    "for (" + forCondition + ")",
                    line,
                    null,
                    null,
                    null
                ));
            }

            @Override
            public void visitForeachStatement(@NotNull PsiForeachStatement statement) {
                super.visitForeachStatement(statement);
                PsiParameter param = statement.getIterationParameter();
                PsiExpression iterable = statement.getIteratedValue();
                
                String paramName = param != null ? param.getName() : "item";
                String iterableText = iterable != null ? iterable.getText() : "collection";
                
                int line = getLineNumber(statement);

                cfgNodes.add(new CFGNode(
                    CFGNode.NodeType.LOOP,
                    "foreach (" + paramName + " : " + iterableText + ")",
                    line,
                    null,
                    null,
                    null
                ));
            }

            @Override
            public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
                super.visitMethodCallExpression(expression);
                // Detect stream().forEach() and similar patterns
                String methodText = expression.getMethodExpression().getText();
                
                // Enhanced stream/optional detection
                if (methodText.contains(".filter") || methodText.contains(".peek")) {
                    String fullText = expression.getText();
                    int line = getLineNumber(expression);

                    // Check for reactive streams (Mono/Flux)
                    PsiType type = expression.getType();
                    String typeName = type != null ? type.getCanonicalText() : "";
                    
                    if (typeName.contains("Mono") || typeName.contains("Flux") || 
                        typeName.contains("reactor") || typeName.contains("rxjava")) {
                        cfgNodes.add(new CFGNode(
                            CFGNode.NodeType.REACTIVE_FILTER,
                            "reactive.filter: " + (fullText.length() > 80 ? fullText.substring(0, 80) + "..." : fullText),
                            line,
                            null,
                            null,
                            null
                        ));
                        LOG.debug("Found reactive filter at line {}", line);
                    } else {
                        cfgNodes.add(new CFGNode(
                            CFGNode.NodeType.STREAM_FILTER,
                            "stream.filter: " + (fullText.length() > 80 ? fullText.substring(0, 80) + "..." : fullText),
                            line,
                            null,
                            null,
                            null
                        ));
                        LOG.debug("Found stream filter at line {}", line);
                    }
                }
                
                if (methodText.contains(".map")) {
                    String fullText = expression.getText();
                    int line = getLineNumber(expression);

                    PsiType type = expression.getType();
                    String typeName = type != null ? type.getCanonicalText() : "";
                    
                    if (typeName.contains("Mono") || typeName.contains("Flux") ||
                        typeName.contains("reactor") || typeName.contains("rxjava")) {
                        cfgNodes.add(new CFGNode(
                            CFGNode.NodeType.REACTIVE_MAP,
                            "reactive.map: " + (fullText.length() > 80 ? fullText.substring(0, 80) + "..." : fullText),
                            line,
                            null,
                            null,
                            null
                        ));
                        LOG.debug("Found reactive map at line {}", line);
                    } else {
                        cfgNodes.add(new CFGNode(
                            CFGNode.NodeType.STREAM_MAP,
                            "stream.map: " + (fullText.length() > 80 ? fullText.substring(0, 80) + "..." : fullText),
                            line,
                            null,
                            null,
                            null
                        ));
                        LOG.debug("Found stream map at line {}", line);
                    }
                }
                
                if (methodText.contains(".forEach")) {
                    String fullText = expression.getText();
                    int line = getLineNumber(expression);

                    cfgNodes.add(new CFGNode(
                        CFGNode.NodeType.STREAM_FOREACH,
                        "stream.forEach: " + (fullText.length() > 80 ? fullText.substring(0, 80) + "..." : fullText),
                        line,
                        null,
                        null,
                        null
                    ));
                    LOG.debug("Found stream forEach at line {}", line);
                }
                
                // Reactive error handling
                if (methodText.contains(".onError")) {
                    String fullText = expression.getText();
                    int line = getLineNumber(expression);

                    cfgNodes.add(new CFGNode(
                        CFGNode.NodeType.REACTIVE_ON_ERROR,
                        "reactive.onError: " + (fullText.length() > 80 ? fullText.substring(0, 80) + "..." : fullText),
                        line,
                        null,
                        null,
                        null
                    ));
                    LOG.debug("Found reactive error handler at line {}", line);
                }
                
                // Optional methods
                if (methodText.contains(".ifPresent")) {
                    int line = getLineNumber(expression);
                    
                    if (methodText.contains(".ifPresentOrElse")) {
                        cfgNodes.add(new CFGNode(
                            CFGNode.NodeType.OPTIONAL_IF_PRESENT,
                            "optional.ifPresentOrElse(present)",
                            line,
                            null,
                            null,
                            null
                        ));
                        cfgNodes.add(new CFGNode(
                            CFGNode.NodeType.OPTIONAL_IF_EMPTY,
                            "optional.ifPresentOrElse(empty)",
                            line,
                            null,
                            null,
                            null
                        ));
                        LOG.debug("Found optional.ifPresentOrElse at line {}", line);
                    } else {
                        cfgNodes.add(new CFGNode(
                            CFGNode.NodeType.OPTIONAL_IF_PRESENT,
                            "optional.ifPresent",
                            line,
                            null,
                            null,
                            null
                        ));
                        LOG.debug("Found optional.ifPresent at line {}", line);
                    }
                }
                
                if (methodText.contains(".stream")) {
                    String fullText = expression.getText();
                    int line = getLineNumber(expression);

                    cfgNodes.add(new CFGNode(
                        CFGNode.NodeType.LOOP,
                        "stream/forEach: " + (fullText.length() > 100 ? fullText.substring(0, 100) + "..." : fullText),
                        line,
                        null,
                        null,
                        null
                    ));
                }
            }

            @Override
            public void visitReturnStatement(@NotNull PsiReturnStatement statement) {
                super.visitReturnStatement(statement);
                int line = getLineNumber(statement);
                
                cfgNodes.add(new CFGNode(
                    CFGNode.NodeType.RETURN,
                    null,
                    line,
                    null,
                    null,
                    null
                ));
            }

            @Override
            public void visitThrowStatement(@NotNull PsiThrowStatement statement) {
                super.visitThrowStatement(statement);
                int line = getLineNumber(statement);
                String exceptionType = statement.getException() != null ? 
                    statement.getException().getType().getPresentableText() : "Throwable";
                
                cfgNodes.add(new CFGNode(
                    CFGNode.NodeType.THROW,
                    exceptionType,
                    line,
                    null,
                    null,
                    null
                ));
            }
        });
        
        return cfgNodes;
    }

    /**
     * Extract dependencies with source code and method signatures
     * @param method The method being analyzed
     * @param containingClass The containing class
     * @param includeSourceCode Whether to include full source code
     * @param maxCodeLength Maximum length of source code to include
     * @return List of DependencyInfo with detailed information
     */
    @NotNull
    public List<DependencyInfo> extractDependenciesWithSource(
            @NotNull PsiMethod method,
            @Nullable PsiClass containingClass,
            boolean includeSourceCode,
            int maxCodeLength) {
        
        List<DependencyInfo> dependencyInfos = new ArrayList<>();
        Set<String> processedTypes = new HashSet<>();
        
        // Extract from class fields
        if (containingClass != null) {
            for (PsiField field : containingClass.getFields()) {
                PsiType fieldType = field.getType();
                String typeName = fieldType.getCanonicalText();
                
                if (!processedTypes.contains(typeName)) {
                    processedTypes.add(typeName);
                    boolean isExternal = isExternalDependency(field);
                    boolean nullable = isNullable(field);
                    
                    DependencyInfo info = extractDependencyInfo(
                        field.getName(),
                        typeName,
                        isExternal,
                        nullable,
                        includeSourceCode,
                        maxCodeLength,
                        method.getProject()
                    );
                    dependencyInfos.add(info);
                }
            }
        }
        
        // Extract from method parameters
        for (PsiParameter param : method.getParameterList().getParameters()) {
            String typeName = param.getType().getCanonicalText();
            
            if (!processedTypes.contains(typeName)) {
                processedTypes.add(typeName);
                boolean nullable = isNullable(param);
                
                DependencyInfo info = extractDependencyInfo(
                    param.getName(),
                    typeName,
                    isComplexType(typeName),
                    nullable,
                    includeSourceCode,
                    maxCodeLength,
                    method.getProject()
                );
                dependencyInfos.add(info);
            }
        }
        
        return dependencyInfos;
    }

    /**
     * Extract detailed information about a single dependency
     */
    @NotNull
    private DependencyInfo extractDependencyInfo(
            @NotNull String name,
            @NotNull String type,
            boolean isExternal,
            boolean nullable,
            boolean includeSourceCode,
            int maxCodeLength,
            @NotNull Project project) {
        
        String sourceCode = "";
        List<DependencyInfo.MethodInfo> methods = new ArrayList<>();
        String interfaceContract = "";
        List<DependencyInfo.ImplementationInfo> springImplementations = new ArrayList<>();
        List<DependencyInfo.LombokMethodInfo> lombokMethods = new ArrayList<>();
        DependencyInfo.MapStructInfo mapStructInfo = null;
        
        try {
            // Find the class definition
            PsiClass dependencyClass = findClassByName(type, project);
            
            if (dependencyClass != null) {
                // Extract method signatures
                for (PsiMethod method : dependencyClass.getMethods()) {
                    if (!method.isConstructor() && !method.hasModifierProperty(PsiModifier.STATIC)) {
                        List<String> parameters = Arrays.stream(method.getParameterList().getParameters())
                            .map(p -> p.getType().getCanonicalText() + " " + p.getName())
                            .collect(Collectors.toList());
                        
                        PsiReferenceList throwsList = method.getThrowsList();
                        PsiJavaCodeReferenceElement[] throwRefs = throwsList.getReferenceElements();
                        List<String> exceptions = new ArrayList<>();
                        for (PsiJavaCodeReferenceElement ref : throwRefs) {
                            exceptions.add(ref.getText());
                        }
                        
                        String javadoc = extractMethodJavadoc(method);
                        
                        methods.add(new DependencyInfo.MethodInfo(
                            method.getName(),
                            method.getReturnType() != null ? method.getReturnType().getCanonicalText() : "void",
                            parameters,
                            exceptions,
                            javadoc
                        ));
                    }
                }
                
                // Extract source code if requested
                if (includeSourceCode) {
                    sourceCode = dependencyClass.getText();
                    if (sourceCode.length() > maxCodeLength) {
                        sourceCode = sourceCode.substring(0, maxCodeLength) + "\n... [truncated]";
                    }
                }
                
                // Extract interface contract
                interfaceContract = extractInterfaceContract(dependencyClass);
                
                // REQ 2: Find Spring implementations if this is an interface
                if (dependencyClass.isInterface()) {
                    springImplementations = DependencyInfoExtractor.findSpringImplementations(
                        dependencyClass, project
                    );
                }
                
                // Lombok: Detect Lombok annotations
                lombokMethods = DependencyInfoExtractor.extractLombokGeneratedMethods(dependencyClass);
                
                // MapStruct: Detect MapStruct mapper
                mapStructInfo = DependencyInfoExtractor.extractMapStructInfo(dependencyClass, project);
            }
        } catch (Exception e) {
            System.err.println("Failed to extract dependency info for " + type + ": " + e.getMessage());
        }
        
        return new DependencyInfo(name, type, isExternal, nullable, sourceCode, methods, 
                                  interfaceContract, springImplementations, lombokMethods, mapStructInfo);
    }

    /**
     * Find class by name in project scope
     */
    @Nullable
    private PsiClass findClassByName(@NotNull String className, @NotNull Project project) {
        try {
            PsiShortNamesCache namesCache = PsiShortNamesCache.getInstance(project);
            GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
            
            PsiClass[] classes = namesCache.getClassesByName(className, scope);
            if (classes.length > 0) {
                return classes[0];
            }
            
            // Try to find by FQN
            return com.intellij.psi.JavaPsiFacade.getInstance(project)
                .findClass(className, scope);
        } catch (Exception e) {
            System.err.println("Failed to find class " + className + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Extract method Javadoc
     */
    @NotNull
    private String extractMethodJavadoc(@NotNull PsiMethod method) {
        PsiDocComment docComment = method.getDocComment();
        if (docComment != null) {
            return docComment.getText().trim();
        }
        return "";
    }

    /**
     * Extract interface contract (methods and their signatures)
     */
    @NotNull
    private String extractInterfaceContract(@NotNull PsiClass psiClass) {
        StringBuilder contract = new StringBuilder();
        
        if (psiClass.isInterface()) {
            contract.append("interface ").append(psiClass.getName()).append(" {\n");
            for (PsiMethod method : psiClass.getMethods()) {
                contract.append("  ")
                    .append(method.getReturnType() != null ? method.getReturnType().getCanonicalText() : "void")
                    .append(" ").append(method.getName())
                    .append("(");
                
                String params = Arrays.stream(method.getParameterList().getParameters())
                    .map(p -> p.getType().getCanonicalText() + " " + p.getName())
                    .collect(Collectors.joining(", "));
                contract.append(params);
                contract.append(");\n");
            }
            contract.append("}");
        }
        
        return contract.toString();
    }

    /**
     * Extract DTO/POJO structures from method parameters and return type (REQ 5)
     */
    @NotNull
    public List<DTOInfo> extractDTOStructures(@NotNull PsiMethod method, int maxDepth) {
        List<DTOInfo> dtos = new ArrayList<>();
        Set<String> processedClasses = new HashSet<>();
        
        // Extract from parameters
        for (PsiParameter param : method.getParameterList().getParameters()) {
            PsiType type = param.getType();
            extractDTOFromType(type, dtos, processedClasses, maxDepth, false);
        }
        
        // Extract from return type
        if (method.getReturnType() != null) {
            extractDTOFromType(method.getReturnType(), dtos, processedClasses, maxDepth, false);
        }
        
        return dtos;
    }

    private void extractDTOFromType(@NotNull PsiType type, @NotNull List<DTOInfo> dtos,
                                     @NotNull Set<String> processed, int maxDepth, boolean isNested) {
        if (type instanceof PsiClassType) {
            PsiClassType classType = (PsiClassType) type;
            PsiClass psiClass = classType.resolve();
            
            if (psiClass != null && !isJavaStdLib(psiClass.getQualifiedName())) {
                String className = psiClass.getName();
                if (className != null && !processed.contains(className)) {
                    processed.add(className);
                    
                    List<DTOInfo.FieldInfo> fields = new ArrayList<>();
                    for (PsiField field : psiClass.getFields()) {
                        if (!field.hasModifierProperty(PsiModifier.STATIC) && !field.hasModifierProperty(PsiModifier.FINAL)) {
                            List<String> fieldAnns = Arrays.stream(field.getAnnotations())
                                .map(PsiAnnotation::getQualifiedName).filter(Objects::nonNull)
                                .collect(Collectors.toList());
                            boolean isPrim = field.getType() instanceof PsiPrimitiveType;
                            
                            fields.add(new DTOInfo.FieldInfo(field.getName(), field.getType().getCanonicalText(),
                                fieldAnns, isPrim, "get" + capitalize(field.getName()) + "()",
                                "set" + capitalize(field.getName()) + "(...)"));
                            
                            if (maxDepth > 0 && !isPrim && !isJavaStdLib(field.getType().getCanonicalText())) {
                                extractDTOFromType(field.getType(), dtos, processed, maxDepth - 1, true);
                            }
                        }
                    }
                    
                    List<String> classAnns = Arrays.stream(psiClass.getAnnotations())
                        .map(PsiAnnotation::getQualifiedName).filter(Objects::nonNull)
                        .collect(Collectors.toList());
                    
                    dtos.add(new DTOInfo(className, psiClass.getQualifiedName(), fields, classAnns, isNested, null));
                }
                
                PsiType[] params = classType.getParameters();
                for (PsiType p : params) {
                    extractDTOFromType(p, dtos, processed, maxDepth - 1, isNested);
                }
            }
        }
    }

    /**
     * Extract data transformations - how parameters change (REQ 3)
     */
    @NotNull
    public List<DataTransformation> extractDataTransformations(@NotNull PsiMethod method) {
        List<DataTransformation> transformations = new ArrayList<>();
        
        for (PsiParameter param : method.getParameterList().getParameters()) {
            String paramName = param.getName();
            if (paramName == null) continue;
            
            List<DataTransformation.TransformationStep> steps = new ArrayList<>();
            Map<String, String> intermediateVars = new HashMap<>();
            boolean[] isModified = {false};
            
            method.accept(new JavaRecursiveElementVisitor() {
                @Override
                public void visitAssignmentExpression(@NotNull PsiAssignmentExpression expr) {
                    super.visitAssignmentExpression(expr);
                    if (expr.getLExpression() instanceof PsiReferenceExpression) {
                        PsiReferenceExpression ref = (PsiReferenceExpression) expr.getLExpression();
                        if (paramName.equals(ref.getReferenceName())) {
                            isModified[0] = true;
                            String op = expr.getRExpression() instanceof PsiMethodCallExpression ?
                                "method_call" : "assignment";
                            steps.add(new DataTransformation.TransformationStep(
                                getLineNumber(expr), op, op.equals("method_call") ?
                                "Method call result" : "Reassigned", paramName
                            ));
                        }
                    }
                }
                
                @Override
                public void visitLocalVariable(@NotNull PsiLocalVariable variable) {
                    super.visitLocalVariable(variable);
                    if (variable.getInitializer() instanceof PsiReferenceExpression) {
                        PsiReferenceExpression ref = (PsiReferenceExpression) variable.getInitializer();
                        if (paramName.equals(ref.getReferenceName()) && variable.getName() != null) {
                            intermediateVars.put(variable.getName(), "Created from " + paramName);
                        }
                    }
                }
            });
            
            String finalUsage = isModified[0] ? "modified (" + steps.size() + " steps)" : "used as-is";
            
            if (!steps.isEmpty() || !intermediateVars.isEmpty()) {
                transformations.add(new DataTransformation(
                    paramName, param.getType().getCanonicalText(), steps,
                    finalUsage, isModified[0], intermediateVars
                ));
            }
        }
        
        return transformations;
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    /**
     * Extract called methods from the method body (REQ 1)
     * Finds all method calls and extracts their signatures
     */
    @NotNull
    public List<CalledMethodInfo> extractCalledMethods(@NotNull PsiMethod method, int maxDepth) {
        List<CalledMethodInfo> calledMethods = new ArrayList<>();
        Set<String> processedMethods = new HashSet<>();
        
        method.accept(new JavaRecursiveElementVisitor() {
            @Override
            public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
                super.visitMethodCallExpression(expression);
                
                PsiReferenceExpression methodRef = expression.getMethodExpression();
                PsiMethod calledMethod = (PsiMethod) methodRef.resolve();
                
                if (calledMethod != null) {
                    String qualifiedName = calledMethod.getContainingClass() != null ? 
                        calledMethod.getContainingClass().getQualifiedName() : "unknown";
                    String methodName = calledMethod.getName();
                    String fullName = qualifiedName + "." + methodName;
                    
                    if (!processedMethods.contains(fullName) && !isJavaStdLib(qualifiedName)) {
                        processedMethods.add(fullName);
                        
                        List<String> parameters = Arrays.stream(calledMethod.getParameterList().getParameters())
                            .map(p -> p.getType().getCanonicalText() + " " + p.getName())
                            .collect(Collectors.toList());
                        
                        boolean hasSource = hasSourceCode(calledMethod);
                        String snippet = hasSource ? extractMethodSnippet(calledMethod) : "";
                        
                        calledMethods.add(new CalledMethodInfo(
                            qualifiedName,
                            methodName,
                            calledMethod.getReturnType() != null ? calledMethod.getReturnType().getCanonicalText() : "void",
                            parameters,
                            calledMethod.hasModifierProperty(PsiModifier.STATIC),
                            hasSource,
                            snippet,
                            maxDepth > 0 ? extractCalledMethods(calledMethod, maxDepth - 1).stream()
                                .map(CalledMethodInfo::methodName)
                                .collect(Collectors.toList()) : List.of()
                        ));
                    }
                }
            }
        });
        
        return calledMethods;
    }

    /**
     * Check if class is from standard Java library
     */
    private boolean isJavaStdLib(@NotNull String className) {
        return className.startsWith("java.") || 
               className.startsWith("javax.") ||
               className.startsWith("sun.") ||
               className.startsWith("com.sun.") ||
               className.startsWith("jdk.") ||
               className.equals("java.lang.Object") ||
               className.equals("java.lang.String");
    }

    /**
     * Check if method has source code available
     */
    private boolean hasSourceCode(@NotNull PsiMethod method) {
        PsiFile containingFile = method.getContainingFile();
        return containingFile != null && !containingFile.getName().endsWith(".class");
    }

    /**
     * Extract method code snippet (first 10 lines)
     */
    @NotNull
    private String extractMethodSnippet(@NotNull PsiMethod method) {
        String code = method.getText();
        String[] lines = code.split("\n");
        int maxLines = Math.min(lines.length, 10);
        return String.join("\n", Arrays.copyOfRange(lines, 0, maxLines)) + 
               (lines.length > 10 ? "\n..." : "");
    }

    /**
     * Check if class is a Spring REST Controller
     */
    public boolean isRestController(@Nullable PsiClass psiClass) {
        if (psiClass == null) return false;
        PsiModifierList modifierList = psiClass.getModifierList();
        if (modifierList == null) return false;
        
        return modifierList.findAnnotation("org.springframework.web.bind.annotation.RestController") != null ||
               (modifierList.findAnnotation("org.springframework.stereotype.Controller") != null &&
                hasRequestMappingAnnotation(psiClass));
    }

    /**
     * Check if class is a Spring Service
     */
    public boolean isSpringService(@Nullable PsiClass psiClass) {
        if (psiClass == null) return false;
        PsiModifierList modifierList = psiClass.getModifierList();
        return modifierList != null && 
               modifierList.findAnnotation("org.springframework.stereotype.Service") != null;
    }

    /**
     * Check if class is a Spring Repository
     */
    public boolean isSpringRepository(@Nullable PsiClass psiClass) {
        if (psiClass == null) return false;
        PsiModifierList modifierList = psiClass.getModifierList();
        return modifierList != null && 
               (modifierList.findAnnotation("org.springframework.stereotype.Repository") != null ||
                modifierList.findAnnotation("org.springframework.data.jpa.repository.JpaRepository") != null);
    }

    /**
     * Check if class has @RequestMapping annotation
     */
    private boolean hasRequestMappingAnnotation(@NotNull PsiClass psiClass) {
        PsiModifierList modifierList = psiClass.getModifierList();
        if (modifierList == null) return false;
        
        return modifierList.findAnnotation("org.springframework.web.bind.annotation.RequestMapping") != null ||
               modifierList.findAnnotation("org.springframework.web.bind.annotation.GetMapping") != null ||
               modifierList.findAnnotation("org.springframework.web.bind.annotation.PostMapping") != null;
    }

    /**
     * Extract method source code
     */
    @Nullable
    private String extractMethodSource(@NotNull PsiMethod method) {
        return method.getText();
    }

    /**
     * Get line number for PSI element
     */
    private int getLineNumber(@NotNull PsiElement element) {
        return element.getTextRange().getStartOffset();
    }

    /**
     * Recursively extract branches from a PSI element (e.g., lambda body)
     * Used for nested branch extraction
     */
    private void extractBranchesFromElement(@NotNull PsiElement element, 
                                             @NotNull List<CFGNode> cfgNodes) {
        element.accept(new JavaRecursiveElementVisitor() {
            @Override
            public void visitIfStatement(@NotNull PsiIfStatement statement) {
                super.visitIfStatement(statement);
                String condition = statement.getCondition() != null ?
                    statement.getCondition().getText() : "unknown";
                int line = getLineNumber(statement);

                cfgNodes.add(new CFGNode(
                    CFGNode.NodeType.IF,
                    "[lambda] if (" + condition + ")",
                    line,
                    null,
                    null,
                    null
                ));
            }

            @Override
            public void visitConditionalExpression(@NotNull PsiConditionalExpression expression) {
                super.visitConditionalExpression(expression);
                String condition = expression.getCondition() != null ?
                    expression.getCondition().getText() : "unknown";
                int line = getLineNumber(expression);

                cfgNodes.add(new CFGNode(
                    CFGNode.NodeType.IF,
                    "[lambda] ternary: " + condition,
                    line,
                    null,
                    null,
                    null
                ));
            }

            @Override
            public void visitSwitchStatement(@NotNull PsiSwitchStatement statement) {
                super.visitSwitchStatement(statement);
                String condition = statement.getExpression() != null ?
                    statement.getExpression().getText() : "unknown";
                int line = getLineNumber(statement);

                cfgNodes.add(new CFGNode(
                    CFGNode.NodeType.SWITCH,
                    "[lambda] switch (" + condition + ")",
                    line,
                    null,
                    null,
                    null
                ));
            }

            @Override
            public void visitInstanceOfExpression(@NotNull PsiInstanceOfExpression expression) {
                super.visitInstanceOfExpression(expression);
                PsiPattern pattern = expression.getPattern();
                if (pattern != null) {
                    String patternText = pattern.getText();
                    int line = getLineNumber(expression);

                    cfgNodes.add(new CFGNode(
                        CFGNode.NodeType.PATTERN_MATCHING,
                        "[lambda] instanceof " + patternText,
                        line,
                        null,
                        null,
                        null
                    ));
                }
            }

            @Override
            public void visitAssertStatement(@NotNull PsiAssertStatement statement) {
                super.visitAssertStatement(statement);
                String condition = statement.getAssertCondition().getText();
                int line = getLineNumber(statement);

                cfgNodes.add(new CFGNode(
                    CFGNode.NodeType.ASSERT,
                    "[lambda] assert: " + condition,
                    line,
                    null,
                    null,
                    null
                ));
            }
        });
    }
}
