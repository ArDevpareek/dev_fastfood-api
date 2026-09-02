package com.dev.fastfood.dto;

import com.dev.fastfood.entity.User;
import java.time.OffsetDateTime;
import java.util.UUID;

// This describes exactly what we send BACK. Notice: no password
// field at all, hashed or otherwise. It simply doesn't exist here.
public class UserResponse {

    private UUID id;
    private String email;
    private String firstName;
    private String lastName;
    private String phone;
    private String status;
    private OffsetDateTime createdAt;

    // A shortcut method: hand it a real User (from the database),
    // it builds the safe, public-facing version for us.
    public static UserResponse from(User user) {
        UserResponse response = new UserResponse();
        response.id = user.getId();
        response.email = user.getEmail();
        response.firstName = user.getFirstName();
        response.lastName = user.getLastName();
        response.phone = user.getPhone();
        response.status = user.getStatus();
        response.createdAt = user.getCreatedAt();
        return response;
    }

    // Getters only — nothing external needs to change this object.
    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getPhone() { return phone; }
    public String getStatus() { return status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}