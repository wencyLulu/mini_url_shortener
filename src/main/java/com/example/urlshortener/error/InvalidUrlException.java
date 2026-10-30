package com.example.urlshortener.error;

import org.springframework.http.HttpStatus;

/** Submitted URL is missing, malformed, uses a forbidden scheme, or is too long. */
public class InvalidUrlException extends ApiException {

    public InvalidUrlException(String message) {
        super(HttpStatus.BAD_REQUEST, "INVALID_URL", message);
    }
}
