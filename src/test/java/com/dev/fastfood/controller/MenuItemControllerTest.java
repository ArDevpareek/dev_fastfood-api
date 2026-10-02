package com.dev.fastfood.controller;

import com.dev.fastfood.entity.MenuItem;
import com.dev.fastfood.entity.Restaurant;
import com.dev.fastfood.service.MenuItemService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MenuItemController.class)
@AutoConfigureMockMvc(addFilters = false)
class MenuItemControllerTest {

    @org.springframework.beans.factory.annotation.Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CacheManager cacheManager; // satisfies @EnableCaching's aspect, unused by this slice

    @MockitoBean
    private MenuItemService menuItemService;

    @Test
    void addMenuItem_blankNameAndNegativePrice_returns400WithFieldErrors() throws Exception {
        String body = """
                {
                  "name": "",
                  "price": -5
                }
                """;

        mockMvc.perform(post("/restaurants/" + UUID.randomUUID() + "/menu-items")
                        .contentType("application/json").content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists())
                .andExpect(jsonPath("$.fieldErrors.price").exists());
    }

    @Test
    void addMenuItem_missingPrice_returns400() throws Exception {
        String body = """
                {
                  "name": "Butter Chicken"
                }
                """;

        mockMvc.perform(post("/restaurants/" + UUID.randomUUID() + "/menu-items")
                        .contentType("application/json").content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.price").exists());
    }

    @Test
    void addMenuItem_negativeCalories_returns400() throws Exception {
        String body = """
                {
                  "name": "Butter Chicken",
                  "price": 320.00,
                  "calories": -10
                }
                """;

        mockMvc.perform(post("/restaurants/" + UUID.randomUUID() + "/menu-items")
                        .contentType("application/json").content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.calories").exists());
    }

    @Test
    void addMenuItem_validRequest_returns201() throws Exception {
        Restaurant restaurant = new Restaurant();
        restaurant.setId(UUID.randomUUID());

        MenuItem saved = new MenuItem();
        saved.setId(UUID.randomUUID());
        saved.setRestaurant(restaurant);
        saved.setName("Butter Chicken");
        saved.setPrice(new BigDecimal("320.00"));
        saved.setIsAvailable(true);

        when(menuItemService.addMenuItem(any(), any(), any(), any(), any())).thenReturn(saved);

        String body = """
                {
                  "name": "Butter Chicken",
                  "price": 320.00,
                  "calories": 550
                }
                """;

        mockMvc.perform(post("/restaurants/" + restaurant.getId() + "/menu-items")
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Butter Chicken"));
    }
}
