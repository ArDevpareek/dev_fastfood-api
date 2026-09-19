package com.dev.fastfood.service;

import com.dev.fastfood.dto.CreateOrderRequest;
import com.dev.fastfood.dto.OrderItemRequest;
import com.dev.fastfood.dto.OrderItemResponse;
import com.dev.fastfood.dto.OrderResponse;
import com.dev.fastfood.entity.*;
import com.dev.fastfood.exception.BusinessRuleException;
import com.dev.fastfood.exception.ResourceNotFoundException;
import com.dev.fastfood.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final UserRepository userRepository;
    private final RestaurantRepository restaurantRepository;
    private final MenuItemRepository menuItemRepository;

    public OrderService(OrderRepository orderRepository,
                        OrderItemRepository orderItemRepository,
                        UserRepository userRepository,
                        RestaurantRepository restaurantRepository,
                        MenuItemRepository menuItemRepository) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.userRepository = userRepository;
        this.restaurantRepository = restaurantRepository;
        this.menuItemRepository = menuItemRepository;
    }

    // @Transactional here means: every database write in this method
    // succeeds together, or NONE of them do. If something fails halfway,
    // nothing gets left behind — no orphaned order with no items.
    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {

        // ---- CHECK 1: does the user exist? ----
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found: " + request.getUserId()));

        // ---- CHECK 2: does the restaurant exist? ----
        Restaurant restaurant = restaurantRepository.findByIdForUpdate(request.getRestaurantId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Restaurant not found: " + request.getRestaurantId()));

        // ---- CHECK 3: is the restaurant currently active? ----
        if (!Boolean.TRUE.equals(restaurant.getIsActive())) {
            throw new BusinessRuleException(
                    "Restaurant is not currently active: " + restaurant.getId());
        }

        // ---- CHECK 4: was at least one item ordered? ----
        List<OrderItemRequest> requestedItems = request.getItems();
        if (requestedItems == null || requestedItems.isEmpty()) {
            throw new BusinessRuleException("Order must contain at least one item");
        }

        // ---- Fetch ALL requested menu items in ONE query ----
        // (not one query per item — that would be the N+1 problem
        // your reference doc warned about)
        List<UUID> menuItemIds = requestedItems.stream()
                .map(OrderItemRequest::getMenuItemId)
                .collect(Collectors.toList());

        List<MenuItem> foundMenuItems = menuItemRepository.findAllByIdForUpdate(menuItemIds);

        // Turn the list into a Map for fast lookup by ID, like a dictionary.
        Map<UUID, MenuItem> menuItemMap = foundMenuItems.stream()
                .collect(Collectors.toMap(MenuItem::getId, item -> item));

        // ---- CHECK 5: did every requested menu item actually exist? ----
        if (menuItemMap.size() != menuItemIds.size()) {
            throw new ResourceNotFoundException("One or more menu items were not found");
        }

        // ---- Build each order line, checking rules along the way ----
        BigDecimal subtotal = BigDecimal.ZERO;
        List<OrderItem> orderItemEntities = new ArrayList<>();

        for (OrderItemRequest itemRequest : requestedItems) {
            MenuItem menuItem = menuItemMap.get(itemRequest.getMenuItemId());

            // ---- CHECK 6: does this menu item actually belong to
            // THIS restaurant? Without this check, someone could order
            // a burger from Restaurant A mixed with a dosa from Restaurant B. ----
            if (!menuItem.getRestaurant().getId().equals(restaurant.getId())) {
                throw new BusinessRuleException(
                        "Menu item " + menuItem.getId() + " does not belong to restaurant "
                                + restaurant.getId());
            }

            // ---- CHECK 7: is this item currently available? ----
            if (!Boolean.TRUE.equals(menuItem.getIsAvailable())) {
                throw new BusinessRuleException(
                        "Menu item is not available: " + menuItem.getId());
            }

            Integer quantity = itemRequest.getQuantity();
            if (quantity == null || quantity < 1) {
                throw new BusinessRuleException(
                        "Quantity must be at least 1 for item: " + menuItem.getId());
            }

            // ---- THE MOST IMPORTANT LINE IN THIS WHOLE FILE ----
            // unitPrice comes from menuItem (our own database),
            // NEVER from itemRequest (what the client sent us).
            // This is what stops someone from ordering a ₹400 pizza for ₹1.
            BigDecimal unitPrice = menuItem.getPrice();
            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(quantity));

            subtotal = subtotal.add(lineTotal);

            OrderItem orderItem = new OrderItem();
            orderItem.setId(UUID.randomUUID());
            orderItem.setMenuItem(menuItem);
            orderItem.setQuantity(quantity);
            orderItem.setUnitPrice(unitPrice);
            orderItem.setTotalPrice(lineTotal);
            orderItem.setSpecialInstructions(itemRequest.getSpecialInstructions());
            orderItemEntities.add(orderItem);
        }

        // ---- CHECK 8: does this order meet the restaurant's minimum? ----
        // compareTo, NEVER equals(), for comparing BigDecimal values.
        if (subtotal.compareTo(restaurant.getMinOrderAmount()) < 0) {
            throw new BusinessRuleException(
                    "Order subtotal " + subtotal + " is below the minimum of "
                            + restaurant.getMinOrderAmount());
        }

        // ---- Build and save the Order itself ----
        BigDecimal deliveryFee = restaurant.getDeliveryFee();
        BigDecimal total = subtotal.add(deliveryFee);

        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setUser(user);
        order.setRestaurant(restaurant);
        order.setSubtotal(subtotal);
        order.setDeliveryFee(deliveryFee);
        order.setTotal(total);
        order.setDeliveryAddress(request.getDeliveryAddress());
        order.setDeliveryNotes(request.getDeliveryNotes());

        Order savedOrder = orderRepository.save(order);

        // ---- Now link every order item to the saved order, and save them ----
        for (OrderItem item : orderItemEntities) {
            item.setOrder(savedOrder);
        }
        List<OrderItem> savedItems = orderItemRepository.saveAll(orderItemEntities);

        // ---- Build the response ----
        List<OrderItemResponse> itemResponses = savedItems.stream()
                .map(OrderItemResponse::from)
                .collect(Collectors.toList());

        return OrderResponse.from(savedOrder, itemResponses);
    }
}