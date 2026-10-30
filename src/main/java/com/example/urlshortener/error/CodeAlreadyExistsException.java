package com.example.urlshortener.error;

import org.springframework.http.HttpStatus;

/** Caller-chosen alias is already taken. */
public class CodeAlreadyExistsException extends ApiException {

    public CodeAlreadyExistsException(String code) {
        super(HttpStatus.CONFLICT, "CODE_ALREADY_EXISTS", "Short code '" + code + "' is already in use");
    }
}
