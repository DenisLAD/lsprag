package ru.sbrf.uddk.ai.testing.lsprag.context;

import com.intellij.psi.PsiMethod;
import ru.sbrf.uddk.ai.testing.lsprag.model.KeyToken;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MethodContext {
    private final PsiMethod targetMethod;
    private final List<KeyToken> keyTokens;
    private final Map<String, MethodInfo> calledMethods; // signature -> info
    private final int depth;
    private final List<DTOInfo> requestDTOs;      // НОВОЕ: DTO запросов
    private final List<DTOInfo> responseDTOs;     // НОВОЕ: DTO ответов

    public MethodContext(PsiMethod targetMethod, List<KeyToken> keyTokens,
                         Map<String, MethodInfo> calledMethods, int depth) {
        this.targetMethod = targetMethod;
        this.keyTokens = keyTokens;
        this.calledMethods = calledMethods;
        this.depth = depth;
        this.requestDTOs = new ArrayList<>();
        this.responseDTOs = new ArrayList<>();
    }

    public MethodContext(PsiMethod targetMethod, List<KeyToken> keyTokens,
                         Map<String, MethodInfo> calledMethods, int depth,
                         List<DTOInfo> requestDTOs, List<DTOInfo> responseDTOs) {
        this.targetMethod = targetMethod;
        this.keyTokens = keyTokens;
        this.calledMethods = calledMethods;
        this.depth = depth;
        this.requestDTOs = requestDTOs != null ? requestDTOs : new ArrayList<>();
        this.responseDTOs = responseDTOs != null ? responseDTOs : new ArrayList<>();
    }

    // Геттеры
    public List<DTOInfo> getRequestDTOs() {
        return requestDTOs;
    }

    public List<DTOInfo> getResponseDTOs() {
        return responseDTOs;
    }


    public PsiMethod getTargetMethod() {
        return targetMethod;
    }

    public List<KeyToken> getKeyTokens() {
        return keyTokens;
    }

    public Map<String, MethodInfo> getCalledMethods() {
        return calledMethods;
    }

    public int getDepth() {
        return depth;
    }

    public static class MethodInfo {
        private final String signature;
        private final String returnType;
        private final List<String> parameters;
        private final List<String> thrownExceptions;
        private final String bodySnippet;
        private final String returnTypeAnalysis;  // НОВОЕ ПОЛЕ

        // Новый конструктор с returnTypeAnalysis
        public MethodInfo(String signature, String returnType,
                          List<String> parameters, List<String> thrownExceptions,
                          String bodySnippet, String returnTypeAnalysis) {
            this.signature = signature;
            this.returnType = returnType;
            this.parameters = parameters;
            this.thrownExceptions = thrownExceptions;
            this.bodySnippet = bodySnippet;
            this.returnTypeAnalysis = returnTypeAnalysis;
        }

        // Старый конструктор для обратной совместимости
        public MethodInfo(String signature, String returnType,
                          List<String> parameters, List<String> thrownExceptions,
                          String bodySnippet) {
            this(signature, returnType, parameters, thrownExceptions, bodySnippet, null);
        }

        public String getReturnTypeAnalysis() {
            return returnTypeAnalysis;
        }

        public String getSignature() {
            return signature;
        }

        public String getReturnType() {
            return returnType;
        }

        public List<String> getParameters() {
            return parameters;
        }

        public List<String> getThrownExceptions() {
            return thrownExceptions;
        }

        public String getBodySnippet() {
            return bodySnippet;
        }
    }

    public static class DTOInfo {
        private final String className;
        private final String category;
        private final List<FieldInfo> fields;
        private final String jsonExample;

        public DTOInfo(String className, String category, List<FieldInfo> fields, String jsonExample) {
            this.className = className;
            this.category = category;
            this.fields = fields;
            this.jsonExample = jsonExample;
        }

        public String getName() {
            return className;
        }

        public String getClassName() {
            return className;
        }

        public String getCategory() {
            return category;
        }

        public List<FieldInfo> getFields() {
            return fields;
        }

        public String getJsonExample() {
            return jsonExample;
        }

        public static class FieldInfo {
            private final String name;
            private final String typeName;
            private final List<String> annotations;
            private final boolean nullable;

            public FieldInfo(String name, String typeName, List<String> annotations, boolean nullable) {
                this.name = name;
                this.typeName = typeName;
                this.annotations = annotations;
                this.nullable = nullable;
            }

            public String getName() {
                return name;
            }

            public String getTypeName() {
                return typeName;
            }

            public List<String> getAnnotations() {
                return annotations;
            }

            public boolean isNullable() {
                return nullable;
            }
        }
    }
}