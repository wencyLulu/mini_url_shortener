package com.example.urlshortener.web.dto;

import java.time.Instant;

/** Body of {@code 201 Created}. */
public record LinkResponse(String code, String shortUrl, String url, Instant createdAt, Instant expiresAt) {
}
