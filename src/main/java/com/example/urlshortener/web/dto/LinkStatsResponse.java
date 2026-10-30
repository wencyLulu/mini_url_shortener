package com.example.urlshortener.web.dto;

import java.util.List;

/** Body of {@code GET /api/links/{code}/stats}: daily visits, oldest day first, zero-filled. */
public record LinkStatsResponse(String code, List<DailyCountResponse> days) {
}
