package com.dev.fastfood.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
public class Order {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    // Same relationship idea as MenuItem → Restaurant from Day 3.
    // An order belongs to exactly one user.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // And an order belongs to exactly one restaurant.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @Column(name = "subtotal")
    private BigDecimal subtotal;

    @Column(name = "delivery_fee")
    private BigDecimal deliveryFee;

    @Column(name = "total")
    private BigDecimal total;

    @Column(name = "delivery_address")
    private String deliveryAddress;

    @Column(name = "delivery_notes")
    private String deliveryNotes;

    @Column(name = "status")
    private String status;

    // Milestone 3: the JSON we intend to publish to Kafka, saved in the
    // same transaction as this row. Lets the reconciliation job re-publish
    // if the original Kafka send never happened — see OrderService.
    @Column(name = "pending_payload")
    private String pendingPayload;

    // Milestone 3: when Kafka acknowledged this order's event (acks=all).
    // NULL means the publish was never confirmed — the only case the
    // reconciliation job needs to act on. Written by a targeted UPDATE
    // (OrderRepository.markPublished), never through this entity:
    // insertable/updatable = false stops Hibernate from writing it on a
    // normal save(), so the consumer saving a CONFIRMED order can't
    // accidentally reset it to NULL with a stale value.
    @Column(name = "published_at", insertable = false, updatable = false)
    private OffsetDateTime publishedAt;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.status == null) {
            this.status = "PENDING";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }
}