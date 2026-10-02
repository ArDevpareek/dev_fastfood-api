package com.dev.fastfood.messaging;

import com.dev.fastfood.dto.OrderItemRequest;

import java.util.List;
import java.util.UUID;

// This is the message body published to Kafka when a POST /order request
// passes synchronous validation. It carries everything the consumer needs
// to do its own authoritative (locked) re-check and save — the consumer
// never trusts prices or availability from this event, only IDs and
// quantities, same "never trust the request" rule as the old synchronous
// flow.
public class OrderCreatedEvent {

    private UUID orderId;
    private UUID userId;
    private UUID restaurantId;
    private String deliveryAddress;
    private String deliveryNotes;
    private List<OrderItemRequest> items;

    // Kafka's JSON (de)serializer needs a no-arg constructor to build an
    // empty instance before filling it in field by field.
    public OrderCreatedEvent() {
    }

    public OrderCreatedEvent(UUID orderId, UUID userId, UUID restaurantId,
                              String deliveryAddress, String deliveryNotes,
                              List<OrderItemRequest> items) {
        this.orderId = orderId;
        this.userId = userId;
        this.restaurantId = restaurantId;
        this.deliveryAddress = deliveryAddress;
        this.deliveryNotes = deliveryNotes;
        this.items = items;
    }

    public UUID getOrderId() { return orderId; }
    public UUID getUserId() { return userId; }
    public UUID getRestaurantId() { return restaurantId; }
    public String getDeliveryAddress() { return deliveryAddress; }
    public String getDeliveryNotes() { return deliveryNotes; }
    public List<OrderItemRequest> getItems() { return items; }

    public void setOrderId(UUID orderId) { this.orderId = orderId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public void setRestaurantId(UUID restaurantId) { this.restaurantId = restaurantId; }
    public void setDeliveryAddress(String deliveryAddress) { this.deliveryAddress = deliveryAddress; }
    public void setDeliveryNotes(String deliveryNotes) { this.deliveryNotes = deliveryNotes; }
    public void setItems(List<OrderItemRequest> items) { this.items = items; }
}
