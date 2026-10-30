package com.example.urlshortener.service;

import java.time.Instant;

/**
 * Immutable {@code code -> target} mapping, the only thing stored in the hot-link cache.
 * Visit counters are deliberately NOT cached, so caching can never skew statistics.
 */
public record LinkTarget(long linkId, String url, Instant expiresAt) {

    public boolean isExpired(Instant now) {
        return expiresAt != null && !now.isBefore(expiresAt);
    }
}
