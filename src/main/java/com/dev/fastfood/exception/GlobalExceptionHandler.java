package com.dev.fastfood.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// @RestControllerAdvice = "this class catches exceptions thrown by
// ANY controller in the whole app." One central place, instead of
// try/catch blocks scattered everywhere.
@RestControllerAdvice
public class GlobalExceptionHandler {

    // Whenever a ResourceNotFoundException is thrown ANYWHERE in the app,
    // this method catches it and builds a clean 404 response instead.
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(
            ResourceNotFoundException ex, HttpServletRequest request) {
        ErrorResponse body = ErrorResponse.of(404, "Not Found",
                ex.getMessage(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    // Same idea, for broken business rules — turns into a clean 400.
    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ErrorResponse> handleBusinessRule(
            BusinessRuleException ex, HttpServletRequest request) {
        ErrorResponse body = ErrorResponse.of(400, "Bad Request",
                ex.getMessage(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    // Same idea, for duplicates — turns into a clean 409.
    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ErrorResponse> handleDuplicate(
            DuplicateResourceException ex, HttpServletRequest request) {
        ErrorResponse body = ErrorResponse.of(409, "Conflict",
                ex.getMessage(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    // A safety net — catches ANYTHING else that goes wrong and wasn't
    // one of the three types above. We deliberately DON'T show the real
    // error details to whoever's calling the API — that could leak
    // sensitive info about how our app works internally.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleEverythingElse(
            Exception ex, HttpServletRequest request) {
        ErrorResponse body = ErrorResponse.of(500, "Internal Server Error",
                "Something went wrong", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}