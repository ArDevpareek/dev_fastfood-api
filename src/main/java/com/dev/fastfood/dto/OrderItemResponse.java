package com.dev.fastfood.dto;

import com.dev.fastfood.entity.OrderItem;
import java.math.BigDecimal;
import java.util.UUID;

public class OrderItemResponse {

    private UUID menuItemId;
    private String menuItemName;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal totalPrice;

    public static OrderItemResponse from(OrderItem item) {
        OrderItemResponse response = new OrderItemResponse();
        response.menuItemId = item.getMenuItem().getId();
        response.menuItemName = item.getMenuItem().getName();
        response.quantity = item.getQuantity();
        response.unitPrice = item.getUnitPrice();
        response.totalPrice = item.getTotalPrice();
        return response;
    }

    public UUID getMenuItemId() { return menuItemId; }
    public String getMenuItemName() { return menuItemName; }
    public Integer getQuantity() { return quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public BigDecimal getTotalPrice() { return totalPrice; }
}