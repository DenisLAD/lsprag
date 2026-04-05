package com.reasoningtestgen.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * Coverage information for a method under test
 * Maps CFG nodes to covering test methods
 */
public record CoverageInfo(
    @JsonProperty("totalBranches") int totalBranches,
    @JsonProperty("coveredBranches") int coveredBranches,
    @JsonProperty("uncoveredBranches") List<CFGNode> uncoveredNodes,
    @JsonProperty("coveredNodes") List<CFGNode> coveredNodes,
    @JsonProperty("nodeCoverage") Map<CFGNode, List<String>> nodeToTests,
    @JsonProperty("overallCoveragePercent") double overallCoveragePercent,
    @JsonProperty("existingTestCount") int existingTestCount
) {
    @Override
    public String toString() {
        return String.format(
            "CoverageInfo{%.0f%%, %d/%d branches, %d tests}",
            overallCoveragePercent,
            coveredBranches,
            totalBranches,
            existingTestCount
        );
    }
    
    /**
     * Check if a specific CFG node is covered
     */
    public boolean isNodeCovered(CFGNode node) {
        return nodeToTests.containsKey(node) && !nodeToTests.get(node).isEmpty();
    }
    
    /**
     * Get test methods that cover a specific node
     */
    public List<String> getTestsCoveringNode(CFGNode node) {
        return nodeToTests.getOrDefault(node, List.of());
    }
}
