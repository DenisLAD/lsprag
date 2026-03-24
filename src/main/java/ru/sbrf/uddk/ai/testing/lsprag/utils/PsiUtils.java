package ru.sbrf.uddk.ai.testing.lsprag.utils;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiArrayInitializerMemberValue;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Objects;

/**
 * Утилиты для работы с PSI (Program Structure Interface)
 */
public class PsiUtils {

    /**
     * Форматирует сигнатуру метода для отображения
     */
    @NotNull
    public static String getMethodSignature(@NotNull PsiMethod method) {
        StringBuilder sb = new StringBuilder();

        // Модификаторы
        if (method.hasModifierProperty(PsiModifier.PUBLIC)) sb.append("public ");
        if (method.hasModifierProperty(PsiModifier.PROTECTED)) sb.append("protected ");
        if (method.hasModifierProperty(PsiModifier.PRIVATE)) sb.append("private ");
        if (method.hasModifierProperty(PsiModifier.STATIC)) sb.append("static ");
        if (method.hasModifierProperty(PsiModifier.ABSTRACT)) sb.append("abstract ");

        // Возвращаемый тип
        sb.append(method.getReturnType().getPresentableText()).append(" ");

        // Имя метода
        sb.append(method.getName()).append("(");

        // Параметры
        PsiParameter[] params = method.getParameterList().getParameters();
        for (int i = 0; i < params.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(getParameterSignature(params[i]));
        }
        sb.append(")");

        // Исключения
        PsiClassType[] exceptions = method.getThrowsList().getReferencedTypes();
        if (exceptions.length > 0) {
            sb.append(" throws ");
            for (int i = 0; i < exceptions.length; i++) {
                if (i > 0) sb.append(", ");
                sb.append(exceptions[i].getPresentableText());
            }
        }

        return sb.toString();
    }

    /**
     * Создаёт уникальную сигнатуру метода для кэширования и сравнения
     * Формат: package.ClassName.methodName(paramType1,paramType2)
     */
    @NotNull
    public static String getUniqueMethodSignature(@NotNull PsiMethod method) {
        StringBuilder sb = new StringBuilder();

        // Класс-владелец
        PsiClass containingClass = method.getContainingClass();
        if (containingClass != null) {
            String qualifiedName = containingClass.getQualifiedName();
            if (qualifiedName != null) {
                sb.append(qualifiedName);
            } else {
                sb.append(containingClass.getName());
            }
        } else {
            sb.append("unknown");
        }

        sb.append(".").append(method.getName());

        // Параметры для уникальности (важно для перегруженных методов)
        sb.append("(");
        PsiParameter[] params = method.getParameterList().getParameters();
        ReadAction.run(() -> {
            for (int i = 0; i < params.length; i++) {
                if (i > 0) sb.append(",");
                PsiType type = params[i].getType();
                sb.append(type.getPresentableText());
            }
            sb.append(")");
        });

        return sb.toString();
    }

    /**
     * Создаёт строковое представление параметра метода
     * Формат: TypeName paramName
     */
    @NotNull
    public static String getParameterSignature(@NotNull PsiParameter parameter) {
        StringBuilder sb = new StringBuilder();

        // Аннотации параметра (например, @PathVariable, @RequestBody)
        for (PsiAnnotation annotation : parameter.getAnnotations()) {
            String shortName = annotation.getQualifiedName();
            if (shortName != null) {
                // Берём только простое имя аннотации
                shortName = shortName.substring(shortName.lastIndexOf('.') + 1);
                sb.append("@").append(shortName).append(" ");
            }
        }

        // Тип параметра
        ReadAction.run(() -> {
            sb.append(parameter.getType().getPresentableText());
        });

        // Имя параметра (если есть)
        String name = parameter.getName();
        if (name != null && !name.isEmpty()) {
            sb.append(" ").append(name);
        }

        return sb.toString();
    }

    /**
     * Проверяет, является ли тип экземпляром или наследником указанного типа
     *
     * @param type              Проверяемый тип
     * @param qualifiedTypeName Полное имя типа для проверки (например, "java.util.List")
     * @return true если тип соответствует
     */
    public static boolean isTypeOf(@NotNull PsiType type, @NotNull String qualifiedTypeName) {
        if (type instanceof PsiClassType classType) {
            PsiClass psiClass = ReadAction.compute(classType::resolve);
            if (psiClass == null) {
                // Если не удалось разрешить, сравниваем по имени
                return type.getPresentableText().contains(qualifiedTypeName.substring(
                        qualifiedTypeName.lastIndexOf('.') + 1));
            }

            // Проверяем сам класс
            if (qualifiedTypeName.equals(psiClass.getQualifiedName())) {
                return true;
            }

            // Проверяем наследников и интерфейсы
            return isSubclassOf(psiClass, qualifiedTypeName);
        }

        // Для примитивов и других типов
        return type.getPresentableText().equals(qualifiedTypeName);
    }

    /**
     * Рекурсивная проверка наследования
     */
    private static boolean isSubclassOf(@NotNull PsiClass psiClass, @NotNull String qualifiedTypeName) {
        // Проверяем родительский класс
        PsiClass superClass = psiClass.getSuperClass();
        while (superClass != null) {
            if (qualifiedTypeName.equals(superClass.getQualifiedName())) {
                return true;
            }
            superClass = superClass.getSuperClass();
        }

        // Проверяем интерфейсы
        for (PsiClass iface : psiClass.getInterfaces()) {
            if (qualifiedTypeName.equals(iface.getQualifiedName())) {
                return true;
            }
            if (isSubclassOf(iface, qualifiedTypeName)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Извлекает строковое значение аннотации
     */
    @Nullable
    public static String getAnnotationStringValue(@NotNull PsiAnnotation annotation, @NotNull String attributeName) {
        return ReadAction.compute(() -> {
            PsiAnnotationMemberValue value = annotation.findAttributeValue(attributeName);
            if (value instanceof PsiLiteralExpression literal) {
                Object v = literal.getValue();
                return v != null ? v.toString() : null;
            }
            // Для array initializer (например, method = {RequestMethod.GET})
            if (value instanceof PsiArrayInitializerMemberValue arrayValue) {
                PsiAnnotationMemberValue[] initializers = arrayValue.getInitializers();
                if (initializers.length > 0 && initializers[0] instanceof PsiReferenceExpression ref) {
                    return ref.getReferenceName();
                }
            }
            return null;
        });
    }

    /**
     * Проверяет, имеет ли элемент указанную аннотацию
     */
    public static boolean hasAnnotation(@NotNull PsiModifierListOwner owner, @NotNull String annotationName) {
        return ReadAction.compute(() -> {
            PsiModifierList modifiers = owner.getModifierList();
            return modifiers != null && modifiers.hasAnnotation(annotationName);
        });
    }

    /**
     * Проверяет наличие любой из указанных аннотаций
     */
    public static boolean hasAnyAnnotation(@NotNull PsiModifierListOwner owner, @NotNull String... annotationNames) {
        for (String annotationName : annotationNames) {
            if (hasAnnotation(owner, annotationName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Обрезает код до указанной длины, сохраняя синтаксис
     */
    @NotNull
    public static String truncateCode(@NotNull String code, int maxLength) {
        if (code.length() <= maxLength) return code;

        // Находим подходящее место для обрезки
        String truncated = code.substring(0, maxLength);
        int lastBrace = truncated.lastIndexOf('}');
        int lastSemicolon = truncated.lastIndexOf(';');
        int lastNewline = truncated.lastIndexOf('\n');

        int cutPoint = Math.max(Math.max(lastBrace, lastSemicolon), lastNewline);
        if (cutPoint > maxLength * 0.7) {
            return truncated.substring(0, cutPoint + 1) + "\n    // ... [truncated]";
        }

        return truncated + "\n    // ... [truncated]";
    }

    /**
     * Определяет, является ли метод контроллером Spring
     */
    public static boolean isSpringControllerMethod(@NotNull PsiMethod method) {
        PsiClass containingClass = method.getContainingClass();
        if (containingClass == null) return false;

        return hasAnnotation(containingClass, "org.springframework.web.bind.annotation.RestController") ||
                hasAnnotation(containingClass, "org.springframework.stereotype.Controller");
    }

    /**
     * Извлекает HTTP метод из аннотации Mapping
     */
    @Nullable
    public static String extractHttpMethod(@NotNull PsiMethod method) {
        for (PsiAnnotation ann : method.getAnnotations()) {
            String name = ann.getQualifiedName();
            if (name == null) continue;

            if (name.endsWith("GetMapping")) return "GET";
            if (name.endsWith("PostMapping")) return "POST";
            if (name.endsWith("PutMapping")) return "PUT";
            if (name.endsWith("DeleteMapping")) return "DELETE";
            if (name.endsWith("PatchMapping")) return "PATCH";
            if (name.endsWith("RequestMapping")) {
                // Проверяем атрибут method
                String methodAttr = getAnnotationStringValue(ann, "method");
                if (methodAttr != null) {
                    return methodAttr.contains(".") ?
                            methodAttr.substring(methodAttr.lastIndexOf('.') + 1) :
                            methodAttr;
                }
                return "GET"; // default
            }
        }
        return null;
    }

    /**
     * Извлекает путь эндпоинта из аннотации
     */
    @Nullable
    public static String extractEndpointPath(@NotNull PsiMethod method) {
        // Сначала проверяем аннотацию метода
        for (PsiAnnotation ann : method.getAnnotations()) {
            String name = ann.getQualifiedName();
            if (name != null && name.contains("Mapping")) {
                String value = getAnnotationStringValue(ann, "value");
                if (value != null) return value;
                String path = getAnnotationStringValue(ann, "path");
                if (path != null) return path;
            }
        }

        // Затем проверяем класс
        PsiClass clazz = method.getContainingClass();
        if (clazz != null) {
            for (PsiAnnotation ann : clazz.getAnnotations()) {
                String name = ann.getQualifiedName();
                if (name != null && name.contains("RequestMapping")) {
                    String value = getAnnotationStringValue(ann, "value");
                    if (value != null) return value;
                }
            }
        }

        return null;
    }

    /**
     * Находит класс по полному имени в проекте
     */
    @Nullable
    public static PsiClass findClassByQualifiedName(@NotNull Project project, @NotNull String qualifiedName) {
        JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
        return facade.findClass(qualifiedName, GlobalSearchScope.allScope(project));
    }

    /**
     * Находит все классы с указанным именем в проекте
     */
    @NotNull
    public static PsiClass[] findClassesByShortName(@NotNull Project project, @NotNull String shortName) {
        PsiShortNamesCache cache = PsiShortNamesCache.getInstance(project);
        return cache.getClassesByName(shortName, GlobalSearchScope.projectScope(project));
    }

    /**
     * Проверяет, является ли тип примитивом или обёрткой примитива
     */
    public static boolean isPrimitiveOrWrapper(@NotNull PsiType type) {
        String typeName = type.getPresentableText();
        return switch (typeName) {
            case "boolean", "Boolean",
                 "byte", "Byte",
                 "char", "Character",
                 "short", "Short",
                 "int", "Integer",
                 "long", "Long",
                 "float", "Float",
                 "double", "Double" -> true;
            default -> false;
        };
    }

    /**
     * Проверяет, является ли тип коллекцией
     */
    public static boolean isCollection(@NotNull PsiType type) {
        return isTypeOf(type, "java.util.Collection") ||
                isTypeOf(type, "java.util.List") ||
                isTypeOf(type, "java.util.Set") ||
                isTypeOf(type, "java.util.ArrayList") ||
                isTypeOf(type, "java.util.HashSet");
    }

    /**
     * Проверяет, является ли тип Map
     */
    public static boolean isMap(@NotNull PsiType type) {
        return isTypeOf(type, "java.util.Map") ||
                isTypeOf(type, "java.util.HashMap") ||
                isTypeOf(type, "java.util.TreeMap") ||
                isTypeOf(type, "java.util.LinkedHashMap");
    }

    /**
     * Получает тип элемента коллекции
     */
    @Nullable
    public static PsiType getCollectionElementType(@NotNull PsiType collectionType) {
        if (collectionType instanceof PsiClassType classType) {
            PsiType[] parameters = classType.getParameters();
            if (parameters.length > 0) {
                return parameters[0];
            }
        }
        return null;
    }

    /**
     * Получает типы ключа и значения Map
     */
    @Nullable
    public static PsiType[] getMapTypes(@NotNull PsiType mapType) {
        if (mapType instanceof PsiClassType classType) {
            PsiType[] parameters = classType.getParameters();
            if (parameters.length >= 2) {
                return parameters; // [0] = key, [1] = value
            }
        }
        return null;
    }

    /**
     * Проверяет, является ли метод геттером
     */
    public static boolean isGetter(@NotNull PsiMethod method) {
        String name = method.getName();
        return (name.startsWith("get") && name.length() > 3 && method.getParameterList().getParametersCount() == 0) ||
                (name.startsWith("is") && name.length() > 2 && method.getParameterList().getParametersCount() == 0 &&
                        PsiTypes.booleanType().equals(method.getReturnType()));
    }

    /**
     * Проверяет, является ли метод сеттером
     */
    public static boolean isSetter(@NotNull PsiMethod method) {
        String name = method.getName();
        return name.startsWith("set") && name.length() > 3 &&
                method.getParameterList().getParametersCount() == 1 &&
                PsiTypes.voidType().equals(method.getReturnType());
    }

    /**
     * Извлекает имя свойства из геттера/сеттера
     */
    @Nullable
    public static String getPropertyNameFromAccessor(@NotNull PsiMethod method) {
        String name = method.getName();
        if (name.startsWith("get") || name.startsWith("set")) {
            String propName = name.substring(3);
            return Character.toLowerCase(propName.charAt(0)) + propName.substring(1);
        }
        if (name.startsWith("is")) {
            String propName = name.substring(2);
            return Character.toLowerCase(propName.charAt(0)) + propName.substring(1);
        }
        return null;
    }

    /**
     * Проверяет, является ли класс DTO (простой класс данных)
     */
    public static boolean isDto(@NotNull PsiClass psiClass) {
        // Проверяем наличие Lombok аннотаций
        if (hasAnyAnnotation(psiClass,
                "lombok.Data", "lombok.Value", "lombok.Builder",
                "com.fasterxml.jackson.annotation.JsonIgnoreProperties")) {
            return true;
        }

        // Проверяем, что все методы — геттеры/сеттеры/конструкторы
        long nonAccessorMethods = Arrays.stream(psiClass.getMethods())
                .filter(m -> !m.isConstructor())
                .filter(m -> !m.hasModifierProperty(PsiModifier.STATIC))
                .filter(m -> !isGetter(m))
                .filter(m -> !isSetter(m))
                .count();

        return nonAccessorMethods == 0;
    }

    /**
     * Сравнивает два типа на эквивалентность
     */
    public static boolean areTypesEquivalent(@Nullable PsiType type1, @Nullable PsiType type2) {
        if (type1 == null && type2 == null) return true;
        if (type1 == null || type2 == null) return false;

        String name1 = type1.getCanonicalText();
        String name2 = type2.getCanonicalText();

        return Objects.equals(name1, name2);
    }

    /**
     * Получает все аннотации элемента как строки
     */
    @NotNull
    public static String[] getAnnotationNames(@NotNull PsiModifierListOwner owner) {
        PsiModifierList modifiers = owner.getModifierList();
        if (modifiers == null) {
            return new String[0];
        }

        return Arrays.stream(modifiers.getAnnotations())
                .map(PsiAnnotation::getQualifiedName)
                .filter(Objects::nonNull)
                .toArray(String[]::new);
    }

    /**
     * Проверяет, является ли метод публичным
     */
    public static boolean isPublic(@NotNull PsiModifierListOwner owner) {
        return owner.hasModifierProperty(PsiModifier.PUBLIC);
    }

    /**
     * Проверяет, является ли метод статическим
     */
    public static boolean isStatic(@NotNull PsiModifierListOwner owner) {
        return owner.hasModifierProperty(PsiModifier.STATIC);
    }

    /**
     * Проверяет, является ли метод абстрактным
     */
    public static boolean isAbstract(@NotNull PsiModifierListOwner owner) {
        return owner.hasModifierProperty(PsiModifier.ABSTRACT);
    }

    /**
     * Получает документ из PSI файла
     */
    @Nullable
    public static com.intellij.openapi.editor.Document getDocument(@NotNull PsiFile file, @NotNull Project project) {
        return PsiDocumentManager.getInstance(project).getDocument(file);
    }

    /**
     * Находит метод в классе по имени и параметрам
     */
    @Nullable
    public static PsiMethod findMethod(@NotNull PsiClass clazz, @NotNull String methodName,
                                       @NotNull String... parameterTypes) {
        for (PsiMethod method : clazz.getMethods()) {
            if (!method.getName().equals(methodName)) {
                continue;
            }

            PsiParameter[] params = method.getParameterList().getParameters();
            if (params.length != parameterTypes.length) {
                continue;
            }

            boolean match = true;
            for (int i = 0; i < params.length; i++) {
                if (!params[i].getType().getPresentableText().equals(parameterTypes[i])) {
                    match = false;
                    break;
                }
            }

            if (match) {
                return method;
            }
        }
        return null;
    }

    /**
     * Получает простое имя типа (без пакета)
     */
    @NotNull
    public static String getSimpleTypeName(@NotNull PsiType type) {
        String presentable = type.getPresentableText();
        int lastDot = presentable.lastIndexOf('.');
        return lastDot >= 0 ? presentable.substring(lastDot + 1) : presentable;
    }

    /**
     * Проверяет, находится ли тип в пакете java.* или javax.*
     */
    public static boolean isJdkType(@NotNull PsiType type) {
        if (type instanceof PsiClassType classType) {
            PsiClass psiClass = classType.resolve();
            if (psiClass != null) {
                String packageName = "";
                PsiFile containingFile = psiClass.getContainingFile();
                if (containingFile instanceof PsiJavaFile) {
                    packageName = ((PsiJavaFile) containingFile).getPackageName();
                }
                return packageName != null && (
                        packageName.startsWith("java.") ||
                                packageName.startsWith("javax.") ||
                                packageName.startsWith("jdk.") ||
                                packageName.startsWith("sun.")
                );
            }
        }
        return false;
    }

    /**
     * Получает полное имя типа (с пакетом)
     */
    @Nullable
    public static String getQualifiedTypeName(@NotNull PsiType type) {
        if (type instanceof PsiClassType classType) {
            PsiClass psiClass = classType.resolve();
            if (psiClass != null) {
                return psiClass.getQualifiedName();
            }
        }
        return type.getCanonicalText();
    }

    /**
     * Проверяет, является ли тип примитивом (исправленная версия)
     */
    public static boolean isPrimitiveType(@Nullable PsiType type) {
        if (type == null) return false;

        // Проверка через PsiPrimitiveType
        if (type instanceof PsiPrimitiveType) {
            return true;
        }

        // Проверка по имени типа для примитивов и void
        String typeName = type.getPresentableText();
        return switch (typeName) {
            case "boolean", "byte", "char", "short", "int", "long", "float", "double", "void" -> true;
            default -> false;
        };
    }
}