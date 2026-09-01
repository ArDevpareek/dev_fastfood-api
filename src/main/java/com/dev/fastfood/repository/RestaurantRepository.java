package com.dev.fastfood.repository;

import com.dev.fastfood.entity.Restaurant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

    // This interface gives you free database operations — you write NO code
// inside it, Spring builds the actual implementation automatically at startup.
//
// JpaRepository<Restaurant, UUID> means:
//   - Restaurant = which table/entity this manages
//   - UUID = the type of that table's ID column
//
// Just by extending this, you instantly get:
//   findAll()      → get every restaurant
//   findById(id)   → get one restaurant by its ID
//   save(entity)   → insert or update a restaurant
//   deleteById(id) → delete a restaurant
// ...and more, for free.
    public interface RestaurantRepository extends JpaRepository<Restaurant, UUID> {
    }

