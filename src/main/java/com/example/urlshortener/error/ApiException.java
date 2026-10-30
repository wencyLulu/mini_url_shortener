package com.example.urlshortener.error;

import org.springframework.http.HttpStatus;

/**
 * Base class for every error the API reports deliberately. Carries both the HTTP status and the
 * stable machine-readable {@code error} code that appears in the JSON body.
 */
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;

    private final String errorCode;

    protected ApiException(HttpStatus status, String errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
