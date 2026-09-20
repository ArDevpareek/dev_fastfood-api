package com.dev.fastfood.repository;

import com.dev.fastfood.entity.MenuItem;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface MenuItemRepository extends JpaRepository<MenuItem, UUID> {

    // "join fetch" loads the linked Restaurant in the SAME query, instead
    // of leaving it as a lazy Hibernate proxy. This matters specifically
    // because this method's result gets cached (see MenuItemService):
    // a lazy proxy serializes to JSON with an internal
    // "hibernateLazyInitializer" field that Jackson can't read back on a
    // cache hit, which broke every second call with a 500. A real,
    // already-loaded Restaurant object serializes and deserializes cleanly.
    @Query("SELECT m FROM MenuItem m JOIN FETCH m.restaurant WHERE m.restaurant.id = :restaurantId")
    List<MenuItem> findByRestaurantId(@Param("restaurantId") UUID restaurantId);

    // Same idea, but for multiple menu items at once — locks every
    // matching row so none of them can change while we're deciding.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM MenuItem m WHERE m.id IN :ids")
    List<MenuItem> findAllByIdForUpdate(@Param("ids") List<UUID> ids);
}