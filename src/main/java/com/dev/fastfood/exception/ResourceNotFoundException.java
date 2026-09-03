package com.dev.fastfood.exception;

// Used when something with a given ID doesn't exist —
// e.g. a restaurant ID, user ID, or menu item ID that's not real.
// This will map to a 404 status code.
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}