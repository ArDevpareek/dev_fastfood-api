package com.dev.fastfood.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "menu_items")
@Getter
@Setter
@NoArgsConstructor
public class MenuItem {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    // THIS is the new part — a relationship to another table.
    //
    // @ManyToOne means: MANY menu items can belong to ONE restaurant.
    // fetch = FetchType.LAZY means: don't automatically load the full
    // restaurant every time you load a menu item — only load it if
    // you actually ask for it. This keeps things fast.
    @ManyToOne(fetch = FetchType.LAZY)
    // @JoinColumn says which actual database column holds the link —
    // in your table, that's the "restaurant_id" column.
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "price", nullable = false)
    private BigDecimal price;

    @Column(name = "calories")
    private Integer calories;

    @Column(name = "is_available")
    private Boolean isAvailable;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.isAvailable == null) {
            this.isAvailable = true;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }
}