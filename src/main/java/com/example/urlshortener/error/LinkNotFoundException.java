package com.example.urlshortener.error;

import org.springframework.http.HttpStatus;

public class LinkNotFoundException extends ApiException {

    public LinkNotFoundException(String code) {
        super(HttpStatus.NOT_FOUND, "NOT_FOUND", "No short link with code '" + code + "'");
    }
}
