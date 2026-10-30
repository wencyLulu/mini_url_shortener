package com.example.urlshortener.web.dto;

import java.time.LocalDate;

public record DailyCountResponse(LocalDate date, long count) {
}
