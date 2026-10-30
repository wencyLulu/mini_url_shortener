package com.example.urlshortener.web.dto;

import java.time.Instant;

/** Body of {@code GET /api/links/{code}}. */
public record LinkDetailsResponse(
        String code,
        String url,
        Instant createdAt,
        Instant expiresAt,
        long visitCount,
        Instant lastVisitedAt
) {
}
