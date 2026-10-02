package com.dev.fastfood.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public class CreateOrderRequest {

    @NotNull(message = "userId is required")
    private UUID userId;

    @NotNull(message = "restaurantId is required")
    private UUID restaurantId;

    @NotBlank(message = "deliveryAddress is required")
    @Size(max = 255, message = "deliveryAddress must be at most 255 characters")
    private String deliveryAddress;

    @Size(max = 255, message = "deliveryNotes must be at most 255 characters")
    private String deliveryNotes;

    // @Valid on the type argument (not the List itself) cascades validation
    // into each OrderItemRequest (e.g. its quantity cap) — the modern form;
    // annotating the container directly still works but is deprecated.
    @NotEmpty(message = "items must contain at least one item")
    private List<@Valid OrderItemRequest> items;

    public UUID getUserId() { return userId; }
    public UUID getRestaurantId() { return restaurantId; }
    public String getDeliveryAddress() { return deliveryAddress; }
    public String getDeliveryNotes() { return deliveryNotes; }
    public List<OrderItemRequest> getItems() { return items; }

    public void setUserId(UUID userId) { this.userId = userId; }
    public void setRestaurantId(UUID restaurantId) { this.restaurantId = restaurantId; }
    public void setDeliveryAddress(String deliveryAddress) { this.deliveryAddress = deliveryAddress; }
    public void setDeliveryNotes(String deliveryNotes) { this.deliveryNotes = deliveryNotes; }
    public void setItems(List<OrderItemRequest> items) { this.items = items; }
}
