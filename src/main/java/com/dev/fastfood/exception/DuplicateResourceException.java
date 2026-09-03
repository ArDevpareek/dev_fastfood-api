package com.dev.fastfood.exception;

// Used when someone tries to create something that already exists —
// e.g. signing up with an email that's already taken.
// This will map to a 409 status code.
public class DuplicateResourceException extends RuntimeException {
    public DuplicateResourceException(String message) {
        super(message);
    }
}