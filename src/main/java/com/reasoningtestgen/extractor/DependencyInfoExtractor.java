package com.reasoningtestgen.extractor;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.reasoningtestgen.model.DependencyInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Helper class for extracting Spring, Lombok, and MapStruct information
 */
public class DependencyInfoExtractor {

    /**
     * Find Spring implementations of an interface (REQ 2)
     */
    @NotNull
    public static List<DependencyInfo.ImplementationInfo> findSpringImplementations(
            @NotNull PsiClass interfaceClass,
            @NotNull Project project) {
        
        List<DependencyInfo.ImplementationInfo> implementations = new ArrayList<>();
        String interfaceName = interfaceClass.getName();
        if (interfaceName == null) return implementations;
        
        try {
            PsiShortNamesCache namesCache = PsiShortNamesCache.getInstance(project);
            GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
            
            // Search for all classes
            String[] allClassNames = namesCache.getAllClassNames();
            
            for (String className : allClassNames) {
                // Skip standard java packages
                if (className.startsWith("java.") || className.startsWith("javax.") ||
                    className.startsWith("jdk.") || className.startsWith("com.sun.") ||
                    className.startsWith("sun.")) continue;
                
                PsiClass[] classes = namesCache.getClassesByName(className, scope);
                for (PsiClass psiClass : classes) {
                    if (psiClass.isInterface() || psiClass.isEnum() || psiClass.isAnnotationType()) continue;
                    
                    // Check if implements our interface
                    if (implementsInterface(psiClass, interfaceName)) {
                        // Check for Spring annotations
                        PsiModifierList modifierList = psiClass.getModifierList();
                        if (modifierList != null) {
                            List<String> annotations = new ArrayList<>();
                            boolean isSpringBean = false;
                            
                            String[] springAnnotations = {
                                "org.springframework.stereotype.Service",
                                "org.springframework.stereotype.Component",
                                "org.springframework.stereotype.Repository",
                                "org.springframework.stereotype.Controller",
                                "org.springframework.web.bind.annotation.RestController",
                                "org.springframework.context.annotation.Bean",
                                "org.springframework.boot.autoconfigure.SpringBootApplication"
                            };
                            
                            for (String ann : springAnnotations) {
                                PsiAnnotation annotation = modifierList.findAnnotation(ann);
                                if (annotation != null) {
                                    annotations.add(ann);
                                    isSpringBean = true;
                                }
                            }
                            
                            if (isSpringBean) {
                                // Extract methods
                                List<DependencyInfo.MethodInfo> methods = new ArrayList<>();
                                for (PsiMethod method : psiClass.getMethods()) {
                                    if (!method.isConstructor() && !method.hasModifierProperty(PsiModifier.STATIC)) {
                                        List<String> params = Arrays.stream(method.getParameterList().getParameters())
                                            .map(p -> p.getType().getCanonicalText() + " " + p.getName())
                                            .collect(Collectors.toList());
                                        
                                        methods.add(new DependencyInfo.MethodInfo(
                                            method.getName(),
                                            method.getReturnType() != null ? method.getReturnType().getCanonicalText() : "void",
                                            params,
                                            List.of(),
                                            ""
                                        ));
                                    }
                                }
                                
                                boolean hasSource = hasSourceCode(psiClass);
                                String snippet = hasSource ? extractSnippet(psiClass) : "";
                                
                                implementations.add(new DependencyInfo.ImplementationInfo(
                                    psiClass.getQualifiedName(),
                                    psiClass.getQualifiedName() != null && psiClass.getQualifiedName().contains(".") ?
                                        psiClass.getQualifiedName().substring(0, psiClass.getQualifiedName().lastIndexOf(".")) : "",
                                    annotations,
                                    methods,
                                    hasSource,
                                    snippet
                                ));
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to find Spring implementations for " + interfaceName + ": " + e.getMessage());
        }
        
        return implementations;
    }

    /**
     * Check if class implements an interface
     */
    private static boolean implementsInterface(@NotNull PsiClass psiClass, @NotNull String interfaceName) {
        PsiClassType[] implementsList = psiClass.getImplementsListTypes();
        for (PsiClassType implType : implementsList) {
            String implName = implType.getCanonicalText();
            if (implName.equals(interfaceName)) return true;
        }
        
        // Check parent interfaces
        for (PsiClassType extendsType : psiClass.getExtendsListTypes()) {
            PsiClass extended = extendsType.resolve();
            if (extended != null && implementsInterface(extended, interfaceName)) return true;
        }
        
        return false;
    }

    /**
     * Extract Lombok generated methods
     */
    @NotNull
    public static List<DependencyInfo.LombokMethodInfo> extractLombokGeneratedMethods(@NotNull PsiClass psiClass) {
        List<DependencyInfo.LombokMethodInfo> lombokMethods = new ArrayList<>();
        
        try {
            PsiModifierList modifierList = psiClass.getModifierList();
            if (modifierList == null) return lombokMethods;
            
            // Detect Lombok annotations
            String[] lombokAnnotations = {
                "lombok.Data",
                "lombok.Getter",
                "lombok.Setter",
                "lombok.Builder",
                "lombok.AllArgsConstructor",
                "lombok.NoArgsConstructor",
                "lombok.Value",
                "lombok.ToString",
                "lombok.EqualsAndHashCode"
            };
            
            List<String> detectedLombok = new ArrayList<>();
            for (String lombokAnn : lombokAnnotations) {
                if (modifierList.findAnnotation(lombokAnn) != null) {
                    detectedLombok.add(lombokAnn.substring("lombok.".length()));
                }
            }
            
            if (detectedLombok.isEmpty()) return lombokMethods;
            
            // Generate method signatures based on Lombok annotations
            for (PsiField field : psiClass.getFields()) {
                if (field.hasModifierProperty(PsiModifier.STATIC) || field.hasModifierProperty(PsiModifier.FINAL)) continue;
                
                String fieldName = field.getName();
                String fieldType = field.getType().getCanonicalText();
                String capitalized = capitalize(fieldName);
                
                // @Data, @Getter generates getters
                if (detectedLombok.contains("Data") || detectedLombok.contains("Getter") || 
                    detectedLombok.contains("Value")) {
                    lombokMethods.add(new DependencyInfo.LombokMethodInfo(
                        "get" + capitalized + "()",
                        "@Getter/@Data",
                        fieldType,
                        List.of()
                    ));
                }
                
                // @Data, @Setter generates setters (non-final fields)
                if (!field.hasModifierProperty(PsiModifier.FINAL) &&
                    (detectedLombok.contains("Data") || detectedLombok.contains("Setter"))) {
                    lombokMethods.add(new DependencyInfo.LombokMethodInfo(
                        "set" + capitalized + "(" + fieldType + " " + fieldName + ")",
                        "@Setter/@Data",
                        "void",
                        List.of(fieldType + " " + fieldName)
                    ));
                }
            }
            
            // @Builder generates builder() method
            if (detectedLombok.contains("Builder")) {
                lombokMethods.add(new DependencyInfo.LombokMethodInfo(
                    "builder()",
                    "@Builder",
                    psiClass.getName() + "Builder",
                    List.of()
                ));
            }
            
        } catch (Exception e) {
            System.err.println("Failed to extract Lombok methods for " + psiClass.getName() + ": " + e.getMessage());
        }
        
        return lombokMethods;
    }

    /**
     * Extract MapStruct mapper information
     */
    @Nullable
    public static DependencyInfo.MapStructInfo extractMapStructInfo(
            @NotNull PsiClass psiClass,
            @NotNull Project project) {
        
        try {
            PsiModifierList modifierList = psiClass.getModifierList();
            if (modifierList == null) return null;
            
            // Check for @Mapper annotation
            PsiAnnotation mapperAnnotation = modifierList.findAnnotation("org.mapstruct.Mapper");
            if (mapperAnnotation == null) return null;
            
            // Extract componentModel
            String componentModel = "spring"; // Default
            PsiAnnotationMemberValue componentModelAttr = mapperAnnotation.findDeclaredAttributeValue("componentModel");
            if (componentModelAttr instanceof PsiLiteralExpression) {
                componentModel = ((PsiLiteralExpression) componentModelAttr).getValue().toString();
            }
            
            // Find mapping methods
            List<DependencyInfo.MapStructInfo.MappingInfo> mappings = new ArrayList<>();
            String sourceType = "";
            String targetType = "";
            
            for (PsiMethod method : psiClass.getMethods()) {
                PsiAnnotation mappingAnnotation = method.getModifierList().findAnnotation("org.mapstruct.Mapping");
                if (mappingAnnotation == null) continue;
                
                PsiAnnotationMemberValue sourceAttr = mappingAnnotation.findDeclaredAttributeValue("source");
                PsiAnnotationMemberValue targetAttr = mappingAnnotation.findDeclaredAttributeValue("target");
                PsiAnnotationMemberValue qualifiedByNameAttr = mappingAnnotation.findDeclaredAttributeValue("qualifiedByName");
                
                if (sourceAttr instanceof PsiLiteralExpression && targetAttr instanceof PsiLiteralExpression) {
                    String source = ((PsiLiteralExpression) sourceAttr).getValue().toString();
                    String target = ((PsiLiteralExpression) targetAttr).getValue().toString();
                    String qualifiedByName = qualifiedByNameAttr instanceof PsiLiteralExpression ? 
                        ((PsiLiteralExpression) qualifiedByNameAttr).getValue().toString() : "";
                    
                    mappings.add(new DependencyInfo.MapStructInfo.MappingInfo(source, target, qualifiedByName));
                    
                    // Extract source/target types from method signature
                    if (method.getParameterList().getParametersCount() > 0) {
                        sourceType = method.getParameterList().getParameters()[0].getType().getCanonicalText();
                    }
                    if (method.getReturnType() != null) {
                        targetType = method.getReturnType().getCanonicalText();
                    }
                }
            }
            
            return new DependencyInfo.MapStructInfo(
                psiClass.getQualifiedName(),
                sourceType,
                targetType,
                mappings,
                true
            );
            
        } catch (Exception e) {
            System.err.println("Failed to extract MapStruct info for " + psiClass.getName() + ": " + e.getMessage());
            return null;
        }
    }

    private static String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    private static boolean hasSourceCode(@NotNull PsiClass psiClass) {
        PsiFile containingFile = psiClass.getContainingFile();
        return containingFile != null && !containingFile.getName().endsWith(".class");
    }

    @NotNull
    private static String extractSnippet(@NotNull PsiClass psiClass) {
        String code = psiClass.getText();
        String[] lines = code.split("\n");
        int maxLines = Math.min(lines.length, 15);
        return String.join("\n", Arrays.copyOfRange(lines, 0, maxLines)) + 
               (lines.length > 15 ? "\n..." : "");
    }
}
