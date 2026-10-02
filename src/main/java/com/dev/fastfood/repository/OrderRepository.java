package com.dev.fastfood.repository;

import com.dev.fastfood.entity.Order;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    // Used by the reconciliation job (OrderReconciliationScheduler) to find
    // orders saved as PENDING whose Kafka publish was never ACKNOWLEDGED
    // (published_at IS NULL) — the only ones that might truly be lost.
    // Orders Kafka has acknowledged are deliberately left out: they may be
    // waiting in a long queue, but they're safely stored, and the
    // retry/DLT path guarantees they end CONFIRMED or FAILED.
    List<Order> findByStatusAndPublishedAtIsNullAndCreatedAtBefore(String status, OffsetDateTime cutoff);

    // Records that Kafka acknowledged these orders' events, in one statement
    // (see PublishAckRecorder for why it's batched). A targeted UPDATE
    // rather than load + save, so it can't clash with the consumer, which
    // may be saving the same order at the same moment. @Transactional
    // because it's called from a scheduler thread, outside any service
    // transaction.
    @Transactional
    @Modifying
    @Query("UPDATE Order o SET o.publishedAt = :now WHERE o.id IN :ids")
    int markPublished(@Param("ids") Collection<UUID> ids, @Param("now") OffsetDateTime now);

    // Same PESSIMISTIC_WRITE idea as RestaurantRepository.findByIdForUpdate.
    // The consumer holds this lock on the order row while it processes it,
    // so nothing else (a second consumer during a rebalance, or the
    // reconciliation job giving up on the order) can change its status
    // halfway through.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") UUID id);

    // One conditional UPDATE instead of "read, check, save": the status
    // check and the change happen in a single statement, so this can never
    // overwrite an order the consumer has just CONFIRMED. Returns how many
    // rows changed: 1 if we gave up on it, 0 if it was no longer PENDING.
    @Modifying
    @Query("UPDATE Order o SET o.status = 'FAILED', o.pendingPayload = null, o.updatedAt = :now "
            + "WHERE o.id = :id AND o.status = 'PENDING'")
    int markFailedIfPending(@Param("id") UUID id, @Param("now") OffsetDateTime now);
}
