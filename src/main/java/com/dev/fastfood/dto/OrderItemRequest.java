package com.dev.fastfood.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

// One line item inside an incoming order request.
// Notice: NO price field here. We never trust a price from the client —
// we'll look up the real price ourselves, from our own database.
public class OrderItemRequest {

    @NotNull(message = "menuItemId is required")
    private UUID menuItemId;

    // Upper bound closes the gap where quantity: 400000 overflowed
    // orders.total (NUMERIC(10,2)) and surfaced as a raw 500 instead of a
    // 400 — see README Known gaps. 100 is a generous cap for a single
    // menu item in one order; nothing legitimate needs more than that.
    @NotNull(message = "quantity is required")
    @Positive(message = "quantity must be at least 1")
    @Max(value = 100, message = "quantity cannot exceed 100 per item")
    private Integer quantity;

    private String specialInstructions;

    public UUID getMenuItemId() { return menuItemId; }
    public Integer getQuantity() { return quantity; }
    public String getSpecialInstructions() { return specialInstructions; }

    public void setMenuItemId(UUID menuItemId) { this.menuItemId = menuItemId; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public void setSpecialInstructions(String specialInstructions) { this.specialInstructions = specialInstructions; }
}
