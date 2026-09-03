package com.dev.fastfood.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "order_items")
@Getter
@Setter
@NoArgsConstructor
public class OrderItem {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    // Every order item belongs to exactly one order.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    // And it points at exactly one menu item — which is HOW we'll
    // know what was actually ordered, and its real price.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "menu_item_id", nullable = false)
    private MenuItem menuItem;

    @Column(name = "quantity")
    private Integer quantity;

    // unitPrice = the price of ONE of this item, copied from the
    // menu item's real price in the database — never trusted from
    // whatever the client sends us. More on this in Step 5.
    @Column(name = "unit_price")
    private BigDecimal unitPrice;

    // totalPrice = unitPrice × quantity, for this one line
    @Column(name = "total_price")
    private BigDecimal totalPrice;

    @Column(name = "special_instructions")
    private String specialInstructions;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }
}