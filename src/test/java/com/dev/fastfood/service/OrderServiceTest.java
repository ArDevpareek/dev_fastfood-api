package com.dev.fastfood.service;

import com.dev.fastfood.dto.CreateOrderRequest;
import com.dev.fastfood.dto.OrderItemRequest;
import com.dev.fastfood.dto.OrderResponse;
import com.dev.fastfood.entity.*;
import com.dev.fastfood.exception.BusinessRuleException;
import com.dev.fastfood.exception.ResourceNotFoundException;
import com.dev.fastfood.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

// @ExtendWith(MockitoExtension.class) turns on Mockito — the tool that
// lets us create FAKE repositories, so this test never touches a real
// database. That's what makes it fast (milliseconds, not seconds).
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    // @Mock = a fake version of each repository. We control exactly
    // what they return, instead of relying on real data.
    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private UserRepository userRepository;
    @Mock private RestaurantRepository restaurantRepository;
    @Mock private MenuItemRepository menuItemRepository;

    // @InjectMocks builds a REAL OrderService, but hands it all the
    // FAKE repositories above instead of real ones.
    @InjectMocks
    private OrderService orderService;

    // These are just reusable test data we'll build fresh before each test.
    private User testUser;
    private Restaurant testRestaurant;
    private MenuItem testMenuItem;

    // @BeforeEach runs this method before EVERY single test below,
    // so we always start with fresh, known data.
    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(UUID.randomUUID());
        testUser.setEmail("test@example.com");

        testRestaurant = new Restaurant();
        testRestaurant.setId(UUID.randomUUID());
        testRestaurant.setName("Test Restaurant");
        testRestaurant.setIsActive(true);
        testRestaurant.setDeliveryFee(new BigDecimal("30.00"));
        testRestaurant.setMinOrderAmount(new BigDecimal("100.00"));

        testMenuItem = new MenuItem();
        testMenuItem.setId(UUID.randomUUID());
        testMenuItem.setRestaurant(testRestaurant);
        testMenuItem.setName("Test Item");
        testMenuItem.setPrice(new BigDecimal("150.00"));
        testMenuItem.setIsAvailable(true);
    }

    // Small helper so we don't repeat this in every test.
    private CreateOrderRequest buildValidRequest() {
        OrderItemRequest itemRequest = new OrderItemRequest();
        itemRequest.setMenuItemId(testMenuItem.getId());
        itemRequest.setQuantity(2);

        CreateOrderRequest request = new CreateOrderRequest();
        request.setUserId(testUser.getId());
        request.setRestaurantId(testRestaurant.getId());
        request.setDeliveryAddress("Test Address");
        request.setItems(List.of(itemRequest));
        return request;
    }

    @Test
    void createOrder_happyPath_calculatesCorrectTotals() {
        // ARRANGE: tell every fake repository what to return when asked
        when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
        when(restaurantRepository.findById(testRestaurant.getId())).thenReturn(Optional.of(testRestaurant));
        when(menuItemRepository.findAllById(anyList())).thenReturn(List.of(testMenuItem));
        // save() normally returns what got saved — we fake that too,
        // just handing back whatever was passed in.
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderItemRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        // ACT: actually call the real method we're testing
        OrderResponse response = orderService.createOrder(buildValidRequest());

        // ASSERT: check the real math happened correctly
        // 2 x ₹150 = ₹300 subtotal, + ₹30 delivery = ₹330 total
        assertThat(response.getSubtotal()).isEqualByComparingTo("300.00");
        assertThat(response.getDeliveryFee()).isEqualByComparingTo("30.00");
        assertThat(response.getTotal()).isEqualByComparingTo("330.00");
    }

    @Test
    void createOrder_userNotFound_throwsResourceNotFound() {
        when(userRepository.findById(testUser.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.createOrder(buildValidRequest()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User not found");
    }

    @Test
    void createOrder_inactiveRestaurant_throwsBusinessRule() {
        testRestaurant.setIsActive(false);
        when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
        when(restaurantRepository.findById(testRestaurant.getId())).thenReturn(Optional.of(testRestaurant));

        assertThatThrownBy(() -> orderService.createOrder(buildValidRequest()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("not currently active");
    }

    @Test
    void createOrder_belowMinimumOrder_throwsBusinessRule() {
        // Order just 1 item at ₹150 — restaurant minimum is ₹100 x... wait,
        // let's actually make it fail: set minimum HIGHER than the order.
        testRestaurant.setMinOrderAmount(new BigDecimal("500.00"));

        when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
        when(restaurantRepository.findById(testRestaurant.getId())).thenReturn(Optional.of(testRestaurant));
        when(menuItemRepository.findAllById(anyList())).thenReturn(List.of(testMenuItem));

        assertThatThrownBy(() -> orderService.createOrder(buildValidRequest()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("below the minimum");
    }

    @Test
    void createOrder_priceComesFromDatabase_notFromClient() {
        // This test proves the security fix works: even though our
        // request DTO has no price field at all (by design), let's prove
        // the actual unit price used is whatever's in the database.
        when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
        when(restaurantRepository.findById(testRestaurant.getId())).thenReturn(Optional.of(testRestaurant));
        when(menuItemRepository.findAllById(anyList())).thenReturn(List.of(testMenuItem));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderItemRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse response = orderService.createOrder(buildValidRequest());

        // testMenuItem's real price is ₹150 — confirm that's exactly
        // what ended up in the order line, untouched.
        assertThat(response.getItems().get(0).getUnitPrice())
                .isEqualByComparingTo("150.00");
    }
}