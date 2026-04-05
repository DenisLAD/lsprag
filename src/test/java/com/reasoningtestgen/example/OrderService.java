package com.reasoningtestgen.example;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Order service for calculating discounts based on user role and items.
 * 
 * This service must:
 * - Always validate user input
 * - Apply correct discount rules
 * - Never return negative discounts
 */
public class OrderService {

    private final PricingEngine pricingEngine;
    private final UserRepository userRepository;

    public OrderService(PricingEngine pricingEngine, UserRepository userRepository) {
        this.pricingEngine = Objects.requireNonNull(pricingEngine, "PricingEngine must not be null");
        this.userRepository = Objects.requireNonNull(userRepository, "UserRepository must not be null");
    }

    /**
     * Calculates discount percentage for a user based on their role and cart items.
     * 
     * @param user The user requesting discount. Must not be null.
     * @param items List of items in cart. Can be empty but not null.
     * @return Discount percentage from 0 to 50
     * @throws IllegalArgumentException if user is null
     * @throws IllegalArgumentException if items is null
     * @throws IllegalStateException if user not found in repository
     */
    public double calculateDiscount(User user, List<Item> items) {
        // Validate input
        if (user == null) {
            throw new IllegalArgumentException("User must not be null");
        }
        if (items == null) {
            throw new IllegalArgumentException("Items must not be null");
        }

        // Fetch user profile
        UserProfile profile = userRepository.findById(user.getId());
        if (profile == null) {
            throw new IllegalStateException("User not found: " + user.getId());
        }

        // Empty cart = no discount
        if (items.isEmpty()) {
            return 0.0;
        }

        // Calculate total cart value
        double totalValue = items.stream()
            .mapToDouble(Item::getPrice)
            .sum();

        // VIP users always get 50% discount
        if (profile.isVip()) {
            return 50.0;
        }

        // Premium users get 25% if cart > 1000
        if (profile.isPremium()) {
            if (totalValue > 1000.0) {
                return 25.0;
            }
            return 10.0;
        }

        // Regular users: discount based on cart value
        if (totalValue > 2000.0) {
            return 15.0;
        } else if (totalValue > 500.0) {
            return 5.0;
        }

        return 0.0;
    }

    /**
     * Applies discount to order total.
     * 
     * @param orderTotal Original order total
     * @param discountPercentage Discount percentage (0-50)
     * @return Discounted total
     * @throws IllegalArgumentException if discount is out of range
     */
    public double applyDiscount(double orderTotal, double discountPercentage) {
        if (discountPercentage < 0.0 || discountPercentage > 50.0) {
            throw new IllegalArgumentException(
                "Discount must be between 0 and 50, got: " + discountPercentage
            );
        }
        
        double discount = orderTotal * (discountPercentage / 100.0);
        return Math.max(0.0, orderTotal - discount);
    }

    // ===== Supporting Classes =====

    public static class User {
        private final Long id;
        private final String name;

        public User(Long id, String name) {
            this.id = id;
            this.name = name;
        }

        public Long getId() { return id; }
        public String getName() { return name; }
    }

    public static class Item {
        private final String name;
        private final double price;

        public Item(String name, double price) {
            this.name = name;
            this.price = price;
        }

        public String getName() { return name; }
        public double getPrice() { return price; }
    }

    public interface PricingEngine {
        double calculateTax(double amount);
    }

    public interface UserRepository {
        UserProfile findById(Long userId);
    }

    public static class UserProfile {
        private final Long userId;
        private final boolean vip;
        private final boolean premium;

        public UserProfile(Long userId, boolean vip, boolean premium) {
            this.userId = userId;
            this.vip = vip;
            this.premium = premium;
        }

        public Long getUserId() { return userId; }
        public boolean isVip() { return vip; }
        public boolean isPremium() { return premium; }
    }
}
