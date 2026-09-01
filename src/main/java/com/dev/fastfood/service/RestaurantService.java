package com.dev.fastfood.service;

import com.dev.fastfood.entity.Restaurant;
import com.dev.fastfood.repository.RestaurantRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

// @Service = "this class holds business logic" — Spring creates
// one instance of it automatically and manages it for us.
@Service
public class RestaurantService {

    // This is the repository we built in Step 2. We "inject" it here —
    // meaning Spring hands us a working copy automatically, we don't
    // create it ourselves with "new".
    private final RestaurantRepository restaurantRepository;

    // This constructor is how Spring hands us that repository.
    // Writing it this way (instead of "new RestaurantRepository()")
    // is the standard, correct way to do it.
    public RestaurantService(RestaurantRepository restaurantRepository) {
        this.restaurantRepository = restaurantRepository;
    }

    // Returns every restaurant in the database.
    // findAll() is one of the free methods JpaRepository gave us.
    public List<Restaurant> getAllRestaurants() {
        return restaurantRepository.findAll();
    }

    // Returns ONE restaurant, found by its ID.
    public Restaurant getRestaurantById(UUID restaurantId) {
        // findById returns something called an "Optional" — think of it
        // as a box that might be empty or might have a restaurant inside.
        // orElseThrow says: "if the box is empty, crash with this error
        // message instead of silently returning nothing."
        return restaurantRepository.findById(restaurantId)
                .orElseThrow(() ->
                        new RuntimeException("Restaurant not found: " + restaurantId));
    }
}