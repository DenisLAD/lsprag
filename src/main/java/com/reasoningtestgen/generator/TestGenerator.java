package com.reasoningtestgen.generator;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.file.PsiJavaDirectoryImpl;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.openapi.fileTypes.StdFileTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * Generates test files from GeneratedCode model
 * According to ANALYTICS.md Section 5.4
 */
public class TestGenerator {

    private final Project project;

    public TestGenerator(@NotNull Project project) {
        this.project = project;
    }

    /**
     * Create a PsiFile from generated code
     * Must be called within WriteCommandAction
     */
    @NotNull
    public PsiFile createTestFile(@NotNull String className, 
                                   @NotNull String code) {
        return WriteCommandAction.runWriteCommandAction(project, (com.intellij.openapi.util.Computable<PsiFile>) () -> {
            PsiFileFactory fileFactory = PsiFileFactory.getInstance(project);
            return fileFactory.createFileFromText(
                className + ".java",
                StdFileTypes.JAVA,
                code
            );
        });
    }

    /**
     * Find or create test directory for the given class
     * Must be called within ReadAction
     */
    @Nullable
    public PsiDirectory findOrCreateTestDirectory(@NotNull String packageName) {
        return ReadAction.compute(() -> {
            // Get project base path
            String basePath = project.getBasePath();
            if (basePath == null) {
                return null;
            }

            // Convert package to directory path
            String dirPath = packageName.replace('.', File.separatorChar);
            
            // Try to find in test source roots
            PsiManager psiManager = PsiManager.getInstance(project);
            
            // Common test source root patterns
            String[] testRoots = {
                basePath + "/src/test/java/" + dirPath,
                basePath + "/test/java/" + dirPath,
                basePath + "/test/" + dirPath
            };

            for (String path : testRoots) {
                VirtualFile vFile = LocalFileSystem.getInstance().findFileByPath(path);
                if (vFile != null && vFile.isDirectory()) {
                    return psiManager.findDirectory(vFile);
                }
            }

            // Create directory if not exists
            String testDirPath = basePath + "/src/test/java/" + dirPath;
            File dir = new File(testDirPath);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            VirtualFile vDir = LocalFileSystem.getInstance().refreshAndFindFileByPath(testDirPath.replace(File.separatorChar, '/'));
            if (vDir != null) {
                return psiManager.findDirectory(vDir);
            }

            return null;
        });
    }

    /**
     * Write test file to disk
     * Must be called within WriteCommandAction
     */
    public void writeTestFile(@NotNull PsiFile testFile, 
                               @NotNull PsiDirectory targetDir) throws WriteException {
        try {
            WriteCommandAction.runWriteCommandAction(project, () -> {
                targetDir.add(testFile);
            });
        } catch (Exception e) {
            throw new WriteException("Failed to write test file", e);
        }
    }

    /**
     * Get package name for test class
     */
    @NotNull
    public String getTestPackageName(@NotNull String sourcePackageName) {
        return sourcePackageName; // Tests typically in same package structure under src/test/java
    }

    /**
     * Exception during test file writing
     */
    public static class WriteException extends Exception {
        public WriteException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
