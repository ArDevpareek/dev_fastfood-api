package com.dev.fastfood.service;

import com.dev.fastfood.dto.CreateOrderRequest;
import com.dev.fastfood.dto.OrderItemRequest;
import com.dev.fastfood.dto.OrderItemResponse;
import com.dev.fastfood.dto.OrderResponse;
import com.dev.fastfood.entity.*;
import com.dev.fastfood.exception.BusinessRuleException;
import com.dev.fastfood.exception.ResourceNotFoundException;
import com.dev.fastfood.messaging.OrderCreatedEvent;
import com.dev.fastfood.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.dev.fastfood.config.KafkaTopicConfig.ORDER_CREATED_TOPIC;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    // Same Jackson 3 setup as OrderEventSerializer — used here only to turn
    // an OrderCreatedEvent into the JSON text we store in pending_payload,
    // not to talk to Kafka directly (KafkaTemplate + OrderEventSerializer
    // still do that part).
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final UserRepository userRepository;
    private final RestaurantRepository restaurantRepository;
    private final MenuItemRepository menuItemRepository;
    private final KafkaTemplate<String, OrderCreatedEvent> orderEventKafkaTemplate;
    private final PublishAckRecorder publishAckRecorder;

    public OrderService(OrderRepository orderRepository,
                        OrderItemRepository orderItemRepository,
                        UserRepository userRepository,
                        RestaurantRepository restaurantRepository,
                        MenuItemRepository menuItemRepository,
                        KafkaTemplate<String, OrderCreatedEvent> orderEventKafkaTemplate,
                        PublishAckRecorder publishAckRecorder) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.userRepository = userRepository;
        this.restaurantRepository = restaurantRepository;
        this.menuItemRepository = menuItemRepository;
        this.orderEventKafkaTemplate = orderEventKafkaTemplate;
        this.publishAckRecorder = publishAckRecorder;
    }

    // ==================================================================
    // PATH 1 — POST /order calls this. Fast, synchronous, unlocked reads.
    // Every check here is IDENTICAL in spirit to the old Milestone-1/2
    // synchronous flow, so a bad request still gets an instant 404/400,
    // exactly like before. The only things that changed: no locking here
    // (that's now the consumer's job, right before the final save), and
    // only the Order header gets saved — order_items are created later,
    // by the consumer, once it's done its own authoritative re-check.
    // ==================================================================
    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        ValidationResult validated = validate(
                request.getUserId(), request.getRestaurantId(), request.getItems(), false);

        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setUser(validated.user());
        order.setRestaurant(validated.restaurant());
        order.setSubtotal(validated.subtotal());
        order.setDeliveryFee(validated.restaurant().getDeliveryFee());
        order.setTotal(validated.subtotal().add(validated.restaurant().getDeliveryFee()));
        order.setDeliveryAddress(request.getDeliveryAddress());
        order.setDeliveryNotes(request.getDeliveryNotes());
        // Set explicitly (rather than relying on Order's @PrePersist default)
        // because PENDING now carries real meaning in Milestone 3: it's the
        // idempotency key the consumer and the reconciliation job both check.
        // It also means the value is present even when no JPA provider is
        // involved — e.g. in OrderServiceTest, where save() is a Mockito fake.
        order.setStatus("PENDING");

        OrderCreatedEvent event = new OrderCreatedEvent(
                order.getId(), request.getUserId(), request.getRestaurantId(),
                request.getDeliveryAddress(), request.getDeliveryNotes(), request.getItems());

        // Saved in the SAME transaction as the order row — this is what
        // makes the reconciliation job possible later (see class comment
        // on Order.pendingPayload).
        order.setPendingPayload(MAPPER.writeValueAsString(event));

        Order savedOrder = orderRepository.save(order);

        publishAfterCommit(event);

        // Provisional item breakdown for the response — computed from the
        // same validation pass, but NOT yet the persisted order_items rows.
        // The consumer may still (rarely) see different data by the time it
        // authoritatively re-checks, in which case the order ends up FAILED
        // instead of CONFIRMED — that's why GET /order/{id} exists.
        List<OrderItemResponse> provisionalItems = validated.orderItems().stream()
                .map(OrderItemResponse::from)
                .collect(Collectors.toList());

        return OrderResponse.from(savedOrder, provisionalItems);
    }

    // A topic is just a durable, named log — publishing to it doesn't block
    // on anyone reading it. We still only want to publish once we're SURE
    // the order row actually committed, or a consumer could process an
    // event for an order that a rollback later erased. Registering this
    // callback (instead of calling kafkaTemplate.send directly here) is
    // what delays the send until after commit.
    private void publishAfterCommit(OrderCreatedEvent event) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sendEvent(event);
                }
            });
        } else {
            // No transaction to hook into — e.g. a unit test calling this
            // service method directly, outside Spring's @Transactional
            // proxy. Publish immediately rather than silently dropping it.
            sendEvent(event);
        }
    }

    private void sendEvent(OrderCreatedEvent event) {
        UUID orderId = event.getOrderId();
        try {
            orderEventKafkaTemplate.send(ORDER_CREATED_TOPIC, orderId.toString(), event)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            // Not fatal by itself — published_at stays NULL, so
                            // the reconciliation job will re-publish this order
                            // later. Logged so it's visible, not silent.
                            log.warn("Failed to publish order-created event for order {}: {}",
                                    orderId, ex.toString());
                            return;
                        }
                        // Kafka acknowledged it (acks=all: stored by every in-sync
                        // replica). Record that, so the reconciliation job knows
                        // this order is safely queued rather than lost. This
                        // callback runs on Kafka's own I/O thread, so it only
                        // queues the ID; PublishAckRecorder writes them in batches.
                        publishAckRecorder.record(orderId);
                    });
        } catch (Exception ex) {
            // KafkaTemplate.send() doesn't ALWAYS fail through the returned
            // future above — when the producer can't get cluster metadata
            // within max.block.ms (broker completely unreachable), it throws
            // synchronously instead. This runs inside the after-commit hook,
            // on the request thread, so an uncaught throw here would turn
            // a successfully-saved PENDING order into a 500 response. Treat
            // it exactly like the async failure case instead: published_at
            // stays NULL, and reconciliation retries once Kafka is back.
            log.warn("Failed to publish order-created event for order {}: {}", orderId, ex.toString());
        }
    }

    // ==================================================================
    // PATH 2 — the Kafka consumer (OrderConsumer) calls this for every
    // delivered message. This is the AUTHORITATIVE check: locked reads,
    // same pessimistic-locking behavior Milestone 1 introduced, right
    // before the final save.
    // ==================================================================
    @Transactional
    public void processOrderEvent(OrderCreatedEvent event) {
        // Locked read: holds the order row until this transaction commits,
        // so the status check below and the final save can't be interleaved
        // with another consumer or the reconciliation job (see
        // OrderRepository.findByIdForUpdate).
        Order order = orderRepository.findByIdForUpdate(event.getOrderId()).orElse(null);
        if (order == null) {
            // The order row is always saved synchronously before this event
            // is ever published, so this should never happen in practice —
            // logged as a warning rather than thrown, since throwing here
            // would just send an unrecoverable message round the retry loop.
            log.warn("Received order-created event for unknown order {} — ignoring", event.getOrderId());
            return;
        }

        // ---- IDEMPOTENCY CHECK ----
        // "Idempotent" means: doing something twice has the same effect as
        // doing it once. If this order isn't PENDING anymore, a previous
        // delivery of this same message (or an equivalent one from the
        // reconciliation job) already finished the job — redelivery is
        // expected with Kafka's at-least-once delivery, so this is the
        // normal way we stay safe against it, not an error case.
        if (!"PENDING".equals(order.getStatus())) {
            log.info("Order {} is already {} — skipping duplicate delivery", order.getId(), order.getStatus());
            return;
        }

        try {
            ValidationResult validated = validate(
                    event.getUserId(), event.getRestaurantId(), event.getItems(), true);

            List<OrderItem> orderItemEntities = validated.orderItems();
            for (OrderItem item : orderItemEntities) {
                item.setOrder(order);
            }
            orderItemRepository.saveAll(orderItemEntities);

            // Recompute from THIS (locked, authoritative) read, in case
            // anything changed between the sync validation and now.
            order.setSubtotal(validated.subtotal());
            order.setTotal(validated.subtotal().add(validated.restaurant().getDeliveryFee()));
            order.setStatus("CONFIRMED");
            order.setPendingPayload(null); // no longer needed once resolved
            orderRepository.save(order);

        } catch (ResourceNotFoundException | BusinessRuleException ex) {
            // A business rule failing here is an expected, TERMINAL outcome
            // (e.g. the restaurant got deactivated in the gap between the
            // sync check and now) — not a bug and not a transient failure,
            // so retrying it would never help. We mark it FAILED and do
            // NOT rethrow, so OrderConsumer's retry/DLT machinery never
            // sees this as an error to retry.
            markOrderFailed(order, "Validation failed during processing: " + ex.getMessage());
        }
    }

    // Used both by the business-failure path above and by OrderConsumer's
    // @DltHandler, once a message has exhausted every retry attempt.
    @Transactional
    public void markOrderFailed(UUID orderId, String reason) {
        orderRepository.findById(orderId).ifPresent(order -> markOrderFailed(order, reason));
    }

    private void markOrderFailed(Order order, String reason) {
        if (!"PENDING".equals(order.getStatus())) {
            return; // already resolved — same idempotency guard as above
        }
        log.warn("Order {} marked FAILED: {}", order.getId(), reason);
        order.setStatus("FAILED");
        order.setPendingPayload(null);
        orderRepository.save(order);
    }

    // ==================================================================
    // Shared validation — the same 8 business-rule checks either path
    // needs. withLock=false (sync path) uses plain reads; withLock=true
    // (consumer path) uses the same pessimistic-locking queries Milestone 1
    // introduced, so nothing about that fix changed, it just moved here.
    // ==================================================================
    private ValidationResult validate(UUID userId, UUID restaurantId,
                                       List<OrderItemRequest> requestedItems, boolean withLock) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        Restaurant restaurant = (withLock
                ? restaurantRepository.findByIdForUpdate(restaurantId)
                : restaurantRepository.findById(restaurantId))
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant not found: " + restaurantId));

        if (!Boolean.TRUE.equals(restaurant.getIsActive())) {
            throw new BusinessRuleException("Restaurant is not currently active: " + restaurant.getId());
        }

        if (requestedItems == null || requestedItems.isEmpty()) {
            throw new BusinessRuleException("Order must contain at least one item");
        }

        List<UUID> menuItemIds = requestedItems.stream()
                .map(OrderItemRequest::getMenuItemId)
                .collect(Collectors.toList());

        List<MenuItem> foundMenuItems = withLock
                ? menuItemRepository.findAllByIdForUpdate(menuItemIds)
                : menuItemRepository.findAllById(menuItemIds);

        Map<UUID, MenuItem> menuItemMap = foundMenuItems.stream()
                .collect(Collectors.toMap(MenuItem::getId, item -> item));

        if (menuItemMap.size() != menuItemIds.size()) {
            throw new ResourceNotFoundException("One or more menu items were not found");
        }

        BigDecimal subtotal = BigDecimal.ZERO;
        List<OrderItem> orderItemEntities = new ArrayList<>();

        for (OrderItemRequest itemRequest : requestedItems) {
            MenuItem menuItem = menuItemMap.get(itemRequest.getMenuItemId());

            if (!menuItem.getRestaurant().getId().equals(restaurant.getId())) {
                throw new BusinessRuleException(
                        "Menu item " + menuItem.getId() + " does not belong to restaurant "
                                + restaurant.getId());
            }

            if (!Boolean.TRUE.equals(menuItem.getIsAvailable())) {
                throw new BusinessRuleException("Menu item is not available: " + menuItem.getId());
            }

            Integer quantity = itemRequest.getQuantity();
            if (quantity == null || quantity < 1) {
                throw new BusinessRuleException("Quantity must be at least 1 for item: " + menuItem.getId());
            }

            BigDecimal unitPrice = menuItem.getPrice();
            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
            subtotal = subtotal.add(lineTotal);

            OrderItem orderItem = new OrderItem();
            orderItem.setId(UUID.randomUUID());
            orderItem.setMenuItem(menuItem);
            orderItem.setQuantity(quantity);
            orderItem.setUnitPrice(unitPrice);
            orderItem.setTotalPrice(lineTotal);
            orderItem.setSpecialInstructions(itemRequest.getSpecialInstructions());
            orderItemEntities.add(orderItem);
        }

        if (subtotal.compareTo(restaurant.getMinOrderAmount()) < 0) {
            throw new BusinessRuleException(
                    "Order subtotal " + subtotal + " is below the minimum of "
                            + restaurant.getMinOrderAmount());
        }

        return new ValidationResult(user, restaurant, orderItemEntities, subtotal);
    }

    // GET /order/{id} — how the client finds out what happened to an
    // order it can no longer wait for synchronously. Items are only
    // populated once the consumer has actually saved them (status
    // CONFIRMED); PENDING or FAILED orders simply have none yet.
    @Transactional(readOnly = true)
    public OrderResponse getOrder(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        List<OrderItemResponse> items = orderItemRepository.findByOrderId(orderId).stream()
                .map(OrderItemResponse::from)
                .collect(Collectors.toList());

        return OrderResponse.from(order, items);
    }

    // Used by OrderReconciliationScheduler once an order has been PENDING
    // longer than its give-up limit. Returns false if the order was no
    // longer PENDING by the time we tried (e.g. the consumer confirmed it
    // a moment ago), in which case nothing changes.
    @Transactional
    public boolean giveUpOnStuckOrder(UUID orderId, String reason) {
        boolean markedFailed = orderRepository.markFailedIfPending(orderId, OffsetDateTime.now()) == 1;
        if (markedFailed) {
            log.error("Order {} marked FAILED by reconciliation: {}", orderId, reason);
        }
        return markedFailed;
    }

    // Used by OrderReconciliationScheduler to re-send an order stuck in
    // PENDING whose original Kafka publish may never have happened.
    public void republish(UUID orderId, String pendingPayloadJson) {
        OrderCreatedEvent event = MAPPER.readValue(pendingPayloadJson, OrderCreatedEvent.class);
        log.info("Reconciliation: re-publishing order-created event for order {}", orderId);
        sendEvent(event);
    }

    // Plain data holder for what validate() computes — never persisted
    // itself, just passed straight back to whichever path called it.
    private record ValidationResult(User user, Restaurant restaurant,
                                     List<OrderItem> orderItems, BigDecimal subtotal) {
    }
}
