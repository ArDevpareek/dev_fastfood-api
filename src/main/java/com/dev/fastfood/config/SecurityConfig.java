package com.dev.fastfood.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

// @Configuration = "this class sets up some app-wide settings"
@Configuration
public class SecurityConfig {

    // This method tells Spring Security: "let every request through,
    // don't ask for a login." We're not building authentication in
    // this project — we only added this tool for password hashing.
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    // This is the actual hashing tool. We make it available once here,
    // so any part of our app (like UserService, coming next) can use it.
    @Bean
    public org.springframework.security.crypto.password.PasswordEncoder passwordEncoder() {
        return new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
    }
}