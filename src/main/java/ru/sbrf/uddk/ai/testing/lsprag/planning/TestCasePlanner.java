package ru.sbrf.uddk.ai.testing.lsprag.planning;

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import org.jetbrains.annotations.NotNull;
import ru.sbrf.uddk.ai.testing.lsprag.context.MethodContext;
import ru.sbrf.uddk.ai.testing.lsprag.model.KeyToken;
import ru.sbrf.uddk.ai.testing.lsprag.model.TestCase;
import ru.sbrf.uddk.ai.testing.lsprag.model.TokenRole;
import ru.sbrf.uddk.ai.testing.lsprag.utils.PsiUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TestCasePlanner {

    private static int counter = 0;

    @NotNull
    public List<TestCase> plan(@NotNull MethodContext context) {
        List<TestCase> testCases = new ArrayList<>();
        PsiMethod method = context.getTargetMethod();

        // 1. Базовый позитивный кейс
        testCases.add(createBasicPositiveCase(method));

        // 2. Кейсы для условных токенов
        for (KeyToken token : context.getKeyTokens()) {
            if (token.getRole() == TokenRole.CONDITION) {
                testCases.addAll(createConditionCases(method, token));
            }
        }

        // 3. Кейсы для исключений
        testCases.addAll(createExceptionCases(context));

        // 4. Кейсы для валидации параметров
        testCases.addAll(createValidationCases(method));

        return testCases;
    }

    private TestCase createBasicPositiveCase(PsiMethod method) {
        String endpoint = extractEndpoint(method);
        String httpMethod = extractHttpMethod(method);
        return new TestCase(
                generateId(),
                "Successful request to " + endpoint,
                "200",
                httpMethod,
                endpoint
        );
    }

    private List<TestCase> createConditionCases(PsiMethod method, KeyToken token) {
        List<TestCase> cases = new ArrayList<>();
        String endpoint = extractEndpoint(method);
        String httpMethod = extractHttpMethod(method);
        String conditionDesc = token.getName();

        // Case: condition TRUE
        TestCase trueCase = new TestCase(
                generateId(),
                "When " + conditionDesc + " is TRUE",
                "200",
                httpMethod,
                endpoint
        );
        trueCase.addInput("condition", true);
        cases.add(trueCase);

        // Case: condition FALSE
        TestCase falseCase = new TestCase(
                generateId(),
                "When " + conditionDesc + " is FALSE",
                "400",
                httpMethod,
                endpoint
        );
        falseCase.addInput("condition", false);
        cases.add(falseCase);

        return cases;
    }

    private List<TestCase> createExceptionCases(MethodContext context) {
        List<TestCase> cases = new ArrayList<>();
        String endpoint = extractEndpoint(context.getTargetMethod());
        String httpMethod = extractHttpMethod(context.getTargetMethod());

        for (MethodContext.MethodInfo info : context.getCalledMethods().values()) {
            for (String exception : info.getThrownExceptions()) {
                String status = mapExceptionToStatus(exception);
                TestCase tc = new TestCase(
                        generateId(),
                        "When " + info.getSignature() + " throws " + exception,
                        status,
                        httpMethod,
                        endpoint
                );
                tc.addInput("triggerException", exception);
                cases.add(tc);
            }
        }
        return cases;
    }

    private List<TestCase> createValidationCases(PsiMethod method) {
        List<TestCase> cases = new ArrayList<>();
        String endpoint = extractEndpoint(method);
        String httpMethod = extractHttpMethod(method);

        ReadAction.run(() -> {
            for (PsiParameter param : method.getParameterList().getParameters()) {
                if (hasValidationAnnotation(param)) {
                    TestCase invalidCase = new TestCase(
                            generateId(),
                            "Invalid " + param.getName() + " should return 400",
                            "400",
                            httpMethod,
                            endpoint
                    );
                    invalidCase.addInput(param.getName(), null);
                    cases.add(invalidCase);
                }
            }
        });
        return cases;
    }

    // Вспомогательные методы
    private String extractEndpoint(PsiMethod method) {
        return ReadAction.compute(() -> {
            for (PsiAnnotation ann : method.getAnnotations()) {
                String name = ann.getQualifiedName();
                if (name != null && name.contains("Mapping")) {
                    String value = PsiUtils.getAnnotationStringValue(ann, "value");
                    return value != null ? value : "/api/endpoint";
                }
            }
            return "/api/endpoint";
        });
    }

    private String extractHttpMethod(PsiMethod method) {
        return ReadAction.compute(() -> {
            for (PsiAnnotation ann : method.getAnnotations()) {
                String name = ann.getQualifiedName();
                if (name != null) {
                    if (name.endsWith("GetMapping")) return "GET";
                    if (name.endsWith("PostMapping")) return "POST";
                    if (name.endsWith("PutMapping")) return "PUT";
                    if (name.endsWith("DeleteMapping")) return "DELETE";
                    if (name.endsWith("PatchMapping")) return "PATCH";
                }
            }
            return "GET";
        });
    }

    private boolean hasValidationAnnotation(PsiParameter param) {
        return Arrays.stream(param.getAnnotations())
                .map(PsiAnnotation::getQualifiedName)
                .anyMatch(qn -> qn != null && (
                        qn.contains("Valid") || qn.contains("NotNull") ||
                                qn.contains("NotEmpty") || qn.contains("NotBlank")));
    }

    private String mapExceptionToStatus(String exception) {
        return switch (exception.toLowerCase()) {
            case "resourcenotfoundexception", "entitynotfoundexception" -> "404";
            case "illegalargumentexception", "validationexception" -> "400";
            case "unauthorizedexception", "accessdeniedexception" -> "401";
            case "forbiddenexception" -> "403";
            default -> "500";
        };
    }

    private String generateId() {
        return "TC_" + (++counter);
    }
}
