package ru.sbrf.uddk.ai.testing.lsprag.analysis;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ImplementationFinder {

    private final Project project;
    private final GlobalSearchScope searchScope;

    public ImplementationFinder(@NotNull Project project) {
        this.project = project;
        this.searchScope = GlobalSearchScope.projectScope(project);
    }

    /**
     * Находит все классы, реализующие данный интерфейс
     */
    @NotNull
    public List<PsiClass> findImplementations(@NotNull PsiClass interfaceClass) {
        return ReadAction.compute(() -> {
            if (!interfaceClass.isInterface()) {
                return Collections.singletonList(interfaceClass);
            }

            List<PsiClass> implementations = new ArrayList<>();

            // 1. Прямые наследники через ClassInheritorsSearch
            Collection<PsiClass> inheritors = ClassInheritorsSearch
                    .search(interfaceClass, searchScope, true)
                    .findAll();

            for (PsiClass impl : inheritors) {
                if (!impl.hasModifierProperty(PsiModifier.ABSTRACT) && !impl.isInterface()) {
                    implementations.add(impl);
                }
            }

            // 2. Рекурсивный поиск для многоуровневой иерархии
            if (implementations.isEmpty()) {
                findImplementationsRecursive(interfaceClass, implementations, new HashSet<>());
            }

            // 3. Поиск через текстовый поиск (fallback)
            if (implementations.isEmpty()) {
                implementations.addAll(findByTextSearch(interfaceClass));
            }

            return implementations;
        });
    }

    /**
     * Рекурсивный поиск реализаций
     */
    private void findImplementationsRecursive(@NotNull PsiClass interfaceClass,
                                              @NotNull List<PsiClass> result,
                                              @NotNull Set<String> visited) {
        String qualifiedName = interfaceClass.getQualifiedName();
        if (qualifiedName == null || visited.contains(qualifiedName)) {
            return;
        }
        visited.add(qualifiedName);

        Collection<PsiClass> inheritors = ClassInheritorsSearch
                .search(interfaceClass, searchScope, true)
                .findAll();

        for (PsiClass impl : inheritors) {
            if (impl.isInterface()) {
                // Рекурсивно для родительских интерфейсов
                findImplementationsRecursive(impl, result, visited);
            } else if (!impl.hasModifierProperty(PsiModifier.ABSTRACT)) {
                result.add(impl);
            }
        }
    }

    /**
     * Поиск через текстовый анализ (когда PSI не находит)
     */
    @NotNull
    private List<PsiClass> findByTextSearch(@NotNull PsiClass interfaceClass) {
        List<PsiClass> implementations = new ArrayList<>();
        String interfaceName = interfaceClass.getName();

        if (interfaceName == null) {
            return implementations;
        }

        // Поиск классов с implements InterfaceName
        for (PsiClass clazz : getAllClassesInProject()) {
            if (clazz.isInterface() || clazz.hasModifierProperty(PsiModifier.ABSTRACT)) {
                continue;
            }

            PsiClass[] interfaces = clazz.getInterfaces();
            for (PsiClass iface : interfaces) {
                if (interfaceName.equals(iface.getName())) {
                    implementations.add(clazz);
                    break;
                }
            }

            // Проверка через extends для абстрактных классов
            PsiClass superClass = clazz.getSuperClass();
            while (superClass != null) {
                if (interfaceName.equals(superClass.getName())) {
                    implementations.add(clazz);
                    break;
                }
                superClass = superClass.getSuperClass();
            }
        }

        return implementations;
    }

    /**
     * Получает все классы в проекте
     */
    @NotNull
    private List<PsiClass> getAllClassesInProject() {
        List<PsiClass> allClasses = new ArrayList<>();

        ProjectRootManager.getInstance(project)
                .getFileIndex()
                .iterateContent(file -> {
                    if (file.getName().endsWith(".java")) {
                        PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
                        if (psiFile instanceof PsiJavaFile javaFile) {
                            Collections.addAll(allClasses, javaFile.getClasses());
                        }
                    }
                    return true;
                });

        return allClasses;
    }

    /**
     * Находит конкретную реализацию для метода интерфейса
     */
    @Nullable
    public PsiMethod findImplementationMethod(@NotNull PsiMethod interfaceMethod,
                                              @NotNull PsiClass implementationClass) {
        return ReadAction.compute(() -> {
            String methodName = interfaceMethod.getName();
            PsiParameter[] parameters = interfaceMethod.getParameterList().getParameters();

            // Поиск метода с той же сигнатурой
            for (PsiMethod method : implementationClass.getMethods()) {
                if (method.getName().equals(methodName) &&
                        isMatchingSignature(method, parameters)) {
                    return method;
                }
            }

            // Поиск через переопределение
            PsiMethod[] methods = implementationClass.findMethodsByName(methodName, true);
            for (PsiMethod method : methods) {
                if (isMatchingSignature(method, parameters)) {
                    return method;
                }
            }

            return null;
        });
    }

    /**
     * Проверяет совпадение сигнатур методов
     */
    private boolean isMatchingSignature(@NotNull PsiMethod method,
                                        @NotNull PsiParameter[] expectedParams) {
        PsiParameter[] actualParams = method.getParameterList().getParameters();

        if (actualParams.length != expectedParams.length) {
            return false;
        }

        for (int i = 0; i < actualParams.length; i++) {
            String expectedType = expectedParams[i].getType().getPresentableText();
            String actualType = actualParams[i].getType().getPresentableText();

            if (!expectedType.equals(actualType)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Определяет, является ли метод абстрактным или из интерфейса
     */
    public boolean isInterfaceMethod(@NotNull PsiMethod method) {
        return ReadAction.compute(() -> {
            PsiClass containingClass = method.getContainingClass();
            if (containingClass == null) {
                return false;
            }

            return containingClass.isInterface() ||
                    method.hasModifierProperty(PsiModifier.ABSTRACT) ||
                    method.getBody() == null;
        });
    }
}
