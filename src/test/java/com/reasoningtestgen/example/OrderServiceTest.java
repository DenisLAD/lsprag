import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.reasoningtestgen.example.OrderService;
import com.reasoningtestgen.example.OrderService.User;
import com.reasoningtestgen.example.OrderService.Item;
import com.reasoningtestgen.example.OrderService.UserRepository;
import com.reasoningtestgen.example.OrderService.PricingEngine;
import com.reasoningtestgen.example.OrderService.UserProfile;

import java.util.ArrayList;
import java.util.List;

class OrderServiceTest {

    private OrderService orderService;
    private UserRepository userRepository;
    private PricingEngine pricingEngine;
    private User testUser;
    private UserProfile regularProfile;
    private UserProfile vipProfile;
    private UserProfile premiumProfile;
    private List<Item> emptyItems;
    private List<Item> vipItems;
    private List<Item> premiumHighValueItems;
    private List<Item> regularHighValueItems;
    private List<Item> regularMediumValueItems;
    private List<Item> regularLowValueItems;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        pricingEngine = mock(PricingEngine.class);
        // Correct order: PricingEngine first, then UserRepository
        orderService = new OrderService(pricingEngine, userRepository);

        testUser = new User(1L, "Test User");
        
        // Create user profiles
        regularProfile = new UserProfile(1L, false, false);
        vipProfile = new UserProfile(2L, true, false);
        premiumProfile = new UserProfile(3L, false, true);
        
        emptyItems = new ArrayList<>();
        vipItems = createItemsWithTotal(100.0);
        premiumHighValueItems = createItemsWithTotal(1500.0);
        regularHighValueItems = createItemsWithTotal(2500.0);
        regularMediumValueItems = createItemsWithTotal(750.0);
        regularLowValueItems = createItemsWithTotal(250.0);
    }

    @Nested
    class NullInputValidation {

        @Test
        @DisplayName("should_throwIllegalArgumentException_when_user_is_null")
        void should_throwIllegalArgumentException_when_user_is_null() {
            assertThatThrownBy(() -> orderService.calculateDiscount(null, emptyItems))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("user");
        }

        @Test
        @DisplayName("should_throwIllegalArgumentException_when_items_are_null")
        void should_throwIllegalArgumentException_when_items_are_null() {
            assertThatThrownBy(() -> orderService.calculateDiscount(testUser, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("items");
        }
    }

    @Nested
    class EmptyCartScenarios {

        @Test
        @DisplayName("should_return_zero_discount_when_items_list_is_empty")
        void should_return_zero_discount_when_items_list_is_empty() {
            when(userRepository.findById(1L)).thenReturn(regularProfile);
            double discount = orderService.calculateDiscount(testUser, emptyItems);
            assertThat(discount).isZero();
        }
    }

    @Nested
    class VipUserScenarios {

        @Test
        @DisplayName("should_return_50_percent_discount_when_user_is_vip")
        void should_return_50_percent_discount_when_user_is_vip() {
            User vipUser = new User(2L, "VIP User");
            when(userRepository.findById(2L)).thenReturn(vipProfile);

            double discount = orderService.calculateDiscount(vipUser, vipItems);
            assertThat(discount).isEqualTo(50.0);
        }
    }

    @Nested
    class PremiumUserScenarios {

        @Test
        @DisplayName("should_return_25_percent_discount_when_user_is_premium_and_cart_over_1000")
        void should_return_25_percent_discount_when_user_is_premium_and_cart_over_1000() {
            User premiumUser = new User(3L, "Premium User");
            when(userRepository.findById(3L)).thenReturn(premiumProfile);

            double discount = orderService.calculateDiscount(premiumUser, premiumHighValueItems);
            assertThat(discount).isEqualTo(25.0);
        }

        @Test
        @DisplayName("should_return_10_percent_discount_when_user_is_premium_and_cart_under_or_equal_1000")
        void should_return_10_percent_discount_when_user_is_premium_and_cart_under_or_equal_1000() {
            User premiumUser = new User(3L, "Premium User");
            when(userRepository.findById(3L)).thenReturn(premiumProfile);

            double discount = orderService.calculateDiscount(premiumUser, vipItems);
            assertThat(discount).isEqualTo(10.0);
        }
    }

    @Nested
    class RegularUserScenarios {

        @Test
        @DisplayName("should_return_15_percent_discount_when_user_is_regular_and_cart_over_2000")
        void should_return_15_percent_discount_when_user_is_regular_and_cart_over_2000() {
            User regularUser = new User(1L, "Regular User");
            when(userRepository.findById(1L)).thenReturn(regularProfile);

            double discount = orderService.calculateDiscount(regularUser, regularHighValueItems);
            assertThat(discount).isEqualTo(15.0);
        }

        @Test
        @DisplayName("should_return_5_percent_discount_when_user_is_regular_and_cart_over_500")
        void should_return_5_percent_discount_when_user_is_regular_and_cart_over_500() {
            User regularUser = new User(1L, "Regular User");
            when(userRepository.findById(1L)).thenReturn(regularProfile);

            double discount = orderService.calculateDiscount(regularUser, regularMediumValueItems);
            assertThat(discount).isEqualTo(5.0);
        }

        @Test
        @DisplayName("should_return_zero_discount_when_user_is_regular_and_cart_under_or_equal_500")
        void should_return_zero_discount_when_user_is_regular_and_cart_under_or_equal_500() {
            User regularUser = new User(1L, "Regular User");
            when(userRepository.findById(1L)).thenReturn(regularProfile);

            double discount = orderService.calculateDiscount(regularUser, regularLowValueItems);
            assertThat(discount).isZero();
        }
    }

    @Nested
    class UserNotFoundScenarios {

        @Test
        @DisplayName("should_throwIllegalStateException_when_user_not_found_in_repository")
        void should_throwIllegalStateException_when_user_not_found_in_repository() {
            when(userRepository.findById(any())).thenReturn(null);
            assertThatThrownBy(() -> orderService.calculateDiscount(testUser, emptyItems))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("not found");
        }
    }

    private List<Item> createItemsWithTotal(double totalValue) {
        List<Item> items = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            items.add(new Item("item" + i, totalValue / 10.0));
        }
        return items;
    }
}