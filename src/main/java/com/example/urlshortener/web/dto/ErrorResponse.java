package com.example.urlshortener.web.dto;

import java.time.Instant;

/**
 * The single error shape used by every endpoint.
 *
 * @param status    HTTP status code
 * @param error     stable machine-readable code, e.g. {@code INVALID_URL}
 * @param message   human-readable explanation
 * @param timestamp when the error was produced (UTC)
 * @param path      request path that failed
 */
public record ErrorResponse(int status, String error, String message, Instant timestamp, String path) {
}
