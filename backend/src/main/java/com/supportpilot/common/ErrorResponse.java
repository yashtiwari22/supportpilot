package com.supportpilot.common;

import java.time.Instant;
import java.util.Map;

/**
 * Standard error response envelope for SupportPilot REST APIs.
 */
public record ErrorResponse(
    int status,
    String error,
    String message,
    String path,
    Instant timestamp,
    Map<String, String> validationErrors
) {
    public ErrorResponse(int status, String error, String message, String path) {
        this(status, error, message, path, Instant.now(), null);
    }

    public ErrorResponse(int status, String error, String message, String path, Map<String, String> validationErrors) {
        this(status, error, message, path, Instant.now(), validationErrors);
    }
}
