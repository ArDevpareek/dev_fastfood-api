package com.dev.fastfood.repository;

import com.dev.fastfood.entity.Restaurant;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface RestaurantRepository extends JpaRepository<Restaurant, UUID> {

    // @Lock(PESSIMISTIC_WRITE) means: when this query fetches a restaurant,
    // also tell the database "lock this row — nobody else can change it
    // until my current transaction finishes."
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Restaurant r WHERE r.id = :id")
    Optional<Restaurant> findByIdForUpdate(@Param("id") UUID id);
}