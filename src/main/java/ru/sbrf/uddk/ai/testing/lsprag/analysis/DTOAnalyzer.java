package ru.sbrf.uddk.ai.testing.lsprag.analysis;

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PropertyUtilBase;
import org.jetbrains.annotations.NotNull;
import ru.sbrf.uddk.ai.testing.lsprag.utils.PropertyUtilBaseHelper;
import ru.sbrf.uddk.ai.testing.lsprag.utils.PsiUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import static ru.sbrf.uddk.ai.testing.lsprag.utils.PsiUtils.isPrimitiveOrWrapper;

public class DTOAnalyzer {

    @NotNull
    public DTOInfo analyze(@NotNull PsiType type) {
        return ReadAction.compute(() -> {
            if (!(type instanceof PsiClassType classType)) {
                return new DTOInfo(type.getPresentableText(), "primitive", Collections.emptyList());
            }

            PsiClass psiClass = classType.resolve();
            if (psiClass == null) {
                return new DTOInfo(type.getPresentableText(), "unknown", Collections.emptyList());
            }

            // Определяем тип: DTO, Entity, Record, etc.
            String category = classifyClass(psiClass);

            // Извлекаем поля
            List<FieldInfo> fields = extractFields(psiClass);

            // Рекурсивно анализируем вложенные типы
            for (FieldInfo field : fields) {
                if (!field.isPrimitive() && field.getNestedInfo() == null) {
                    field.setNestedInfo(analyze(field.getType()));
                }
            }

            return new DTOInfo(psiClass.getName(), category, fields);
        });
    }

    @NotNull
    private String classifyClass(@NotNull PsiClass psiClass) {
        if (psiClass.isRecord()) return "record";
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
    private List<FieldInfo> extractFields(@NotNull PsiClass psiClass) {
        List<FieldInfo> fields = new ArrayList<>();

        // Поля класса
        for (PsiField field : psiClass.getFields()) {
            if (field.hasModifierProperty(PsiModifier.STATIC) ||
                    field.hasModifierProperty(PsiModifier.FINAL) && field.getInitializer() != null) {
                continue; // пропускаем константы
            }

            FieldInfo info = new FieldInfo(
                    field.getName(),
                    field.getType().getPresentableText(),
                    field.getType(),
                    extractAnnotations(field),
                    isNullable(field)
            );
            fields.add(info);
        }

        // Свойства через геттеры (для случаев без явных полей)
        for (PsiMethod getter : PropertyUtilBaseHelper.getAllGetters(psiClass)) {
            String propName = PropertyUtilBase.getPropertyName(getter);
            if (propName != null && fields.stream().noneMatch(f -> f.getName().equals(propName))) {
                FieldInfo info = new FieldInfo(
                        propName,
                        getter.getReturnType().getPresentableText(),
                        getter.getReturnType(),
                        extractAnnotations(getter),
                        !isPrimitiveOrWrapper(getter.getReturnType())
                );
                fields.add(info);
            }
        }

        return fields;
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
                PropertyUtilBaseHelper.getAllGetters(psiClass).size();
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
        String type = field.getType().getPresentableText().toLowerCase();

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
        if (type.contains("uuid")) return UUID.randomUUID().toString();
        if (type.contains("localdate")) return "01-01-2001";
        if (type.contains("localdatetime")) return "01-01-2001 00:00:00";

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

        public DTOInfo(String name, String category, List<FieldInfo> fields) {
            this.name = name;
            this.category = category;
            this.fields = fields;
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

        @Override
        public String toString() {
            return String.format("%s{%s, fields=%d}", name, category, fields.size());
        }
    }

    public static class FieldInfo {
        private final String name;
        private final String typeName;
        private final PsiType type;
        private final List<String> annotations;
        private final boolean nullable;
        private DTOInfo nestedInfo;

        public FieldInfo(String name, String typeName, PsiType type,
                         List<String> annotations, boolean nullable) {
            this.name = name;
            this.typeName = typeName;
            this.type = type;
            this.annotations = annotations;
            this.nullable = nullable;
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

        public void setNestedInfo(DTOInfo nestedInfo) {
            this.nestedInfo = nestedInfo;
        }
    }
}