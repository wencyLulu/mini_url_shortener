package com.example.urlshortener.service;

import java.net.URI;
import java.net.URISyntaxException;

import com.example.urlshortener.error.InvalidUrlException;

import org.springframework.stereotype.Component;

/**
 * Applies the three validation rules from the spec: present, http/https scheme, at most 2048 chars.
 */
@Component
public class UrlValidator {

    public static final int MAX_URL_LENGTH = 2048;

    public String validateAndNormalize(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new InvalidUrlException("url must be present and non-empty");
        }
        String url = rawUrl.trim();
        if (url.length() > MAX_URL_LENGTH) {
            throw new InvalidUrlException("url must be at most " + MAX_URL_LENGTH + " characters");
        }

        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException ex) {
            throw new InvalidUrlException("url is not a valid URI: " + ex.getReason());
        }
        String scheme = uri.getScheme();
        if (scheme == null) {
            throw new InvalidUrlException("url must include an http or https scheme");
        }
        if (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https")) {
            throw new InvalidUrlException("url scheme must be http or https, got '" + scheme + "'");
        }
        if (uri.getHost() == null) {
            throw new InvalidUrlException("url must contain a host");
        }
        return url;
    }
}
