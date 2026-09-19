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

    List<MenuItem> findByRestaurantId(UUID restaurantId);

    // Same idea, but for multiple menu items at once — locks every
    // matching row so none of them can change while we're deciding.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM MenuItem m WHERE m.id IN :ids")
    List<MenuItem> findAllByIdForUpdate(@Param("ids") List<UUID> ids);
}