package com.dev.fastfood.exception;

import java.time.OffsetDateTime;

// This describes the exact shape of every error your API will ever send.
// Whoever calls your API can always expect these same fields, no surprises.
public class ErrorResponse {

    private OffsetDateTime timestamp;
    private int status;
    private String error;
    private String message;
    private String path;

    public ErrorResponse(OffsetDateTime timestamp, int status, String error,
                         String message, String path) {
        this.timestamp = timestamp;
        this.status = status;
        this.error = error;
        this.message = message;
        this.path = path;
    }

    // A shortcut — builds one of these using the current time automatically,
    // so we don't have to type OffsetDateTime.now() everywhere.
    public static ErrorResponse of(int status, String error, String message, String path) {
        return new ErrorResponse(OffsetDateTime.now(), status, error, message, path);
    }

    public OffsetDateTime getTimestamp() { return timestamp; }
    public int getStatus() { return status; }
    public String getError() { return error; }
    public String getMessage() { return message; }
    public String getPath() { return path; }
}