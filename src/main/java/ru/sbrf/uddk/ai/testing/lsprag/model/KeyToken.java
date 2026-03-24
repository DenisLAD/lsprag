package ru.sbrf.uddk.ai.testing.lsprag.model;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class KeyToken {
    private final PsiElement element;
    private final String name;
    private final TokenRole role;
    private final PsiElement parentCondition;
    private final int offset;

    public KeyToken(@NotNull PsiElement element, @NotNull String name,
                    @NotNull TokenRole role, @Nullable PsiElement parentCondition) {
        this.element = element;
        this.name = name;
        this.role = role;
        this.parentCondition = parentCondition;
        this.offset = element.getTextOffset();
    }

    @NotNull public PsiElement getElement() { return element; }
    @NotNull public String getName() { return name; }
    @NotNull public TokenRole getRole() { return role; }
    @Nullable public PsiElement getParentCondition() { return parentCondition; }
    public int getOffset() { return offset; }

    @Override
    public String toString() {
        return String.format("KeyToken{name='%s', role=%s, offset=%d}", name, role, offset);
    }
}