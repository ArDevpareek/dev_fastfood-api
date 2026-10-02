package com.dev.fastfood.controller;

import com.dev.fastfood.entity.User;
import com.dev.fastfood.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// @WebMvcTest loads only the web layer (this controller + Spring MVC infra
// + @RestControllerAdvice beans like GlobalExceptionHandler) — no real
// database, no real UserService. addFilters = false skips the servlet
// filter chain (including Spring Security's) — the app's own security
// config permits everything anyway, so there's nothing meaningful to test
// there, and wiring Security's full auto-config into this slice isn't worth it.
@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
class UserControllerTest {

    @org.springframework.beans.factory.annotation.Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CacheManager cacheManager; // satisfies @EnableCaching's aspect, unused by this slice

    @MockitoBean
    private UserService userService;

    @Test
    void createUser_blankEmailAndShortPassword_returns400WithFieldErrors() throws Exception {
        String body = """
                {
                  "email": "",
                  "password": "short",
                  "firstName": "Priya"
                }
                """;

        mockMvc.perform(post("/users").contentType("application/json").content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
    }

    @Test
    void createUser_invalidEmailFormat_returns400() throws Exception {
        String body = """
                {
                  "email": "not-an-email",
                  "password": "longenoughpassword",
                  "firstName": "Priya"
                }
                """;

        mockMvc.perform(post("/users").contentType("application/json").content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").exists());
    }

    @Test
    void createUser_missingFirstName_returns400() throws Exception {
        String body = """
                {
                  "email": "priya@example.com",
                  "password": "longenoughpassword"
                }
                """;

        mockMvc.perform(post("/users").contentType("application/json").content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.firstName").exists());
    }

    @Test
    void createUser_validRequest_returns201() throws Exception {
        User savedUser = new User();
        savedUser.setId(UUID.randomUUID());
        savedUser.setEmail("priya@example.com");
        savedUser.setFirstName("Priya");
        savedUser.setStatus("ACTIVE");
        when(userService.createUser(any(), any(), any(), any(), any())).thenReturn(savedUser);

        String body = """
                {
                  "email": "priya@example.com",
                  "password": "longenoughpassword",
                  "firstName": "Priya",
                  "lastName": "Sharma",
                  "phone": "9876543210"
                }
                """;

        mockMvc.perform(post("/users").contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("priya@example.com"));
    }
}
