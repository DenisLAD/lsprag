package ru.sbrf.uddk.ai.testing.lsprag.analysis;

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.*;
import com.intellij.psi.util.PropertyUtilBase;
import org.jetbrains.annotations.NotNull;
import ru.sbrf.uddk.ai.testing.lsprag.utils.PropertyUtilBaseHelper;
import ru.sbrf.uddk.ai.testing.lsprag.utils.PsiUtils;

import java.util.*;
import java.util.stream.Collectors;

import static ru.sbrf.uddk.ai.testing.lsprag.utils.PsiUtils.isPrimitiveOrWrapper;

public class DTOAnalyzer {

    // Хранилище для всех найденных DTO, чтобы избежать повторного анализа
    private final Map<String, DTOInfo> analyzedDTOs = new HashMap<>();
    private final List<DTOInfo> allNestedDTOs = new ArrayList<>();

    @NotNull
    public DTOInfo analyze(@NotNull PsiType type) {
        analyzedDTOs.clear();
        allNestedDTOs.clear();

        return ReadAction.compute(() -> {
            DTOInfo rootInfo = analyzeInternal(type, new HashSet<>());

            // Добавляем все найденные вложенные DTO в корневой объект
            if (rootInfo != null) {
                rootInfo.getAllNestedDTOs().addAll(allNestedDTOs);
            }

            return rootInfo;
        });
    }

    @NotNull
    private DTOInfo analyzeInternal(@NotNull PsiType type, Set<String> processingStack) {
        if (!(type instanceof PsiClassType classType)) {
            return new DTOInfo(type.getPresentableText(), "primitive", Collections.emptyList(), new ArrayList<>());
        }

        PsiClass psiClass = classType.resolve();
        if (psiClass == null) {
            return new DTOInfo(type.getPresentableText(), "unknown", Collections.emptyList(), new ArrayList<>());
        }

        String className = psiClass.getQualifiedName();

        // Пропускаем java.* пакеты
        if (shouldSkipJavaClass(className)) {
            return new DTOInfo(psiClass.getName(), "java-class", Collections.emptyList(), new ArrayList<>());
        }

        // Проверяем, не анализировали ли мы уже этот класс
        String cacheKey = psiClass.getQualifiedName();
        if (analyzedDTOs.containsKey(cacheKey)) {
            return analyzedDTOs.get(cacheKey);
        }

        // Проверяем на циклические зависимости
        if (processingStack.contains(cacheKey)) {
            return new DTOInfo(psiClass.getName(), "cyclic", Collections.emptyList(), new ArrayList<>());
        }

        processingStack.add(cacheKey);

        // Определяем тип: DTO, Entity, Record, etc.
        String category = classifyClass(psiClass);

        // Если это не DTO-подобный класс, не анализируем его поля глубоко
        boolean isDTOLike = isDTOLikeClass(psiClass, category);

        // Извлекаем поля
        List<FieldInfo> fields = extractFields(psiClass, processingStack, isDTOLike);

        DTOInfo dtoInfo = new DTOInfo(psiClass.getName(), category, fields, new ArrayList<>());

        // Сохраняем в кэш
        if (isDTOLike) {
            analyzedDTOs.put(cacheKey, dtoInfo);

            // Добавляем в общий список всех вложенных DTO
            if (!allNestedDTOs.contains(dtoInfo)) {
                allNestedDTOs.add(dtoInfo);
            }
        }

        processingStack.remove(cacheKey);

        return dtoInfo;
    }

    private boolean shouldSkipJavaClass(String className) {
        if (className == null) return true;
        return className.startsWith("java.") ||
                className.startsWith("javax.") ||
                className.startsWith("jakarta.") ||
                className.startsWith("sun.") ||
                className.startsWith("com.sun.");
    }

    private boolean isDTOLikeClass(@NotNull PsiClass psiClass, String category) {
        return "record".equals(category) ||
                "lombok-dto".equals(category) ||
                "dto".equals(category) ||
                ("complex".equals(category) && !isEntityOrSpecial(psiClass));
    }

    private boolean isEntityOrSpecial(@NotNull PsiClass psiClass) {
        return psiClass.isEnum() ||
                psiClass.isInterface() ||
                PsiUtils.hasAnnotation(psiClass, "jakarta.persistence.Entity") ||
                PsiUtils.hasAnnotation(psiClass, "javax.persistence.Entity");
    }

    @NotNull
    private String classifyClass(@NotNull PsiClass psiClass) {
        if (psiClass.isRecord()) return "record";
        if (psiClass.isEnum()) return "enum";
        if (psiClass.isInterface()) return "interface";

        if (PsiUtils.hasAnnotation(psiClass, "lombok.Data") ||
                PsiUtils.hasAnnotation(psiClass, "lombok.Value")) {
            return "lombok-dto";
        }

        if (PsiUtils.hasAnnotation(psiClass, "jakarta.persistence.Entity") ||
                PsiUtils.hasAnnotation(psiClass, "javax.persistence.Entity")) {
            return "entity";
        }

        // Простой эвристический анализ: если есть только геттеры/сеттеры и нет бизнес-методов
        if (hasOnlyAccessors(psiClass)) {
            return "dto";
        }

        return "complex";
    }

    @NotNull
    private List<FieldInfo> extractFields(@NotNull PsiClass psiClass, Set<String> processingStack, boolean analyzeNested) {
        List<FieldInfo> fields = new ArrayList<>();

        // Поля класса
        for (PsiField field : psiClass.getFields()) {
            if (field.hasModifierProperty(PsiModifier.STATIC) ||
                    field.hasModifierProperty(PsiModifier.FINAL) && field.getInitializer() != null) {
                continue; // пропускаем константы
            }

            PsiType fieldType = field.getType();
            DTOInfo nestedInfo = null;

            // Анализируем вложенный тип только если это DTO-подобный класс и мы должны анализировать
            if (analyzeNested && shouldAnalyzeNestedType(fieldType)) {
                nestedInfo = analyzeNestedType(fieldType, processingStack);
            }

            FieldInfo info = new FieldInfo(
                    field.getName(),
                    fieldType.getPresentableText(),
                    fieldType,
                    extractAnnotations(field),
                    isNullable(field),
                    nestedInfo
            );
            fields.add(info);
        }

        // Свойства через геттеры (для случаев без явных полей)
        for (PsiMethod getter : PropertyUtilBaseHelper.getAllGetters(psiClass)) {
            String propName = PropertyUtilBase.getPropertyName(getter);
            if (propName != null && fields.stream().noneMatch(f -> f.getName().equals(propName))) {
                PsiType returnType = getter.getReturnType();
                if (returnType != null) {
                    DTOInfo nestedInfo = null;

                    if (analyzeNested && shouldAnalyzeNestedType(returnType)) {
                        nestedInfo = analyzeNestedType(returnType, processingStack);
                    }

                    FieldInfo info = new FieldInfo(
                            propName,
                            returnType.getPresentableText(),
                            returnType,
                            extractAnnotations(getter),
                            !isPrimitiveOrWrapper(returnType),
                            nestedInfo
                    );
                    fields.add(info);
                }
            }
        }

        return fields;
    }

    private boolean shouldAnalyzeNestedType(PsiType type) {
        if (isPrimitiveOrWrapper(type)) {
            return false;
        }

        // Извлекаем реальный тип из Generic
        PsiType realType = extractRealTypeFromGenerics(type);

        if (realType instanceof PsiClassType classType) {
            PsiClass psiClass = classType.resolve();
            if (psiClass != null) {
                String className = psiClass.getQualifiedName();
                // Не анализируем классы из java.* пакетов
                return !shouldSkipJavaClass(className) &&
                        !isPrimitiveOrWrapper(realType) &&
                        !isCollectionType(realType);
            }
        }

        return false;
    }

    @NotNull
    private PsiType extractRealTypeFromGenerics(PsiType type) {
        if (type instanceof PsiClassType classType) {
            PsiType[] parameters = classType.getParameters();

            // Если это коллекция или Optional, извлекаем параметр типа
            if (parameters.length > 0 && isCollectionOrOptional(classType)) {
                return extractRealTypeFromGenerics(parameters[0]);
            }
        }
        return type;
    }

    private boolean isCollectionOrOptional(PsiClassType classType) {
        String className = classType.getCanonicalText();
        return className.startsWith("java.util.List") ||
                className.startsWith("java.util.Set") ||
                className.startsWith("java.util.Collection") ||
                className.startsWith("java.util.Optional") ||
                className.startsWith("java.util.Map");
    }

    private boolean isCollectionType(PsiType type) {
        String text = type.getCanonicalText();
        return text.startsWith("java.util.List") ||
                text.startsWith("java.util.Set") ||
                text.startsWith("java.util.Collection") ||
                text.startsWith("java.util.Map") ||
                text.startsWith("java.util.Optional");
    }

    @NotNull
    private DTOInfo analyzeNestedType(PsiType type, Set<String> processingStack) {
        // Извлекаем реальный тип из Generic
        PsiType realType = extractRealTypeFromGenerics(type);

        if (realType instanceof PsiClassType) {
            return analyzeInternal(realType, processingStack);
        }

        return new DTOInfo(type.getPresentableText(), "unknown", Collections.emptyList(), new ArrayList<>());
    }

    @NotNull
    private List<String> extractAnnotations(@NotNull PsiModifierListOwner owner) {
        return Arrays.stream(owner.getAnnotations())
                .map(PsiAnnotation::getQualifiedName)
                .filter(Objects::nonNull)
                .filter(qn -> qn.contains("NotNull") || qn.contains("NotBlank") ||
                        qn.contains("Email") || qn.contains("Size") ||
                        qn.contains("JsonProperty") || qn.contains("JsonIgnore"))
                .collect(Collectors.toList());
    }

    private boolean isNullable(@NotNull PsiField field) {
        return !field.hasModifierProperty(PsiModifier.FINAL) &&
                !PsiUtils.hasAnnotation(field, "javax.validation.constraints.NotNull") &&
                !PsiUtils.hasAnnotation(field, "jakarta.validation.constraints.NotNull");
    }

    private boolean hasOnlyAccessors(@NotNull PsiClass psiClass) {
        long methodCount = Arrays.stream(psiClass.getMethods())
                .filter(m -> !m.isConstructor() && !m.hasModifierProperty(PsiModifier.STATIC))
                .count();
        long accessorCount = PropertyUtilBaseHelper.getAllGetters(psiClass).size() +
                PropertyUtilBaseHelper.getAllSetters(psiClass).size();
        return methodCount > 0 && methodCount == accessorCount;
    }

    /**
     * Генерация примера JSON для промпта
     */
    @NotNull
    public String generateJsonExample(@NotNull DTOInfo dto, int depth) {
        if (depth <= 0) return "\"...\"";

        StringBuilder json = new StringBuilder("{");
        boolean first = true;

        for (FieldInfo field : dto.getFields()) {
            if (!first) json.append(",");
            first = false;

            json.append("\"").append(field.getName()).append("\":");
            json.append(generateValueExample(field, depth - 1));
        }

        json.append("}");
        return json.toString();
    }

    @NotNull
    private String generateValueExample(@NotNull FieldInfo field, int depth) {
        String type = field.getTypeName().toLowerCase();

        if (type.contains("string")) {
            return field.getAnnotations().stream().anyMatch(a -> a.contains("Email"))
                    ? "\"user@example.com\"" : "\"example\"";
        }
        if (type.contains("integer") || type.contains("long")) return "123";
        if (type.contains("boolean")) return "true";
        if (type.contains("double") || type.contains("float")) return "123.45";
        if (type.contains("list") || type.contains("array")) return "[]";
        if (type.contains("map")) return "{}";
        if (type.contains("optional")) return "null";
        if (type.contains("uuid")) return "\"" + UUID.randomUUID() + "\"";
        if (type.contains("localdate")) return "\"2001-01-01\"";
        if (type.contains("localdatetime")) return "\"2001-01-01T00:00:00\"";

        // Вложенный DTO
        if (field.getNestedInfo() != null && depth > 0) {
            return generateJsonExample(field.getNestedInfo(), depth);
        }

        return field.isNullable() ? "null" : "\"value\"";
    }

    // === Data classes ===

    public static class DTOInfo {
        private final String name;
        private final String category;
        private final List<FieldInfo> fields;
        private final List<DTOInfo> allNestedDTOs;

        public DTOInfo(String name, String category, List<FieldInfo> fields, List<DTOInfo> allNestedDTOs) {
            this.name = name;
            this.category = category;
            this.fields = fields;
            this.allNestedDTOs = allNestedDTOs;
        }

        public String getName() {
            return name;
        }

        public String getCategory() {
            return category;
        }

        public List<FieldInfo> getFields() {
            return fields;
        }

        public List<DTOInfo> getAllNestedDTOs() {
            return allNestedDTOs;
        }

        @Override
        public String toString() {
            return String.format("%s{%s, fields=%d, nestedDTOs=%d}",
                    name, category, fields.size(), allNestedDTOs.size());
        }
    }

    public static class FieldInfo {
        private final String name;
        private final String typeName;
        private final PsiType type;
        private final List<String> annotations;
        private final boolean nullable;
        private final DTOInfo nestedInfo;

        public FieldInfo(String name, String typeName, PsiType type,
                         List<String> annotations, boolean nullable, DTOInfo nestedInfo) {
            this.name = name;
            this.typeName = typeName;
            this.type = type;
            this.annotations = annotations;
            this.nullable = nullable;
            this.nestedInfo = nestedInfo;
        }

        public String getName() {
            return name;
        }

        public PsiType getType() {
            return type;
        }

        public String getTypeName() {
            return typeName;
        }

        public boolean isPrimitive() {
            return isPrimitiveOrWrapper(type);
        }

        public List<String> getAnnotations() {
            return annotations;
        }

        public boolean isNullable() {
            return nullable;
        }

        public DTOInfo getNestedInfo() {
            return nestedInfo;
        }
    }
}