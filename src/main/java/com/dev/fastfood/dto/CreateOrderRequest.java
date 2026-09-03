package com.dev.fastfood.dto;

import java.util.List;
import java.util.UUID;

public class CreateOrderRequest {

    private UUID userId;
    private UUID restaurantId;
    private String deliveryAddress;
    private String deliveryNotes;
    private List<OrderItemRequest> items;

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