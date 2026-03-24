package ru.sbrf.uddk.ai.testing.lsprag.analysis;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class SpringBeanResolver {

    private final Project project;
    private final GlobalSearchScope searchScope;
    private final ImplementationFinder implementationFinder;

    // Spring аннотации для компонентов
    private static final Set<String> SPRING_COMPONENT_ANNOTATIONS = Set.of(
            "org.springframework.stereotype.Service",
            "org.springframework.stereotype.Component",
            "org.springframework.stereotype.Repository",
            "org.springframework.stereotype.Controller",
            "org.springframework.web.bind.annotation.RestController"
    );

    public SpringBeanResolver(@NotNull Project project) {
        this.project = project;
        this.searchScope = GlobalSearchScope.projectScope(project);
        this.implementationFinder = new ImplementationFinder(project);
    }

    /**
     * Находит Spring-реализацию для интерфейса по типу поля
     */
    @NotNull
    public List<PsiClass> findSpringImplementations(@NotNull PsiType type) {
        List<PsiClass> result = new ArrayList<>();

        if (!(type instanceof PsiClassType classType)) {
            return result;
        }

        PsiClass interfaceClass = classType.resolve();
        if (interfaceClass == null) {
            return result;
        }

        // 1. Ищем все реализации интерфейса
        List<PsiClass> implementations = implementationFinder.findImplementations(interfaceClass);

        // 2. Фильтруем только Spring-компоненты
        for (PsiClass impl : implementations) {
            if (isSpringComponent(impl)) {
                result.add(impl);
            }
        }

        // 3. Если не нашли Spring-компоненты, возвращаем все реализации
        if (result.isEmpty()) {
            result.addAll(implementations);
        }

        // 4. Сортируем по приоритету (@Service > @Component > другие)
        result.sort(this::compareSpringPriority);

        return result;
    }

    /**
     * Находит реализацию для поля с @Autowired
     */
    @NotNull
    public List<PsiClass> resolveAutowiredField(@NotNull PsiField field) {
        List<PsiClass> result = new ArrayList<>();

        // Проверяем наличие @Autowired или инъекции через конструктор
        if (!isAutowired(field) && !isConstructorInjected(field)) {
            return result;
        }

        PsiType type = field.getType();
        return findSpringImplementations(type);
    }

    /**
     * Находит реализацию для параметра конструктора
     */
    @NotNull
    public List<PsiClass> resolveConstructorParameter(@NotNull PsiParameter parameter) {
        List<PsiClass> result = new ArrayList<>();

        PsiType type = parameter.getType();
        return findSpringImplementations(type);
    }

    /**
     * Проверяет, является ли класс Spring-компонентом
     */
    public boolean isSpringComponent(@NotNull PsiClass psiClass) {
        return ReadAction.compute(() -> {
            for (PsiAnnotation annotation : psiClass.getAnnotations()) {
                String qualifiedName = annotation.getQualifiedName();
                if (qualifiedName != null && SPRING_COMPONENT_ANNOTATIONS.contains(qualifiedName)) {
                    return true;
                }
            }

            // Проверка через мета-аннотации (например, @SpringBootApplication)
            return hasSpringMetaAnnotation(psiClass);
        });
    }

    /**
     * Проверяет наличие @Autowired
     */
    public boolean isAutowired(@NotNull PsiModifierListOwner owner) {
        return ReadAction.compute(() -> {
            for (PsiAnnotation annotation : owner.getAnnotations()) {
                String qualifiedName = annotation.getQualifiedName();
                if (qualifiedName != null && (
                        qualifiedName.equals("org.springframework.beans.factory.annotation.Autowired") ||
                                qualifiedName.equals("javax.inject.Inject") ||
                                qualifiedName.equals("jakarta.inject.Inject"))) {
                    return true;
                }
            }
            return false;
        });
    }

    /**
     * Проверяет инъекцию через конструктор
     */
    public boolean isConstructorInjected(@NotNull PsiField field) {
        return ReadAction.compute(() -> {
            PsiClass containingClass = field.getContainingClass();
            if (containingClass == null) {
                return false;
            }

            // Ищем конструктор, который использует это поле
            for (PsiMethod constructor : containingClass.getConstructors()) {
                for (PsiParameter param : constructor.getParameterList().getParameters()) {
                    if (param.getName() != null && param.getName().equals(field.getName())) {
                        return true;
                    }
                }
            }

            return false;
        });
    }

    /**
     * Определяет bean name для Spring-компонента
     */
    @Nullable
    public String getBeanName(@NotNull PsiClass psiClass) {
        return ReadAction.compute(() -> {
            for (PsiAnnotation annotation : psiClass.getAnnotations()) {
                String qualifiedName = annotation.getQualifiedName();
                if (qualifiedName != null && SPRING_COMPONENT_ANNOTATIONS.contains(qualifiedName)) {
                    // Пробуем получить значение аннотации (имя бина)
                    PsiAnnotationMemberValue value = annotation.findAttributeValue("value");
                    if (value instanceof PsiLiteralExpression literal) {
                        Object val = literal.getValue();
                        if (val != null && !val.toString().isEmpty()) {
                            return val.toString();
                        }
                    }
                    // По умолчанию имя класса с первой буквой в нижнем регистре
                    return decapitalize(psiClass.getName());
                }
            }
            return null;
        });
    }

    /**
     * Приоритет Spring-аннотаций
     */
    private int compareSpringPriority(@NotNull PsiClass c1, @NotNull PsiClass c2) {
        int p1 = getSpringPriority(c1);
        int p2 = getSpringPriority(c2);
        return Integer.compare(p2, p1); // Больший приоритет первый
    }

    private int getSpringPriority(@NotNull PsiClass psiClass) {
        for (PsiAnnotation annotation : psiClass.getAnnotations()) {
            String qualifiedName = annotation.getQualifiedName();
            if (qualifiedName == null) continue;

            if (qualifiedName.contains("Service")) return 4;
            if (qualifiedName.contains("Repository")) return 3;
            if (qualifiedName.contains("Controller") || qualifiedName.contains("RestController")) return 2;
            if (qualifiedName.contains("Component")) return 1;
        }
        return 0;
    }

    private boolean hasSpringMetaAnnotation(@NotNull PsiClass psiClass) {
        for (PsiAnnotation annotation : psiClass.getAnnotations()) {
            PsiClass annotationClass = annotation.resolveAnnotationType();
            if (annotationClass != null) {
                for (PsiAnnotation metaAnn : annotationClass.getAnnotations()) {
                    String metaName = metaAnn.getQualifiedName();
                    if (metaName != null && SPRING_COMPONENT_ANNOTATIONS.contains(metaName)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private String decapitalize(String name) {
        if (name == null || name.isEmpty()) return name;
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }
}
