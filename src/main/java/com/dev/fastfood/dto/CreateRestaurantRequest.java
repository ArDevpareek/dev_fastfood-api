package com.dev.fastfood.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

// What we expect someone to send when creating a restaurant.
// No "id", "isActive", "createdAt" etc. here — those are either
// generated or defaulted by the entity itself, never trusted from a client.
public class CreateRestaurantRequest {

    @NotBlank(message = "name is required")
    @Size(max = 255, message = "name must be at most 255 characters")
    private String name;

    private String description;

    @Size(max = 255, message = "cuisine must be at most 255 characters")
    private String cuisine;

    @NotNull(message = "deliveryFee is required")
    @DecimalMin(value = "0.0", message = "deliveryFee cannot be negative")
    private BigDecimal deliveryFee;

    @NotNull(message = "minOrderAmount is required")
    @DecimalMin(value = "0.0", message = "minOrderAmount cannot be negative")
    private BigDecimal minOrderAmount;

    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getCuisine() { return cuisine; }
    public BigDecimal getDeliveryFee() { return deliveryFee; }
    public BigDecimal getMinOrderAmount() { return minOrderAmount; }

    public void setName(String name) { this.name = name; }
    public void setDescription(String description) { this.description = description; }
    public void setCuisine(String cuisine) { this.cuisine = cuisine; }
    public void setDeliveryFee(BigDecimal deliveryFee) { this.deliveryFee = deliveryFee; }
    public void setMinOrderAmount(BigDecimal minOrderAmount) { this.minOrderAmount = minOrderAmount; }
}
