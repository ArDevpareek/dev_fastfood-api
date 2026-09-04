package com.dev.fastfood.dto;

import com.dev.fastfood.entity.Order;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public class OrderResponse {

    private UUID id;
    private UUID userId;
    private UUID restaurantId;
    private BigDecimal subtotal;
    private BigDecimal deliveryFee;
    private BigDecimal total;
    private String status;
    private List<OrderItemResponse> items;

    // Notice this version of "from" takes the order AND the items separately.
    // That's because Order doesn't hold its items directly in our design —
    // we'll fetch them separately in the service, next step.
    public static OrderResponse from(Order order, List<OrderItemResponse> items) {
        OrderResponse response = new OrderResponse();
        response.id = order.getId();
        response.userId = order.getUser().getId();
        response.restaurantId = order.getRestaurant().getId();
        response.subtotal = order.getSubtotal();
        response.deliveryFee = order.getDeliveryFee();
        response.total = order.getTotal();
        response.status = order.getStatus();
        response.items = items;
        return response;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public UUID getRestaurantId() { return restaurantId; }
    public BigDecimal getSubtotal() { return subtotal; }
    public BigDecimal getDeliveryFee() { return deliveryFee; }
    public BigDecimal getTotal() { return total; }
    public String getStatus() { return status; }
    public List<OrderItemResponse> getItems() { return items; }
}