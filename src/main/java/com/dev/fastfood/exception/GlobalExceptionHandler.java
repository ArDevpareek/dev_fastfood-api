package com.dev.fastfood.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

// @RestControllerAdvice = "this class catches exceptions thrown by
// ANY controller in the whole app." One central place, instead of
// try/catch blocks scattered everywhere.
@RestControllerAdvice
public class GlobalExceptionHandler {

    // Thrown automatically by Spring when a @Valid @RequestBody fails one
    // or more bean-validation annotations (@NotBlank, @Size, @Positive,
    // etc.) on the DTO — the controller method body never even runs.
    // Collects every failing field into one map, instead of the client
    // only ever seeing the FIRST failure and having to fix-and-resubmit
    // one field at a time.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        ErrorResponse body = ErrorResponse.ofValidation(400, "Bad Request",
                "Validation failed", request.getRequestURI(), fieldErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

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