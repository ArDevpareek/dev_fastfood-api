package com.dev.fastfood.service;

import com.dev.fastfood.entity.Order;
import com.dev.fastfood.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Same approach as OrderServiceTest: fake repository and service, so this
// only tests the job's own decision — re-publish, or give up.
@ExtendWith(MockitoExtension.class)
class OrderReconciliationSchedulerTest {

    @Mock private OrderRepository orderRepository;
    @Mock private OrderService orderService;

    private OrderReconciliationScheduler scheduler;

    @BeforeEach
    void setUp() {
        // Built by hand (not @InjectMocks) because the give-up limit is a
        // plain Duration, not a mock.
        scheduler = new OrderReconciliationScheduler(orderRepository, orderService, Duration.ofMinutes(10));
    }

    private Order pendingOrderCreatedMinutesAgo(long minutes) {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setStatus("PENDING");
        order.setPendingPayload("{\"orderId\":\"...\"}");
        order.setCreatedAt(OffsetDateTime.now().minusMinutes(minutes));
        return order;
    }

    @Test
    void stuckButWithinLimit_isRepublished() {
        Order order = pendingOrderCreatedMinutesAgo(1);
        when(orderRepository.findByStatusAndPublishedAtIsNullAndCreatedAtBefore(eq("PENDING"), any())).thenReturn(List.of(order));

        scheduler.republishStuckOrders();

        verify(orderService).republish(order.getId(), order.getPendingPayload());
        verify(orderService, never()).giveUpOnStuckOrder(any(), anyString());
    }

    @Test
    void stuckPastLimit_isMarkedFailedInsteadOfRepublished() {
        Order order = pendingOrderCreatedMinutesAgo(11);
        when(orderRepository.findByStatusAndPublishedAtIsNullAndCreatedAtBefore(eq("PENDING"), any())).thenReturn(List.of(order));

        scheduler.republishStuckOrders();

        verify(orderService).giveUpOnStuckOrder(eq(order.getId()), anyString());
        verify(orderService, never()).republish(any(), anyString());
    }
}
