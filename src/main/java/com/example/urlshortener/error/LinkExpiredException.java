package com.example.urlshortener.error;

import org.springframework.http.HttpStatus;

public class LinkExpiredException extends ApiException {

    public LinkExpiredException(String code) {
        super(HttpStatus.GONE, "LINK_EXPIRED", "Short link '" + code + "' has expired");
    }
}
