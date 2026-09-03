package com.dev.fastfood.service;

import com.dev.fastfood.entity.User;
import com.dev.fastfood.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;

    // This is the password-scrambling tool we set up in SecurityConfig.
    // Spring hands us the working copy automatically, same as the repository.
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public User createUser(String email, String rawPassword, String firstName,
                           String lastName, String phone) {

        // Check FIRST, before touching the database with a save.
        // If this email already exists, stop here.
        if (userRepository.existsByEmail(email)) {
            throw new com.dev.fastfood.exception.DuplicateResourceException("Email already in use: " + email);
        }

        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(email);

        // THIS is the important line. We never save rawPassword directly.
        // encode() scrambles it into a one-way hash before it touches the database.
        user.setPasswordHash(passwordEncoder.encode(rawPassword));

        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setPhone(phone);

        return userRepository.save(user);
    }
}