package com.dev.fastfood.service;

import com.dev.fastfood.entity.Restaurant;
import com.dev.fastfood.repository.RestaurantRepository;
import org.springframework.stereotype.Service;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;

import java.math.BigDecimal;
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
    //
    // @Cacheable belongs HERE, on the method — not on the class. Putting it
    // on the class (as it was before) applies it to every public method,
    // including getRestaurantById below, which would start caching single
    // restaurants under this same cache without us intending it to.
    @Cacheable("restaurants")
    public List<Restaurant> getAllRestaurants() {
        return restaurantRepository.findAll();
    }

    // Creates a new restaurant. allEntries = true, not a key, because the
    // "restaurants" cache holds exactly one entry — the full list, under
    // getAllRestaurants()'s own (argument-less) key — which doesn't match
    // any key we could compute from this method's arguments. Evicting that
    // one entry means the very next GET /restaurants re-reads from Postgres
    // and includes the new restaurant, instead of waiting out the 10-minute TTL.
    @CacheEvict(value = "restaurants", allEntries = true)
    public Restaurant createRestaurant(String name, String description, String cuisine,
                                       BigDecimal deliveryFee, BigDecimal minOrderAmount) {
        Restaurant restaurant = new Restaurant();
        restaurant.setId(UUID.randomUUID());
        restaurant.setName(name);
        restaurant.setDescription(description);
        restaurant.setCuisine(cuisine);
        restaurant.setDeliveryFee(deliveryFee);
        restaurant.setMinOrderAmount(minOrderAmount);
        // isActive/createdAt/updatedAt are filled in by Restaurant's own
        // @PrePersist — same as every other entity in this codebase.
        return restaurantRepository.save(restaurant);
    }

    // Returns ONE restaurant, found by its ID.
    public Restaurant getRestaurantById(UUID restaurantId) {
        // findById returns something called an "Optional" — think of it
        // as a box that might be empty or might have a restaurant inside.
        // orElseThrow says: "if the box is empty, crash with this error
        // message instead of silently returning nothing."
        return restaurantRepository.findById(restaurantId)
                .orElseThrow(() ->
                        new com.dev.fastfood.exception.ResourceNotFoundException("Restaurant not found: " + restaurantId));
    }
}