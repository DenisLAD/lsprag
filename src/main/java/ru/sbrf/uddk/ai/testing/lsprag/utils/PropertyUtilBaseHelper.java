package ru.sbrf.uddk.ai.testing.lsprag.utils;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PropertyUtilBase;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class PropertyUtilBaseHelper {

    /**
     * Получает все геттеры класса (без указания имени свойства)
     */
    @NotNull
    public static List<PsiMethod> getAllGetters(@NotNull PsiClass psiClass) {
        return ContainerUtil.filter(psiClass.getMethods(), PropertyUtilBaseHelper::isGetter);
    }

    /**
     * Получает все сеттеры класса (без указания имени свойства)
     */
    @NotNull
    public static List<PsiMethod> getAllSetters(@NotNull PsiClass psiClass) {
        return ContainerUtil.filter(psiClass.getMethods(), PropertyUtilBaseHelper::isSetter);
    }

    /**
     * Получает геттер для конкретного свойства
     */
    @NotNull
    public static List<PsiMethod> getGettersForProperty(@NotNull PsiClass psiClass,
                                                        @NotNull String propertyName) {
        return PropertyUtilBase.getGetters(psiClass, propertyName);
    }

    /**
     * Получает сеттер для конкретного свойства
     */
    @NotNull
    public static List<PsiMethod> getSettersForProperty(@NotNull PsiClass psiClass,
                                                        @NotNull String propertyName) {
        return PropertyUtilBase.getSetters(psiClass, propertyName);
    }

    /**
     * Получает имя свойства из геттера/сеттера
     */
    @NotNull
    public static String getPropertyName(@NotNull PsiMethod accessorMethod) {
        String name = PropertyUtilBase.getPropertyName(accessorMethod);
        return name != null ? name : "";
    }

    /**
     * Проверяет, является ли метод геттером
     */
    public static boolean isGetter(@NotNull PsiMethod method) {
        return PropertyUtilBase.isSimplePropertyGetter(method);
    }

    /**
     * Проверяет, является ли метод сеттером
     */
    public static boolean isSetter(@NotNull PsiMethod method) {
        return PropertyUtilBase.isSimplePropertySetter(method);
    }

    /**
     * Находит геттер для поля
     */
    @NotNull
    public static java.util.Optional<PsiMethod> findGetterForField(@NotNull PsiClass psiClass,
                                                                   @NotNull String fieldName) {
        return getAllGetters(psiClass).stream()
                .filter(m -> getPropertyName(m).equals(fieldName))
                .findFirst();
    }

    /**
     * Находит сеттер для поля
     */
    @NotNull
    public static java.util.Optional<PsiMethod> findSetterForField(@NotNull PsiClass psiClass,
                                                                   @NotNull String fieldName) {
        return getAllSetters(psiClass).stream()
                .filter(m -> getPropertyName(m).equals(fieldName))
                .findFirst();
    }
}
