package com.reasoningtestgen.generator;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.testFramework.LightVirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Arrays;
import java.util.List;

/**
 * V2: Writes test files with proper integration to IDEA project structure
 * Uses ModuleRootManager to find test source roots
 * Automatically creates directories if they don't exist
 */
public class TestFileWriter {

    private static final Logger LOG = LoggerFactory.getLogger(TestFileWriter.class);

    private final Project project;

    public TestFileWriter(@NotNull Project project) {
        this.project = project;
    }

    /**
     * Find the appropriate test source root for the given class
     * Uses multiple strategies to find or create test roots
     */
    @Nullable
    public VirtualFile findTestSourceRoot(@NotNull String qualifiedClassName) {
        return ReadAction.compute(() -> {
            // Extract package from qualified class name
            String packageName = "";
            int lastDot = qualifiedClassName.lastIndexOf('.');
            if (lastDot > 0) {
                packageName = qualifiedClassName.substring(0, lastDot);
            }

            // Strategy 1: Find module and use ProjectFileIndex to detect test roots
            ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
            Module[] modules = ProjectUtil.getModules(project);
            
            if (modules.length > 0) {
                Module module = modules[0];
                ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
                
                // Get all source roots and find test roots properly
                VirtualFile[] sourceRoots = rootManager.getSourceRoots();
                VirtualFile testRoot = null;
                
                // Find test root by checking if it's marked as test content
                for (VirtualFile root : sourceRoots) {
                    if (fileIndex.isInTestSourceContent(root)) {
                        testRoot = root;
                        LOG.info("Found test source root via file index: {}", root.getPath());
                        break;
                    }
                    // Also check by path patterns
                    String rootPath = root.getPath().replace('\\', '/');
                    if (rootPath.endsWith("/src/test/java") || 
                        rootPath.endsWith("/test/java") ||
                        rootPath.endsWith("/src/test")) {
                        testRoot = root;
                        LOG.info("Found test source root by path pattern: {}", root.getPath());
                        break;
                    }
                }
                
                if (testRoot != null) {
                    // Navigate to package directory
                    return navigateToPackage(testRoot, packageName);
                }
            }
            
            // Strategy 2: Try to find src/test/java explicitly
            VirtualFile baseDir = project.getBaseDir();
            if (baseDir != null) {
                VirtualFile srcDir = baseDir.findChild("src");
                if (srcDir != null && srcDir.isDirectory()) {
                    VirtualFile testDir = srcDir.findChild("test");
                    if (testDir != null && testDir.isDirectory()) {
                        VirtualFile javaDir = testDir.findChild("java");
                        if (javaDir != null && javaDir.isDirectory()) {
                            LOG.info("Found test source root via explicit path: {}", javaDir.getPath());
                            return navigateToPackage(javaDir, packageName);
                        }
                    }
                }
            }
            
            // Strategy 3: Fallback to project base dir (not ideal, but better than null)
            LOG.warn("No test source root found, falling back to project base dir");
            return baseDir;
        });
    }

    /**
     * Navigate to (or create) the package directory
     */
    @NotNull
    private VirtualFile navigateToPackage(@NotNull VirtualFile root, @NotNull String packageName) {
        VirtualFile targetDir = root;
        
        if (!packageName.isEmpty()) {
            String[] pathParts = packageName.split("\\.");
            for (String part : pathParts) {
                VirtualFile child = targetDir.findChild(part);
                if (child == null || !child.isDirectory()) {
                    // Directory doesn't exist yet - return root, will create later
                    LOG.info("Package directory doesn't exist yet: {}", part);
                    break;
                }
                targetDir = child;
            }
        }
        
        return targetDir;
    }

    /**
     * Create the full path for a test file, creating directories as needed
     */
    @Nullable
    public VirtualFile createTestFilePath(@NotNull String qualifiedClassName,
                                           @NotNull String testFileName) {
        return WriteCommandAction.runWriteCommandAction(project, (com.intellij.openapi.util.Computable<VirtualFile>) () -> {
            VirtualFile testRoot = findTestSourceRoot(qualifiedClassName);
            if (testRoot == null) {
                LOG.error("Cannot find test source root");
                return null;
            }

            // Extract package path
            String packageName = "";
            int lastDot = qualifiedClassName.lastIndexOf('.');
            if (lastDot > 0) {
                packageName = qualifiedClassName.substring(0, lastDot);
            }

            // Navigate/create package directories
            VirtualFile targetDir = testRoot;
            if (!packageName.isEmpty()) {
                String[] pathParts = packageName.split("\\.");
                for (String part : pathParts) {
                    VirtualFile child = targetDir.findChild(part);
                    if (child == null || !child.isDirectory()) {
                        try {
                            child = targetDir.createChildDirectory(this, part);
                            LOG.info("Created directory: {}", child.getPath());
                        } catch (Exception e) {
                            LOG.error("Failed to create directory: {}", part, e);
                            return null;
                        }
                    }
                    targetDir = child;
                }
            }

            // Check if file already exists
            VirtualFile existingFile = targetDir.findChild(testFileName);
            if (existingFile != null) {
                LOG.info("Test file already exists: {}", existingFile.getPath());
                return existingFile;
            }

            // Create new file
            try {
                VirtualFile newFile = targetDir.createChildData(this, testFileName);
                LOG.info("Created test file: {}", newFile.getPath());
                return newFile;
            } catch (Exception e) {
                LOG.error("Failed to create test file: {}", testFileName, e);
                return null;
            }
        });
    }

    /**
     * Write test code to the appropriate location
     * Handles directory creation and file writing
     */
    @NotNull
    public WriteResult writeTestFile(@NotNull String qualifiedClassName,
                                      @NotNull String testCode) {
        String testFileName = extractTestClassName(qualifiedClassName) + ".java";
        
        VirtualFile testFile = createTestFilePath(qualifiedClassName, testFileName);
        if (testFile == null) {
            return new WriteResult(false, "Failed to create test file path", null);
        }

        try {
            // Write content to file
            WriteCommandAction.runWriteCommandAction(project, () -> {
                try {
                    testFile.setBinaryContent(testCode.getBytes());
                    testFile.refresh(false, false);
                    LOG.info("Successfully wrote test file: {}", testFile.getPath());
                } catch (Exception e) {
                    LOG.error("Failed to write test file content", e);
                }
            });

            // Refresh VFS
            testFile.refresh(false, false);
            VirtualFile refreshedFile = testFile;
            if (!refreshedFile.isValid()) {
                refreshedFile = VirtualFileManager.getInstance().findFileByUrl(testFile.getUrl());
            }

            return new WriteResult(true, "Test file created successfully", refreshedFile);

        } catch (Exception e) {
            LOG.error("Failed to write test file", e);
            return new WriteResult(false, "Failed to write test file: " + e.getMessage(), null);
        }
    }

    /**
     * Extract test class name from qualified class name
     * e.g., "com.example.OrderService" -> "OrderServiceTest"
     */
    @NotNull
    private String extractTestClassName(@NotNull String qualifiedClassName) {
        String className = qualifiedClassName;
        int lastDot = qualifiedClassName.lastIndexOf('.');
        if (lastDot > 0) {
            className = qualifiedClassName.substring(lastDot + 1);
        }
        
        if (!className.endsWith("Test")) {
            className += "Test";
        }
        
        return className;
    }

    /**
     * Get the test package for a given source package
     * Usually the same package but in test source root
     */
    @NotNull
    public String getTestPackage(@NotNull String sourcePackage) {
        // In most projects, test packages match source packages
        // Just in different source root
        return sourcePackage;
    }

    /**
     * Result of a write operation
     */
    public record WriteResult(
        boolean success,
        @NotNull String message,
        @Nullable VirtualFile file
    ) {
    }
}
