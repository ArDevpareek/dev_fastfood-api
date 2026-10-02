package com.dev.fastfood.controller;

import com.dev.fastfood.dto.CreateOrderRequest;
import com.dev.fastfood.dto.OrderResponse;
import com.dev.fastfood.service.OrderService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/order")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    // Handles: POST /order
    // Milestone 3: this now returns as soon as validation passes and the
    // order is saved as PENDING — the actual save of order_items happens
    // later, off the request thread, via Kafka. 202 Accepted (not 201
    // Created) reflects that: "accepted for processing," not "fully done."
    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@RequestBody CreateOrderRequest request) {
        OrderResponse response = orderService.createOrder(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    // Handles: GET /order/{orderId}
    // Milestone 3 addition: since POST /order no longer finishes the work
    // synchronously, this is how a client checks whether an order ended up
    // CONFIRMED or FAILED (or is still PENDING).
    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable UUID orderId) {
        return ResponseEntity.ok(orderService.getOrder(orderId));
    }
}
