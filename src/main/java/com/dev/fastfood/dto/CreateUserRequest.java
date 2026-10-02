package com.dev.fastfood.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// This describes exactly what we expect someone to send us
// when creating a new user.
public class CreateUserRequest {

    @NotBlank(message = "email is required")
    @Email(message = "email must be a valid email address")
    private String email;

    @NotBlank(message = "password is required")
    @Size(min = 8, message = "password must be at least 8 characters long")
    private String password;

    @NotBlank(message = "firstName is required")
    @Size(max = 255, message = "firstName must be at most 255 characters")
    private String firstName;

    @Size(max = 255, message = "lastName must be at most 255 characters")
    private String lastName;

    @Size(max = 20, message = "phone must be at most 20 characters")
    private String phone;

    // Getters — Spring needs these to read the incoming JSON values.
    public String getEmail() { return email; }
    public String getPassword() { return password; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getPhone() { return phone; }

    // Setters — Spring needs these to fill in the values from JSON.
    public void setEmail(String email) { this.email = email; }
    public void setPassword(String password) { this.password = password; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public void setPhone(String phone) { this.phone = phone; }
}
