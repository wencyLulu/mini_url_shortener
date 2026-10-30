package com.example.urlshortener.web.dto;

import java.time.Instant;

/**
 * Body of {@code POST /api/links}.
 *
 * @param url       the long URL to shorten (required)
 * @param code      optional caller-chosen alias; when absent a random code is generated
 * @param expiresAt optional expiry timestamp; after it, redirects answer {@code 410 Gone}
 */
public record CreateLinkRequest(String url, String code, Instant expiresAt) {
}
