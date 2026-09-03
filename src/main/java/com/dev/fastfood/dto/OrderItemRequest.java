package com.dev.fastfood.dto;

import java.util.UUID;

// One line item inside an incoming order request.
// Notice: NO price field here. We never trust a price from the client —
// we'll look up the real price ourselves, from our own database.
public class OrderItemRequest {

    private UUID menuItemId;
    private Integer quantity;
    private String specialInstructions;

    public UUID getMenuItemId() { return menuItemId; }
    public Integer getQuantity() { return quantity; }
    public String getSpecialInstructions() { return specialInstructions; }

    public void setMenuItemId(UUID menuItemId) { this.menuItemId = menuItemId; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public void setSpecialInstructions(String specialInstructions) { this.specialInstructions = specialInstructions; }
}