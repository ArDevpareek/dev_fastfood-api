package com.dev.fastfood.dto;

import com.dev.fastfood.entity.MenuItem;
import java.math.BigDecimal;
import java.util.UUID;

// What we send back. Notice: we include restaurantId (just the ID,
// not the whole Restaurant object) — sending the full nested Restaurant
// back would be messy and unnecessary here.
public class MenuItemResponse {

    private UUID id;
    private UUID restaurantId;
    private String name;
    private String description;
    private BigDecimal price;
    private Integer calories;
    private Boolean isAvailable;

    public static MenuItemResponse from(MenuItem menuItem) {
        MenuItemResponse response = new MenuItemResponse();
        response.id = menuItem.getId();
        // .getRestaurant().getId() reaches through the relationship
        // we built in Step 1, and grabs just the restaurant's ID.
        response.restaurantId = menuItem.getRestaurant().getId();
        response.name = menuItem.getName();
        response.description = menuItem.getDescription();
        response.price = menuItem.getPrice();
        response.calories = menuItem.getCalories();
        response.isAvailable = menuItem.getIsAvailable();
        return response;
    }

    public UUID getId() { return id; }
    public UUID getRestaurantId() { return restaurantId; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public BigDecimal getPrice() { return price; }
    public Integer getCalories() { return calories; }
    public Boolean getIsAvailable() { return isAvailable; }
}