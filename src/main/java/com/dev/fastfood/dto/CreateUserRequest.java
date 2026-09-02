package com.dev.fastfood.dto;

// This describes exactly what we expect someone to send us
// when creating a new user. Just 5 plain fields, nothing fancy yet
// (we'll add proper validation like "email must be valid" on a later day).
public class CreateUserRequest {

    private String email;
    private String password;
    private String firstName;
    private String lastName;
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