package com.dev.fastfood.service;

import com.dev.fastfood.dto.CreateOrderRequest;
import com.dev.fastfood.dto.OrderItemRequest;
import com.dev.fastfood.dto.OrderResponse;
import com.dev.fastfood.entity.*;
import com.dev.fastfood.exception.BusinessRuleException;
import com.dev.fastfood.exception.ResourceNotFoundException;
import com.dev.fastfood.messaging.OrderCreatedEvent;
import com.dev.fastfood.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
    // Milestone 3: OrderService now also depends on a KafkaTemplate to
    // publish the order-created event. Faked here for the same reason as
    // the repositories — no real Kafka broker involved in this test.
    @Mock private KafkaTemplate<String, OrderCreatedEvent> orderEventKafkaTemplate;
    // Queues "Kafka acknowledged this order" for a batched DB write — faked
    // here, so the tests below can check whether it was told about an order.
    @Mock private PublishAckRecorder publishAckRecorder;

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

    // Milestone 3: createOrder() only saves the Order header now (no
    // locking reads — those moved to the consumer's authoritative check),
    // so these stubs use the plain, non-locking repository methods.
    private void stubHappyPathReads() {
        when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
        when(restaurantRepository.findById(testRestaurant.getId())).thenReturn(Optional.of(testRestaurant));
        when(menuItemRepository.findAllById(anyList())).thenReturn(List.of(testMenuItem));
        // save() normally returns what got saved — we fake that too,
        // just handing back whatever was passed in.
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        // publishAfterCommit() falls back to publishing immediately when
        // there's no active Spring transaction to hook into (exactly the
        // case here, since this test calls the service directly) — so the
        // KafkaTemplate mock needs a non-null result to call .whenComplete() on.
        when(orderEventKafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));
    }

    @Test
    void createOrder_happyPath_calculatesCorrectTotals() {
        stubHappyPathReads();

        OrderResponse response = orderService.createOrder(buildValidRequest());

        // 2 x ₹150 = ₹300 subtotal, + ₹30 delivery = ₹330 total
        assertThat(response.getSubtotal()).isEqualByComparingTo("300.00");
        assertThat(response.getDeliveryFee()).isEqualByComparingTo("30.00");
        assertThat(response.getTotal()).isEqualByComparingTo("330.00");
        assertThat(response.getStatus()).isEqualTo("PENDING");
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
        // Order just 2 items at ₹150 each (₹300) — set the minimum above that.
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
        stubHappyPathReads();

        OrderResponse response = orderService.createOrder(buildValidRequest());

        // testMenuItem's real price is ₹150 — confirm that's exactly
        // what ended up in the (provisional) order line, untouched.
        assertThat(response.getItems().get(0).getUnitPrice())
                .isEqualByComparingTo("150.00");
    }

    // ---- Milestone 3: recording that Kafka acknowledged the publish ----

    @Test
    void publishAcknowledged_isRecorded() {
        stubHappyPathReads(); // includes a send() that succeeds

        OrderResponse response = orderService.createOrder(buildValidRequest());

        verify(publishAckRecorder).record(response.getId());
    }

    @Test
    void publishFails_leavesOrderUnacknowledgedForReconciliation() {
        when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
        when(restaurantRepository.findById(testRestaurant.getId())).thenReturn(Optional.of(testRestaurant));
        when(menuItemRepository.findAllById(anyList())).thenReturn(List.of(testMenuItem));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderEventKafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker unreachable")));

        // Still succeeds for the client: the order is saved, and
        // reconciliation will re-publish it later.
        OrderResponse response = orderService.createOrder(buildValidRequest());

        assertThat(response.getStatus()).isEqualTo("PENDING");
        verify(publishAckRecorder, never()).record(any());
    }

    // ---- Milestone 3: the consumer side (processOrderEvent) ----

    private OrderCreatedEvent buildEventFor(Order order) {
        OrderItemRequest itemRequest = new OrderItemRequest();
        itemRequest.setMenuItemId(testMenuItem.getId());
        itemRequest.setQuantity(2);
        return new OrderCreatedEvent(order.getId(), testUser.getId(), testRestaurant.getId(),
                "Test Address", null, List.of(itemRequest));
    }

    private Order pendingOrder() {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setStatus("PENDING");
        return order;
    }

    @Test
    void processOrderEvent_alreadyConfirmed_skipsWithoutReprocessing() {
        // Idempotency: a redelivered message for an order that's already
        // past PENDING must not be reprocessed (and must not create a
        // second set of order_items).
        Order order = pendingOrder();
        order.setStatus("CONFIRMED");
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));

        orderService.processOrderEvent(buildEventFor(order));

        verify(menuItemRepository, never()).findAllByIdForUpdate(anyList());
        verify(orderItemRepository, never()).saveAll(anyList());
    }

    @Test
    void processOrderEvent_businessRuleFailsOnRecheck_marksOrderFailedWithoutThrowing() {
        // e.g. the restaurant was deactivated in the gap between the sync
        // check and this consumer run — a terminal outcome, not a bug, so
        // this must NOT throw (that would trigger the retry/DLT machinery
        // for something retrying could never fix).
        Order order = pendingOrder();
        testRestaurant.setIsActive(false);
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
        when(restaurantRepository.findByIdForUpdate(testRestaurant.getId())).thenReturn(Optional.of(testRestaurant));

        orderService.processOrderEvent(buildEventFor(order));

        assertThat(order.getStatus()).isEqualTo("FAILED");
        verify(orderItemRepository, never()).saveAll(anyList());
    }
}
