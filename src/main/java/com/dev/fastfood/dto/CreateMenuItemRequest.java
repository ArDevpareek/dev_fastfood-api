package com.dev.fastfood.dto;

import java.math.BigDecimal;

// What we expect someone to send when adding a menu item.
// Notice: no restaurantId field here — that comes from the URL itself,
// not the request body (you'll see this in the controller next).
public class CreateMenuItemRequest {

    private String name;
    private String description;
    private BigDecimal price;
    private Integer calories;

    public String getName() { return name; }
    public String getDescription() { return description; }
    public BigDecimal getPrice() { return price; }
    public Integer getCalories() { return calories; }

    public void setName(String name) { this.name = name; }
    public void setDescription(String description) { this.description = description; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public void setCalories(Integer calories) { this.calories = calories; }
}