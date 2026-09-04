package com.dev.fastfood.repository;

import com.dev.fastfood.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {

    // Free method — used later if we ever need to list items
    // belonging to one specific order.
    List<OrderItem> findByOrderId(UUID orderId);
}