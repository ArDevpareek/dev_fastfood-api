package com.dev.fastfood.repository;

import com.dev.fastfood.entity.MenuItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MenuItemRepository extends JpaRepository<MenuItem, UUID> {

    // Free method — Spring reads this name and builds the query itself:
    // "find every menu item where the restaurant's ID matches this one"
    List<MenuItem> findByRestaurantId(UUID restaurantId);
}