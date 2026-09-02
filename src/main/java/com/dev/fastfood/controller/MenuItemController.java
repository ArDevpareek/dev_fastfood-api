package com.dev.fastfood.controller;

import com.dev.fastfood.dto.CreateMenuItemRequest;
import com.dev.fastfood.dto.MenuItemResponse;
import com.dev.fastfood.entity.MenuItem;
import com.dev.fastfood.service.MenuItemService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
public class MenuItemController {

    private final MenuItemService menuItemService;

    public MenuItemController(MenuItemService menuItemService) {
        this.menuItemService = menuItemService;
    }

    // Handles: POST /restaurants/{restaurantId}/menu-items
    // @PathVariable grabs restaurantId straight from the URL itself.
    @PostMapping("/restaurants/{restaurantId}/menu-items")
    public ResponseEntity<MenuItemResponse> addMenuItem(
            @PathVariable UUID restaurantId,
            @RequestBody CreateMenuItemRequest request) {

        MenuItem created = menuItemService.addMenuItem(
                restaurantId,
                request.getName(),
                request.getDescription(),
                request.getPrice(),
                request.getCalories()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(MenuItemResponse.from(created));
    }

    // Handles: GET /menu?restaurantId=...
    // @RequestParam reads a query parameter (the part after the ? in a URL).
    // @Transactional here matters: MenuItemResponse.from() reaches into
    // menuItem.getRestaurant() — since Restaurant is LAZY-loaded, that
    // only works while we're still inside an active transaction.
    @GetMapping("/menu")
    @Transactional(readOnly = true)
    public List<MenuItemResponse> getMenu(@RequestParam UUID restaurantId) {
        return menuItemService.getMenuByRestaurant(restaurantId)
                .stream()
                .map(MenuItemResponse::from)
                .toList();
    }
}