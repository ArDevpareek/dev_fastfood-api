package com.dev.fastfood.entity;

// These imports bring in tools we need — think of them as borrowing
// pre-built pieces instead of writing everything from scratch
import jakarta.persistence.*;              // lets Java classes map to database tables
import lombok.Getter;                       // auto-writes getX() methods for us
import lombok.Setter;                       // auto-writes setX() methods for us
import lombok.NoArgsConstructor;            // auto-writes an empty constructor

import java.math.BigDecimal;                // the correct type for money — never use double
import java.time.OffsetDateTime;            // the correct type for timestamps
import java.util.UUID;                      // the correct type for our IDs

// @Entity = "this class represents one row in a database table"
@Entity
// @Table tells it exactly which table — must match your init.sql table name
@Table(name = "restaurants")
// These three save you from writing getters/setters/empty-constructor by hand
@Getter
@Setter
@NoArgsConstructor
public class Restaurant {

    // @Id marks this field as the primary key (the unique ID for each row)
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    // @Column maps this Java field to a specific database column.
    // nullable = false means: this can never be empty in the database.
    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "cuisine")
    private String cuisine;

    // BigDecimal is used here instead of double, because double loses
    // precision with money (e.g. 0.1 + 0.2 doesn't equal exactly 0.3 in double)
    @Column(name = "delivery_fee")
    private BigDecimal deliveryFee;

    @Column(name = "min_order_amount")
    private BigDecimal minOrderAmount;

    @Column(name = "is_active")
    private Boolean isActive;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    // This method runs automatically, right before this row is first saved.
    // We use it to fill in "createdAt", "updatedAt", and "isActive"
    // ourselves, instead of trusting the database to do it.
    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.isActive == null) {
            this.isActive = true;
        }
    }

    // This method runs automatically every time this row gets updated later.
    // It just refreshes the "last updated" timestamp.
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }
}