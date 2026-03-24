package ru.sbrf.uddk.ai.testing.lsprag.io;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.codeStyle.CodeStyleManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.sbrf.uddk.ai.testing.lsprag.context.MethodContext;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TestFileWriter {

    private final Project project;
    private static final Pattern PACKAGE_PATTERN = Pattern.compile("^package\\s+([\\w.]+);", Pattern.MULTILINE);

    public TestFileWriter(@NotNull Project project) {
        this.project = project;
    }

    @NotNull
    public String writeTestFile(@NotNull String code, @NotNull MethodContext context)
            throws IOException {

        System.out.println(code);
        // 1. Определяем пакет и имя класса из кода
        String packageName = extractPackageName(code);
        String className = extractClassName(code);

        if (className == null) {
            throw new IOException("Не удалось определить имя тестового класса в сгенерированном коде");
        }

        // 2. Определяем целевую директорию для тестов
        VirtualFile testDir = findOrCreateTestDirectory(packageName);

        // 3. Создаём или обновляем файл
        VirtualFile testFile = WriteAction.computeAndWait(() -> {
            try {
                VirtualFile existing = testDir.findFileByRelativePath(className + ".java");
                if (existing != null && existing.isValid()) {
                    // Обновляем существующий файл
                    PsiFile psiFile = PsiManager.getInstance(project).findFile(existing);
                    if (psiFile != null) {
                        Document doc = PsiDocumentManager.getInstance(project).getDocument(psiFile);
                        if (doc != null) {
                            doc.setText(code);
                            PsiDocumentManager.getInstance(project).commitDocument(doc);
                            return existing;
                        }
                    }
                }

                // Создаём новый файл
                return testDir.createChildData(this, className + ".java");

            } catch (IOException e) {
                throw new RuntimeException("Ошибка при создании файла: " + className, e);
            }
        });

        // 4. Записываем содержимое
        WriteAction.runAndWait(() -> {
            try {
                testFile.setBinaryContent(code.getBytes("UTF-8"));

                // Форматируем код через CodeStyleManager
                PsiFile psiFile = PsiManager.getInstance(project).findFile(testFile);
                if (psiFile instanceof PsiJavaFile) {
                    WriteCommandAction.runWriteCommandAction(project, () -> {
                        CodeStyleManager.getInstance(project).reformat(psiFile);
                    });
                }
            } catch (IOException e) {
                throw new RuntimeException("Ошибка записи в файл", e);
            }
        });

        // 5. Проверяем и добавляем зависимости (опционально)
        ensureTestDependencies();

        return testFile.getPath();
    }

    @Nullable
    private String extractPackageName(@NotNull String code) {
        Matcher matcher = PACKAGE_PATTERN.matcher(code);
        return matcher.find() ? matcher.group(1) : null;
    }

    @Nullable
    private String extractClassName(@NotNull String code) {
        // Ищем public class ClassName { или класс с аннотациями
        Pattern classPattern = Pattern.compile(
                "(?:public\\s+)?(?:abstract\\s+)?(?:final\\s+)?class\\s+(\\w+)",
                Pattern.MULTILINE
        );
        Matcher matcher = classPattern.matcher(code);
        return matcher.find() ? matcher.group(1) : null;
    }

    @NotNull
    private VirtualFile findOrCreateTestDirectory(@Nullable String packageName)
            throws IOException {

        com.intellij.openapi.module.Module module = ModuleUtilCore.findModuleForFile(project.getProjectFile(), project);
        if (module == null) {
            throw new IOException("Не удалось определить модуль проекта");
        }

        // Ищем source root для тестов
        VirtualFile testRoot = null;
        for (VirtualFile root : ModuleRootManager.getInstance(module).getSourceRoots()) {
            if (root.getPath().contains("test") || root.getName().equals("test")) {
                testRoot = root;
                break;
            }
        }

        // Если не нашли, создаём стандартную структуру
        if (testRoot == null || !testRoot.isValid()) {
            VirtualFile projectRoot = project.getBaseDir();
            testRoot = createTestSourceRoot(projectRoot);
        }

        // Создаём пакетную структуру
        VirtualFile targetDir = testRoot;
        if (packageName != null && !packageName.isEmpty()) {
            for (String part : packageName.split("\\.")) {
                VirtualFile subDir = targetDir.findChild(part);
                if (subDir == null) {
                    VirtualFile fTargetDir = targetDir;
                    targetDir = WriteAction.computeAndWait(() -> {
                        try {
                            return fTargetDir.createChildDirectory(this, part);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
                } else {
                    targetDir = subDir;
                }
            }
        }

        return targetDir;
    }

    @NotNull
    private VirtualFile createTestSourceRoot(@NotNull VirtualFile projectRoot)
            throws IOException {

        // Создаём src/test/java
        VirtualFile src = projectRoot.findChild("src");
        if (src == null) {
            src = WriteAction.computeAndWait(() -> {
                try {
                    return projectRoot.createChildDirectory(this, "src");
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }

        VirtualFile test = src.findChild("test");
        if (test == null) {
            VirtualFile fSrc = src;
            test = WriteAction.computeAndWait(() -> {
                try {
                    return fSrc.createChildDirectory(this, "test");
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }

        VirtualFile java = test.findChild("java");
        final VirtualFile tt = test;
        if (java == null) {
            java = WriteAction.computeAndWait(() -> {
                try {
                    return tt.createChildDirectory(this, "java");
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }

        // TODO: Добавить src/test/java как test source root в модуль
        // Это требует более сложной работы с ModuleRootModificationUtil

        return java;
    }

    private void ensureTestDependencies() {
        // Проверяем наличие зависимостей в pom.xml / build.gradle
        // Это упрощённая реализация — в продакшене нужно парсить билд-файлы

        VirtualFile pom = project.getBaseDir().findFileByRelativePath("pom.xml");
        if (pom != null && pom.isValid()) {
            checkAndAddMavenDependencies(pom);
        }

        VirtualFile gradle = project.getBaseDir().findFileByRelativePath("build.gradle");
        if (gradle == null) {
            gradle = project.getBaseDir().findFileByRelativePath("build.gradle.kts");
        }
        if (gradle != null && gradle.isValid()) {
            checkAndAddGradleDependencies(gradle);
        }
    }

    private void checkAndAddMavenDependencies(@NotNull VirtualFile pomFile) {
        // Упрощённая проверка: если в pom.xml нет rest-assured, можно предложить добавить
        // В полной версии нужно парсить XML и модифицировать <dependencies>

        try {
            String content = new String(pomFile.contentsToByteArray(), "UTF-8");

            boolean hasRestAssured = content.contains("rest-assured");
            boolean hasJUnit5 = content.contains("junit-jupiter") || content.contains("junit-jupiter-api");

            if (!hasRestAssured || !hasJUnit5) {
                // Логируем предупреждение — пользователь должен добавить зависимости вручную
                // Или показать Notification с инструкцией
                // В полной версии: автоматически добавить в pom.xml через XML manipulation
            }
        } catch (IOException e) {
            // Игнорируем ошибки чтения
        }
    }

    private void checkAndAddGradleDependencies(@NotNull VirtualFile gradleFile) {
        // Аналогично для Gradle
        try {
            String content = new String(gradleFile.contentsToByteArray(), "UTF-8");

            boolean hasRestAssured = content.contains("rest-assured");
            boolean hasJUnit5 = content.contains("junit-jupiter");

            if (!hasRestAssured || !hasJUnit5) {
                // Показать уведомление пользователю
            }
        } catch (IOException e) {
            // Игнорируем
        }
    }
}