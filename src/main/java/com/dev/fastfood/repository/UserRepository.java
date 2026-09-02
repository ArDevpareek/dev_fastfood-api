package com.dev.fastfood.repository;

import com.dev.fastfood.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    // Free method Spring builds for us, just from the method name.
    // Checks if any user already has this email — used to block duplicates.
    boolean existsByEmail(String email);

    // Free method to find one user by their email — you'll need this
    // later for login, even though we're not building login today.
    Optional<User> findByEmail(String email);
}