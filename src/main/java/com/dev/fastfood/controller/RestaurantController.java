package com.dev.fastfood.controller;

import com.dev.fastfood.entity.Restaurant;
import com.dev.fastfood.service.RestaurantService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

// @RestController = "this class answers web requests and sends back JSON"
@RestController
// @RequestMapping sets a shared starting path for every method below.
// So every endpoint here automatically starts with /restaurants
@RequestMapping("/restaurants")
public class RestaurantController {

    // Same pattern as before — Spring hands us a working RestaurantService,
    // we don't create it ourselves.
    private final RestaurantService restaurantService;

    public RestaurantController(RestaurantService restaurantService) {
        this.restaurantService = restaurantService;
    }

    // Handles: GET /restaurants
    // (no extra path after /restaurants, so this method runs)
    @GetMapping
    public List<Restaurant> getAllRestaurants() {
        return restaurantService.getAllRestaurants();
    }

    // Handles: GET /restaurants/{restaurantId}
    // e.g. GET /restaurants/abc-123
    @GetMapping("/{restaurantId}")
    public Restaurant getRestaurantById(@PathVariable UUID restaurantId) {
        // @PathVariable grabs the value from the URL itself
        // (the "abc-123" part) and hands it to us as a UUID
        return restaurantService.getRestaurantById(restaurantId);
    }
}