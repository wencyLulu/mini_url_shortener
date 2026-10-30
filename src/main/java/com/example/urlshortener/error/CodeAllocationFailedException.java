package com.example.urlshortener.error;

import org.springframework.http.HttpStatus;

/** Every attempt to generate an unused short code collided; the caller may safely retry. */
public class CodeAllocationFailedException extends ApiException {

    public CodeAllocationFailedException(int attempts) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "CODE_ALLOCATION_FAILED",
                "Could not allocate a free short code after " + attempts + " attempts");
    }
}
