package com.dev.fastfood.controller;

import com.dev.fastfood.dto.CreateUserRequest;
import com.dev.fastfood.dto.UserResponse;
import com.dev.fastfood.entity.User;
import com.dev.fastfood.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    // Handles: POST /users
    // @RequestBody means: take the JSON someone sends, and turn it
    // into a CreateUserRequest object automatically.
    @PostMapping
    public ResponseEntity<UserResponse> createUser(@RequestBody CreateUserRequest request) {

        User createdUser = userService.createUser(
                request.getEmail(),
                request.getPassword(),
                request.getFirstName(),
                request.getLastName(),
                request.getPhone()
        );

        // We convert the real User (which has the password hash inside)
        // into the safe UserResponse (which doesn't) before sending it back.
        UserResponse response = UserResponse.from(createdUser);

        // 201 Created is the correct status code for "I made a new thing
        // successfully" — different from 200 OK, which just means "worked".
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}