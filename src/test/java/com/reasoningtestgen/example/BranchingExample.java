package com.reasoningtestgen.example;

/**
 * Test class with ternary operations and switch-case blocks
 * To verify CFG extraction handles all branching types
 */
public class BranchingExample {

    /**
     * Method with ternary operations and switch-case
     */
    public String processStatus(Status status, int value, boolean flag) {
        // Ternary operation 1
        String prefix = flag ? "Active" : "Inactive";
        
        // Ternary operation 2 (nested)
        String category = value > 100 ? (value > 500 ? "High" : "Medium") : "Low";
        
        // Switch-case block
        String result;
        switch (status) {
            case PENDING:
                result = prefix + "-Pending-" + category;
                break;
            case APPROVED:
                result = prefix + "-Approved-" + category;
                break;
            case REJECTED:
                result = prefix + "-Rejected";
                break;
            default:
                result = "Unknown";
        }
        
        // Ternary in return
        return result != null ? result.toUpperCase() : "EMPTY";
    }

    /**
     * Method with enhanced switch (Java 14+)
     */
    public int calculateDiscount(CustomerType type, int amount) {
        return switch (type) {
            case VIP -> amount > 1000 ? 50 : 25;
            case PREMIUM -> amount > 500 ? 20 : 10;
            case REGULAR -> amount > 2000 ? 15 : (amount > 1000 ? 5 : 0);
            default -> 0;
        };
    }

    public enum Status {
        PENDING, APPROVED, REJECTED
    }

    public enum CustomerType {
        VIP, PREMIUM, REGULAR
    }
}
