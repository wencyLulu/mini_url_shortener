package com.example.urlshortener.error;

import org.springframework.http.HttpStatus;

/** Generic 400 for a malformed request parameter (custom code, expiry, pagination, ...). */
public class InvalidRequestException extends ApiException {

    public InvalidRequestException(String errorCode, String message) {
        super(HttpStatus.BAD_REQUEST, errorCode, message);
    }
}
