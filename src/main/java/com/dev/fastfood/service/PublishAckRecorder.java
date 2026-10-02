package com.dev.fastfood.service;

import com.dev.fastfood.repository.OrderRepository;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

// Records "Kafka acknowledged this order's event" (orders.published_at) in
// BATCHES rather than one UPDATE per order.
//
// Why batched: Kafka calls the send-completion callback on its own I/O
// thread, so the callback must stay cheap — it only adds the order ID to an
// in-memory queue. Twice a second, one UPDATE ... WHERE id IN (...) marks up
// to 1,000 orders at once. The first version did one UPDATE per order on a
// 2-thread pool: under the K6 burst (672 orders/s) it fell behind, 16,430
// updates were rejected, and those orders were needlessly re-published.
//
// If a batch fails (or the app dies before flushing), those orders simply
// keep published_at = NULL and the reconciliation job re-publishes them —
// safe, because the consumer skips anything that's no longer PENDING.
@Component
public class PublishAckRecorder {

    private static final Logger log = LoggerFactory.getLogger(PublishAckRecorder.class);

    // Postgres handles a 1,000-item IN list easily; this keeps each
    // statement (and its transaction) small.
    static final int MAX_BATCH_SIZE = 1_000;

    private final Queue<UUID> acknowledged = new ConcurrentLinkedQueue<>();
    private final OrderRepository orderRepository;

    public PublishAckRecorder(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    // Called from Kafka's I/O thread — non-blocking, no DB work.
    public void record(UUID orderId) {
        acknowledged.add(orderId);
    }

    // 500ms keeps published_at far fresher than the reconciliation job's
    // 20s "stuck" threshold, so an acknowledged order is never mistaken for
    // a lost one.
    @Scheduled(fixedDelay = 500)
    public void flush() {
        List<UUID> batch = new ArrayList<>(MAX_BATCH_SIZE);
        UUID id;
        while ((id = acknowledged.poll()) != null) {
            batch.add(id);
            if (batch.size() == MAX_BATCH_SIZE) {
                write(batch);
                batch = new ArrayList<>(MAX_BATCH_SIZE);
            }
        }
        if (!batch.isEmpty()) {
            write(batch);
        }
    }

    private void write(List<UUID> batch) {
        try {
            orderRepository.markPublished(batch, OffsetDateTime.now());
        } catch (RuntimeException ex) {
            log.warn("Failed to record publish acknowledgement for {} orders — reconciliation "
                    + "will re-publish them: {}", batch.size(), ex.toString());
        }
    }

    // On a normal shutdown, write whatever is still queued instead of
    // leaving those orders to be needlessly re-published after restart.
    @PreDestroy
    public void flushOnShutdown() {
        flush();
    }
}
