package ru.sbrf.uddk.ai.testing.lsprag.analysis;

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class ImplementationContext {

    private final PsiMethod interfaceMethod;
    private final List<ImplementationInfo> implementations;
    private final PsiClass primaryImplementation;

    public ImplementationContext(@NotNull PsiMethod interfaceMethod,
                                 @NotNull List<ImplementationInfo> implementations,
                                 @Nullable PsiClass primaryImplementation) {
        this.interfaceMethod = interfaceMethod;
        this.implementations = implementations;
        this.primaryImplementation = primaryImplementation;
    }

    /**
     * Информация о конкретной реализации
     */
    public static class ImplementationInfo {
        private final PsiClass implementingClass;
        private final PsiMethod implementingMethod;
        private final boolean isSpringComponent;
        private final String beanName;
        private final int priority;
        private final String bodySnippet;

        public ImplementationInfo(@NotNull PsiClass implementingClass,
                                  @NotNull PsiMethod implementingMethod,
                                  boolean isSpringComponent,
                                  @Nullable String beanName,
                                  int priority) {
            this.implementingClass = implementingClass;
            this.implementingMethod = implementingMethod;
            this.isSpringComponent = isSpringComponent;
            this.beanName = beanName;
            this.priority = priority;
            this.bodySnippet = implementingMethod.getBody() != null
                    ? truncateCode(implementingMethod.getBody().getText(), 12500)
                    : "";
        }

        @NotNull
        public PsiClass getImplementingClass() {
            return implementingClass;
        }

        @NotNull
        public PsiMethod getImplementingMethod() {
            return implementingMethod;
        }

        public boolean isSpringComponent() {
            return isSpringComponent;
        }

        @Nullable
        public String getBeanName() {
            return beanName;
        }

        public int getPriority() {
            return priority;
        }

        @NotNull
        public String getBodySnippet() {
            return bodySnippet;
        }

        @Override
        public String toString() {
            return String.format("%s.%s() [priority=%d, spring=%s]",
                    implementingClass.getName(),
                    implementingMethod.getName(),
                    priority,
                    isSpringComponent);
        }
    }

    @NotNull
    public PsiMethod getInterfaceMethod() {
        return interfaceMethod;
    }

    @NotNull
    public List<ImplementationInfo> getImplementations() {
        return implementations;
    }

    @Nullable
    public PsiClass getPrimaryImplementation() {
        return primaryImplementation;
    }

    /**
     * Возвращает основную реализацию или первую в списке
     */
    @NotNull
    public ImplementationInfo getPrimaryImplementationInfo() {
        if (primaryImplementation != null) {
            return implementations.stream()
                    .filter(info -> info.getImplementingClass().equals(primaryImplementation))
                    .findFirst()
                    .orElse(implementations.get(0));
        }
        return implementations.isEmpty() ? createEmptyInfo() : implementations.get(0);
    }

    @NotNull
    private ImplementationInfo createEmptyInfo() {
        return new ImplementationInfo(
                interfaceMethod.getContainingClass(),
                interfaceMethod,
                false,
                null,
                0
        );
    }

    @NotNull
    private static String truncateCode(@NotNull String code, int maxLength) {
        if (code.length() <= maxLength) return code;
        return code.substring(0, maxLength) + "\n    // ... [truncated]";
    }

    /**
     * Форматирует контекст для промпта LLM
     */
    @NotNull
    public String formatForPrompt() {
        StringBuilder sb = new StringBuilder();
        AtomicInteger ai = new AtomicInteger(0);
        ReadAction.run(() -> {
            sb.append("### INTERFACE METHOD\n");
            sb.append(interfaceMethod.getContainingClass().getName())
                    .append(".")
                    .append(interfaceMethod.getName())
                    .append("()\n\n");

            if (implementations.isEmpty()) {
                sb.append("// No implementations found in project\n");
            } else {
                sb.append("### IMPLEMENTATIONS (").append(implementations.size()).append(")\n");

                for (int i = 0; i < implementations.size(); i++) {
                    ImplementationInfo info = implementations.get(i);
                    if (StringUtils.isBlank(info.getBodySnippet())) {
                        continue;
                    }
                    ai.incrementAndGet();
                    String marker = (info == getPrimaryImplementationInfo()) ? " [PRIMARY]" : "";

                    sb.append("\n--- Implementation ").append(i + 1).append(marker).append(" ---\n");
                    sb.append("Class: ").append(info.getImplementingClass().getName()).append("\n");
                    sb.append("Spring Component: ").append(info.isSpringComponent()).append("\n");
                    if (info.getBeanName() != null) {
                        sb.append("Bean Name: @").append(info.getBeanName()).append("\n");
                    }
                    sb.append("Priority: ").append(info.getPriority()).append("\n\n");
                    sb.append("```java\n");
                    sb.append(info.getBodySnippet()).append("\n");
                    sb.append("```\n");
                }
            }
        });
        return ai.get() == 0 ? "" : sb.toString();
    }
}