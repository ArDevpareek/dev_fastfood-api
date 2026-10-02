package com.dev.fastfood.controller;

import com.dev.fastfood.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
@AutoConfigureMockMvc(addFilters = false)
class OrderControllerTest {

    @org.springframework.beans.factory.annotation.Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CacheManager cacheManager; // satisfies @EnableCaching's aspect, unused by this slice

    @MockitoBean
    private OrderService orderService;

    // The original bug: quantity 400,000 overflowed orders.total
    // (NUMERIC(10,2)) deep inside the service and came back as a raw 500.
    // It must now be rejected by bean validation before OrderService
    // is ever called, via a clean 400.
    @Test
    void createOrder_quantity400000_returns400AndNeverReachesService() throws Exception {
        String body = """
                {
                  "userId": "%s",
                  "restaurantId": "%s",
                  "deliveryAddress": "12 Civil Lines, Roorkee",
                  "items": [
                    { "menuItemId": "%s", "quantity": 400000 }
                  ]
                }
                """.formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

        mockMvc.perform(post("/order").contentType("application/json").content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors['items[0].quantity']").exists());

        verify(orderService, never()).createOrder(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void createOrder_zeroQuantity_returns400() throws Exception {
        String body = """
                {
                  "userId": "%s",
                  "restaurantId": "%s",
                  "deliveryAddress": "12 Civil Lines, Roorkee",
                  "items": [
                    { "menuItemId": "%s", "quantity": 0 }
                  ]
                }
                """.formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

        mockMvc.perform(post("/order").contentType("application/json").content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors['items[0].quantity']").exists());
    }

    @Test
    void createOrder_missingUserIdAndEmptyItems_returns400WithBothFieldErrors() throws Exception {
        String body = """
                {
                  "restaurantId": "%s",
                  "deliveryAddress": "12 Civil Lines, Roorkee",
                  "items": []
                }
                """.formatted(UUID.randomUUID());

        mockMvc.perform(post("/order").contentType("application/json").content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.userId").exists())
                .andExpect(jsonPath("$.fieldErrors.items").exists());
    }

    @Test
    void createOrder_blankDeliveryAddress_returns400() throws Exception {
        String body = """
                {
                  "userId": "%s",
                  "restaurantId": "%s",
                  "deliveryAddress": "",
                  "items": [
                    { "menuItemId": "%s", "quantity": 1 }
                  ]
                }
                """.formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

        mockMvc.perform(post("/order").contentType("application/json").content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.deliveryAddress").exists());
    }
}
