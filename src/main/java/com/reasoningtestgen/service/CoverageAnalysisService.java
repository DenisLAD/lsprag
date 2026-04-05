package com.reasoningtestgen.service;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.reasoningtestgen.model.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Background service for analyzing test coverage of branches
 * Maps existing tests to CFG nodes to find uncovered branches
 * Runs analysis in background without blocking UI
 */
@Service(Service.Level.PROJECT)
public final class CoverageAnalysisService {

    private static final Logger LOG = LoggerFactory.getLogger(CoverageAnalysisService.class);

    private final Project project;
    private volatile CoverageInfo lastAnalysisResult;
    private volatile boolean analysisInProgress = false;

    public CoverageAnalysisService(@NotNull Project project) {
        this.project = project;
    }

    public static CoverageAnalysisService getInstance(@NotNull Project project) {
        return project.getService(CoverageAnalysisService.class);
    }

    /**
     * Analyze coverage for a method in background
     * @param context Method context with CFG and existing tests
     * @param method PSI method being analyzed
     * @param callback Called when analysis completes
     */
    public void analyzeInBackground(@NotNull MethodContext context,
                                      @NotNull PsiMethod method,
                                      @NotNull CoverageCallback callback) {
        if (analysisInProgress) {
            LOG.info("Coverage analysis already in progress, skipping");
            callback.onComplete(lastAnalysisResult);
            return;
        }

        analysisInProgress = true;

        // Run analysis in background thread
        new Thread(() -> {
            try {
                CoverageInfo result = analyzeCoverage(context, method);
                lastAnalysisResult = result;
                callback.onComplete(result);
            } catch (Exception e) {
                LOG.error("Coverage analysis failed", e);
                callback.onError(e);
            } finally {
                analysisInProgress = false;
            }
        }, "CoverageAnalysis-" + method.getName()).start();
    }

    /**
     * Analyze coverage synchronously
     */
    @NotNull
    public CoverageInfo analyzeCoverage(@NotNull MethodContext context,
                                         @NotNull PsiMethod method) {
        return ReadAction.compute(() -> {
            LOG.info("Starting coverage analysis for {}.{}",
                method.getContainingClass() != null ? method.getContainingClass().getName() : "Unknown",
                method.getName());

            List<CFGNode> cfgNodes = context.controlFlow().nodes();
            List<ExistingTestInfo> existingTests = context.existingTests();

            // Find branches (IF, SWITCH, CATCH nodes)
            List<CFGNode> branchNodes = cfgNodes.stream()
                .filter(node -> node.type() == CFGNode.NodeType.IF ||
                               node.type() == CFGNode.NodeType.SWITCH ||
                               node.type() == CFGNode.NodeType.CATCH)
                .collect(Collectors.toList());

            // Map each branch to covering tests
            Map<CFGNode, List<String>> nodeToTests = new HashMap<>();
            List<CFGNode> coveredNodes = new ArrayList<>();
            List<CFGNode> uncoveredNodes = new ArrayList<>();

            for (CFGNode branch : branchNodes) {
                List<String> coveringTests = findTestsCoveringNode(method, branch, existingTests);
                nodeToTests.put(branch, coveringTests);

                if (coveringTests.isEmpty()) {
                    uncoveredNodes.add(branch);
                } else {
                    coveredNodes.add(branch);
                }
            }

            int totalBranches = branchNodes.size();
            int coveredBranches = coveredNodes.size();
            double coveragePercent = totalBranches > 0 ?
                (coveredBranches * 100.0) / totalBranches : 100.0;

            CoverageInfo result = new CoverageInfo(
                totalBranches,
                coveredBranches,
                uncoveredNodes,
                coveredNodes,
                nodeToTests,
                coveragePercent,
                existingTests.size()
            );

            LOG.info("Coverage analysis complete: {}", result);
            return result;
        });
    }

    /**
     * Find which existing tests cover a specific CFG node
     * Uses multiple strategies to map tests to branches
     */
    @NotNull
    private List<String> findTestsCoveringNode(@NotNull PsiMethod method,
                                                 @NotNull CFGNode branch,
                                                 @NotNull List<ExistingTestInfo> existingTests) {
        List<String> coveringTests = new ArrayList<>();

        // Strategy 1: Check if test method name suggests it covers this branch
        String condition = branch.condition() != null ? branch.condition().toLowerCase() : "";
        Set<String> keywords = extractKeywords(condition);

        for (ExistingTestInfo test : existingTests) {
            String testName = test.name().toLowerCase();
            
            // Check if test name contains any keywords from condition
            boolean nameMatches = keywords.stream().anyMatch(testName::contains);
            
            // Also check if test name contains negation keywords (null, empty, invalid, error, etc.)
            boolean negationMatch = testName.contains("null") && condition.contains("null") ||
                                   testName.contains("empty") && condition.contains("empty") ||
                                   testName.contains("invalid") ||
                                   testName.contains("error") ||
                                   testName.contains("exception");
            
            if (nameMatches || negationMatch) {
                coveringTests.add(test.name());
            }
        }

        // Strategy 2: If we have tests but no coverage detected, mark first test as covering
        // (heuristic: at least one test probably covers simple branches)
        if (coveringTests.isEmpty() && !existingTests.isEmpty()) {
            // Check if condition is simple (like null check) and likely covered by any test
            if (keywords.stream().anyMatch(kw -> kw.equals("null") || kw.equals("empty"))) {
                // Take first test that mentions related concepts
                for (ExistingTestInfo test : existingTests) {
                    if (test.name().toLowerCase().contains("null") || 
                        test.name().toLowerCase().contains("empty") ||
                        test.name().toLowerCase().contains("valid")) {
                        coveringTests.add(test.name());
                        break;
                    }
                }
            }
        }

        // Strategy 3: For very simple conditions, assume first test covers it
        if (coveringTests.isEmpty() && !existingTests.isEmpty() && existingTests.size() > 0) {
            // If it's a simple null check or comparison, likely covered
            if (condition.contains("==") || condition.contains("!=") || condition.contains("null")) {
                coveringTests.add(existingTests.get(0).name());
            }
        }

        return coveringTests;
    }

    /**
     * Heuristic: Check if test name suggests it covers this branch
     */
    private boolean testCoversBranch(@NotNull ExistingTestInfo test,
                                      @NotNull CFGNode branch) {
        String testName = test.name().toLowerCase();
        String condition = branch.condition() != null ?
            branch.condition().toLowerCase() : "";

        // Extract keywords from condition
        Set<String> keywords = extractKeywords(condition);

        // Check if test name contains any keywords
        return keywords.stream().anyMatch(testName::contains);
    }

    /**
     * Extract meaningful keywords from a condition expression
     */
    @NotNull
    private Set<String> extractKeywords(@NotNull String condition) {
        Set<String> keywords = new HashSet<>();

        // Remove common operators and syntax
        String cleaned = condition
            .replaceAll("[=!<>]=?", " ")
            .replaceAll("[\\(\\)\\{\\}\\[\\]]", " ")
            .replaceAll("[\\.&|]", " ")
            .trim();

        // Split into tokens
        String[] tokens = cleaned.split("\\s+");
        for (String token : tokens) {
            token = token.trim();
            // Keep meaningful tokens (not keywords like if, null, true, false)
            if (token.length() > 2 &&
                !token.equals("null") &&
                !token.equals("true") &&
                !token.equals("false") &&
                !token.equals("this")) {
                keywords.add(token.toLowerCase());
            }
        }

        return keywords;
    }

    /**
     * Get last analysis result
     */
    @Nullable
    public CoverageInfo getLastResult() {
        return lastAnalysisResult;
    }

    /**
     * Check if analysis is currently running
     */
    public boolean isAnalysisInProgress() {
        return analysisInProgress;
    }

    /**
     * Callback interface for async coverage analysis
     */
    public interface CoverageCallback {
        void onComplete(@Nullable CoverageInfo result);
        default void onError(@NotNull Throwable error) {
            LOG.error("Coverage analysis error", error);
        }
    }
}
