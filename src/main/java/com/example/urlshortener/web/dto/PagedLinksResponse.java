package com.example.urlshortener.web.dto;

import java.util.List;

/** Body of {@code GET /api/links}: newest links first. */
public record PagedLinksResponse(
        List<LinkDetailsResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
