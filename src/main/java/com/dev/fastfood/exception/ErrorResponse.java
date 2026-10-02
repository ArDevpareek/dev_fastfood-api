package com.dev.fastfood.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;
import java.util.Map;

// This describes the exact shape of every error your API will ever send.
// Whoever calls your API can always expect these same fields, no surprises.
public class ErrorResponse {

    private OffsetDateTime timestamp;
    private int status;
    private String error;
    private String message;
    private String path;
    // Only populated for bean-validation failures (@Valid on a request DTO):
    // field name -> that field's own message, e.g. {"quantity": "must be at
    // most 100"}. Every other error type leaves this null — @JsonInclude
    // omits it from the JSON entirely then, rather than sending "fieldErrors": null.
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Map<String, String> fieldErrors;

    public ErrorResponse(OffsetDateTime timestamp, int status, String error,
                         String message, String path) {
        this(timestamp, status, error, message, path, null);
    }

    public ErrorResponse(OffsetDateTime timestamp, int status, String error,
                         String message, String path, Map<String, String> fieldErrors) {
        this.timestamp = timestamp;
        this.status = status;
        this.error = error;
        this.message = message;
        this.path = path;
        this.fieldErrors = fieldErrors;
    }

    // A shortcut — builds one of these using the current time automatically,
    // so we don't have to type OffsetDateTime.now() everywhere.
    public static ErrorResponse of(int status, String error, String message, String path) {
        return new ErrorResponse(OffsetDateTime.now(), status, error, message, path);
    }

    // Same shortcut, for validation failures that also carry field-level messages.
    public static ErrorResponse ofValidation(int status, String error, String message,
                                              String path, Map<String, String> fieldErrors) {
        return new ErrorResponse(OffsetDateTime.now(), status, error, message, path, fieldErrors);
    }

    public OffsetDateTime getTimestamp() { return timestamp; }
    public int getStatus() { return status; }
    public String getError() { return error; }
    public String getMessage() { return message; }
    public String getPath() { return path; }
    public Map<String, String> getFieldErrors() { return fieldErrors; }
}