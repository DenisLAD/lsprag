package com.reasoningtestgen;

import com.reasoningtestgen.example.OrderService;
import com.reasoningtestgen.example.OrderService.*;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.*;

/**
 * Simple test runner for OrderServiceTest
 * Bypasses Gradle instrumentation issues
 */
public class RunOrderServiceTest {
    
    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("Running OrderServiceTest");
        System.out.println("========================================\n");

        try {
            // Test 1: Null user
            test_should_throwIllegalArgumentException_when_user_is_null();
            
            // Test 2: Null items
            test_should_throwIllegalArgumentException_when_items_are_null();
            
            // Test 3: Empty cart
            test_should_return_zero_discount_when_items_list_is_empty();
            
            // Test 4: VIP user
            test_should_return_50_percent_discount_when_user_is_vip();
            
            // Test 5: Premium with high cart
            test_should_return_25_percent_discount_when_user_is_premium_and_cart_over_1000();
            
            // Test 6: Premium with low cart
            test_should_return_10_percent_discount_when_user_is_premium_and_cart_under_or_equal_1000();
            
            // Test 7: Regular high cart
            test_should_return_15_percent_discount_when_user_is_regular_and_cart_over_2000();
            
            // Test 8: Regular medium cart
            test_should_return_5_percent_discount_when_user_is_regular_and_cart_over_500();
            
            // Test 9: Regular low cart
            test_should_return_zero_discount_when_user_is_regular_and_cart_under_or_equal_500();
            
            // Test 10: User not found
            test_should_throwIllegalStateException_when_user_not_found_in_repository();

            // Summary
            System.out.println("\n========================================");
            System.out.println("Test Results");
            System.out.println("========================================");
            System.out.println("Total:   " + (passed + failed));
            System.out.println("Passed:  " + passed);
            System.out.println("Failed:  " + failed);
            System.out.println("========================================");
            
            if (failed > 0) {
                System.exit(1);
            } else {
                System.out.println("\n✓ All tests passed!");
                System.exit(0);
            }
        } catch (Exception e) {
            System.err.println("\n✗ Unexpected error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void test_should_throwIllegalArgumentException_when_user_is_null() {
        System.out.println("Test 1: should_throwIllegalArgumentException_when_user_is_null");
        try {
            OrderService service = createService();
            List<Item> items = new ArrayList<>();
            
            try {
                service.calculateDiscount(null, items);
                fail("Expected IllegalArgumentException");
            } catch (IllegalArgumentException e) {
                if (!e.getMessage().contains("user")) {
                    fail("Message should contain 'user': " + e.getMessage());
                }
            }
            pass();
        } catch (Exception e) {
            fail(e);
        }
    }

    private static void test_should_throwIllegalArgumentException_when_items_are_null() {
        System.out.println("\nTest 2: should_throwIllegalArgumentException_when_items_are_null");
        try {
            OrderService service = createService();
            User user = new User(1L, "Test");
            
            try {
                service.calculateDiscount(user, null);
                fail("Expected IllegalArgumentException");
            } catch (IllegalArgumentException e) {
                if (!e.getMessage().contains("items")) {
                    fail("Message should contain 'items': " + e.getMessage());
                }
            }
            pass();
        } catch (Exception e) {
            fail(e);
        }
    }

    private static void test_should_return_zero_discount_when_items_list_is_empty() {
        System.out.println("\nTest 3: should_return_zero_discount_when_items_list_is_empty");
        try {
            OrderServiceExtension service = createService();
            User user = new User(1L, "Test");
            UserProfile profile = new UserProfile(1L, false, false);
            when(service.userRepository.findById(1L)).thenReturn(profile);
            
            double discount = service.calculateDiscount(user, new ArrayList<>());
            assertEqual(0.0, discount, "discount");
            pass();
        } catch (Exception e) {
            fail(e);
        }
    }

    private static void test_should_return_50_percent_discount_when_user_is_vip() {
        System.out.println("\nTest 4: should_return_50_percent_discount_when_user_is_vip");
        try {
            OrderServiceExtension service = createService();
            User vipUser = new User(2L, "VIP");
            UserProfile vipProfile = new UserProfile(2L, true, false);
            when(service.userRepository.findById(2L)).thenReturn(vipProfile);
            
            double discount = service.calculateDiscount(vipUser, createItems(100.0));
            assertEqual(50.0, discount, "discount");
            pass();
        } catch (Exception e) {
            fail(e);
        }
    }

    private static void test_should_return_25_percent_discount_when_user_is_premium_and_cart_over_1000() {
        System.out.println("\nTest 5: should_return_25_percent_discount_when_premium_and_cart_over_1000");
        try {
            OrderServiceExtension service = createService();
            User premiumUser = new User(3L, "Premium");
            UserProfile premiumProfile = new UserProfile(3L, false, true);
            when(service.userRepository.findById(3L)).thenReturn(premiumProfile);
            
            double discount = service.calculateDiscount(premiumUser, createItems(1500.0));
            assertEqual(25.0, discount, "discount");
            pass();
        } catch (Exception e) {
            fail(e);
        }
    }

    private static void test_should_return_10_percent_discount_when_user_is_premium_and_cart_under_or_equal_1000() {
        System.out.println("\nTest 6: should_return_10_percent_discount_when_premium_and_cart_under_1000");
        try {
            OrderServiceExtension service = createService();
            User premiumUser = new User(3L, "Premium");
            UserProfile premiumProfile = new UserProfile(3L, false, true);
            when(service.userRepository.findById(3L)).thenReturn(premiumProfile);
            
            double discount = service.calculateDiscount(premiumUser, createItems(100.0));
            assertEqual(10.0, discount, "discount");
            pass();
        } catch (Exception e) {
            fail(e);
        }
    }

    private static void test_should_return_15_percent_discount_when_user_is_regular_and_cart_over_2000() {
        System.out.println("\nTest 7: should_return_15_percent_discount_when_regular_and_cart_over_2000");
        try {
            OrderServiceExtension service = createService();
            User regularUser = new User(1L, "Regular");
            UserProfile regularProfile = new UserProfile(1L, false, false);
            when(service.userRepository.findById(1L)).thenReturn(regularProfile);
            
            double discount = service.calculateDiscount(regularUser, createItems(2500.0));
            assertEqual(15.0, discount, "discount");
            pass();
        } catch (Exception e) {
            fail(e);
        }
    }

    private static void test_should_return_5_percent_discount_when_user_is_regular_and_cart_over_500() {
        System.out.println("\nTest 8: should_return_5_percent_discount_when_regular_and_cart_over_500");
        try {
            OrderServiceExtension service = createService();
            User regularUser = new User(1L, "Regular");
            UserProfile regularProfile = new UserProfile(1L, false, false);
            when(service.userRepository.findById(1L)).thenReturn(regularProfile);
            
            double discount = service.calculateDiscount(regularUser, createItems(750.0));
            assertEqual(5.0, discount, "discount");
            pass();
        } catch (Exception e) {
            fail(e);
        }
    }

    private static void test_should_return_zero_discount_when_user_is_regular_and_cart_under_or_equal_500() {
        System.out.println("\nTest 9: should_return_zero_discount_when_regular_and_cart_under_500");
        try {
            OrderServiceExtension service = createService();
            User regularUser = new User(1L, "Regular");
            UserProfile regularProfile = new UserProfile(1L, false, false);
            when(service.userRepository.findById(1L)).thenReturn(regularProfile);
            
            double discount = service.calculateDiscount(regularUser, createItems(250.0));
            assertEqual(0.0, discount, "discount");
            pass();
        } catch (Exception e) {
            fail(e);
        }
    }

    private static void test_should_throwIllegalStateException_when_user_not_found_in_repository() {
        System.out.println("\nTest 10: should_throwIllegalStateException_when_user_not_found");
        try {
            OrderServiceExtension service = createService();
            User user = new User(999L, "NotFound");
            when(service.userRepository.findById(999L)).thenReturn(null);
            
            try {
                service.calculateDiscount(user, new ArrayList<>());
                fail("Expected IllegalStateException");
            } catch (IllegalStateException e) {
                if (!e.getMessage().contains("not found")) {
                    fail("Message should contain 'not found': " + e.getMessage());
                }
            }
            pass();
        } catch (Exception e) {
            fail(e);
        }
    }

    // ===== Helper Methods =====

    private static OrderServiceExtension createService() {
        UserRepository userRepository = mock(UserRepository.class);
        PricingEngine pricingEngine = mock(PricingEngine.class);
        return new OrderServiceExtension(pricingEngine, userRepository);
    }

    private static List<Item> createItems(double totalValue) {
        List<Item> items = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            items.add(new Item("item" + i, totalValue / 10.0));
        }
        return items;
    }

    private static void assertEqual(double expected, double actual, String message) {
        if (Math.abs(expected - actual) > 0.001) {
            throw new AssertionError(String.format(
                "Expected %.2f but got %.2f for %s", expected, actual, message
            ));
        }
    }

    private static void pass() {
        passed++;
        System.out.println("  ✓ PASSED");
    }

    private static void fail(String message) {
        failed++;
        System.out.println("  ✗ FAILED: " + message);
    }

    private static void fail(Exception e) {
        failed++;
        System.out.println("  ✗ FAILED: " + e.getMessage());
        e.printStackTrace();
    }
}

// Extension to expose mock
class OrderServiceExtension extends OrderService {
    public UserRepository userRepository;
    
    public OrderServiceExtension(PricingEngine pricingEngine, UserRepository userRepository) {
        super(pricingEngine, userRepository);
        this.userRepository = userRepository;
    }
}
