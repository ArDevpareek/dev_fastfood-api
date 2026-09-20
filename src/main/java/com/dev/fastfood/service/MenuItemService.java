package com.dev.fastfood.service;

import com.dev.fastfood.entity.MenuItem;
import com.dev.fastfood.entity.Restaurant;
import com.dev.fastfood.repository.MenuItemRepository;
import com.dev.fastfood.repository.RestaurantRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class MenuItemService {

    private final MenuItemRepository menuItemRepository;

    // We ALSO need the restaurant repository here — because before adding
    // a menu item, we must check the restaurant it belongs to actually exists.
    private final RestaurantRepository restaurantRepository;

    public MenuItemService(MenuItemRepository menuItemRepository,
                           RestaurantRepository restaurantRepository) {
        this.menuItemRepository = menuItemRepository;
        this.restaurantRepository = restaurantRepository;
    }

    // Evicts this restaurant's cached menu so the new item shows up on the
    // very next GET /menu call, instead of waiting for the 2-minute TTL to
    // expire. We only evict THIS restaurant's entry (key = restaurantId),
    // not the whole "menus" cache — other restaurants' cached menus are
    // untouched and still valid.
    @CacheEvict(value = "menus", key = "#restaurantId")
    public MenuItem addMenuItem(UUID restaurantId, String name, String description,
                                BigDecimal price, Integer calories) {

        // Step 1: check the restaurant actually exists.
        // If not, "box is empty" — throw an error (rough version for now,
        // same as before — Day 4 will make this a clean 404).
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() ->
                        new RuntimeException("Restaurant not found: " + restaurantId));

        // Step 2: build the new menu item, and LINK it to that restaurant
        // by setting the whole Restaurant object (not just an ID).
        MenuItem menuItem = new MenuItem();
        menuItem.setId(UUID.randomUUID());
        menuItem.setRestaurant(restaurant);
        menuItem.setName(name);
        menuItem.setDescription(description);
        menuItem.setPrice(price);
        menuItem.setCalories(calories);

        return menuItemRepository.save(menuItem);
    }

    // Powers GET /menu?restaurantId=
    // key = "#restaurantId" means each restaurant gets its own cache entry
    // (instead of one shared entry for every call to this method).
    @Cacheable(value = "menus", key = "#restaurantId")
    public List<MenuItem> getMenuByRestaurant(UUID restaurantId) {
        return menuItemRepository.findByRestaurantId(restaurantId);
    }
}