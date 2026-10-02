package com.dev.fastfood.service;

import com.dev.fastfood.entity.Order;
import com.dev.fastfood.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

// The dual-write safety net: saving the PENDING order to Postgres and
// publishing its event to Kafka are two separate systems with no shared
// transaction, so there's a small gap where one succeeds and the other
// doesn't (e.g. the app crashes right after the DB commit but before the
// Kafka send). This job catches that gap by finding orders that have been
// PENDING suspiciously long AND whose publish Kafka never acknowledged
// (published_at IS NULL), and re-publishing them from the payload saved
// alongside — see Order.pendingPayload / OrderService.createOrder.
//
// Why only unacknowledged ones: an acknowledged order is safely stored in
// Kafka even if it's waiting behind a long queue, and the retry/DLT path
// already guarantees it ends CONFIRMED or FAILED. Before this distinction,
// a K6 burst made this job re-publish 123,664 duplicates of orders that
// were merely queued, which slowed the consumer down even further.
@Component
public class OrderReconciliationScheduler {

    private static final Logger log = LoggerFactory.getLogger(OrderReconciliationScheduler.class);

    // An order normally goes PENDING -> CONFIRMED/FAILED within a second or
    // two. 20s is generous slack above that before we treat it as "stuck."
    private static final int STUCK_AFTER_SECONDS = 20;

    // After this long without Kafka ever acknowledging the publish, we stop
    // re-publishing and mark the order FAILED instead, so nothing is
    // retried forever. Only unacknowledged orders ever reach this check
    // (see the query below) — an order that's merely waiting in a long
    // queue is never failed by it. Measured by the order's age
    // (created_at), not by counting attempts: the age is already stored,
    // and it's what the customer actually experiences. 10 minutes allows
    // ~19 re-publish attempts at one per 30s run, so in practice this only
    // fires if Kafka itself has been unreachable for about that long —
    // then a clear FAILED beats an order hanging forever.
    // Configurable, e.g. --fastfood.reconciliation.give-up-after=2m
    private final Duration giveUpAfter;

    private final OrderRepository orderRepository;
    private final OrderService orderService;

    public OrderReconciliationScheduler(OrderRepository orderRepository, OrderService orderService,
                                        @Value("${fastfood.reconciliation.give-up-after:10m}") Duration giveUpAfter) {
        this.orderRepository = orderRepository;
        this.orderService = orderService;
        this.giveUpAfter = giveUpAfter;
    }

    // Every 30s. If the normal after-commit publish already worked (and was
    // acknowledged), this job simply finds nothing to do — even when the
    // consumer is far behind.
    @Scheduled(fixedDelay = 30_000)
    public void republishStuckOrders() {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime cutoff = now.minusSeconds(STUCK_AFTER_SECONDS);
        OffsetDateTime giveUpCutoff = now.minus(giveUpAfter);
        List<Order> stuckOrders =
                orderRepository.findByStatusAndPublishedAtIsNullAndCreatedAtBefore("PENDING", cutoff);

        for (Order order : stuckOrders) {
            // Checked first, so it also covers orders with no payload
            // (below), which could otherwise never be resolved at all.
            if (order.getCreatedAt().isBefore(giveUpCutoff)) {
                orderService.giveUpOnStuckOrder(order.getId(),
                        "Kafka never acknowledged its publish within " + giveUpAfter.toMinutes() + " min");
                continue;
            }
            if (order.getPendingPayload() == null) {
                // Shouldn't happen — every PENDING order gets a payload at
                // creation time — but skip rather than fail the whole run.
                log.warn("Order {} is stuck PENDING but has no saved payload to republish", order.getId());
                continue;
            }
            orderService.republish(order.getId(), order.getPendingPayload());
        }
    }
}
