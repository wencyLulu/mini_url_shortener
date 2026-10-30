package com.example.urlshortener.web;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import com.example.urlshortener.domain.Link;
import com.example.urlshortener.domain.LinkDailyVisit;
import com.example.urlshortener.repository.LinkDailyVisitRepository;
import com.example.urlshortener.repository.LinkRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The 7-day window: boundaries, ordering (oldest -> newest) and zero filling. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:statswindow;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=20000")
class LinkStatsWindowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LinkRepository linkRepository;

    @Autowired
    private LinkDailyVisitRepository dailyVisitRepository;

    @Test
    void windowContainsLastSevenDaysOnlyAndFillsMissingDaysWithZero() throws Exception {
        Link link = linkRepository.save(new Link("statsw1", "https://example.com", Instant.now(), null));
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        dailyVisitRepository.save(new LinkDailyVisit(link.getId(), today.minusDays(6), 2));
        dailyVisitRepository.save(new LinkDailyVisit(link.getId(), today.minusDays(3), 7));
        dailyVisitRepository.save(new LinkDailyVisit(link.getId(), today.minusDays(1), 5));
        // Just outside the window - must not appear.
        dailyVisitRepository.save(new LinkDailyVisit(link.getId(), today.minusDays(8), 99));

        mockMvc.perform(get("/api/links/statsw1/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("statsw1"))
                .andExpect(jsonPath("$.days.length()").value(7))
                .andExpect(jsonPath("$.days[0].date").value(today.minusDays(6).toString()))
                .andExpect(jsonPath("$.days[6].date").value(today.toString()))
                .andExpect(jsonPath("$.days[0].count").value(2))
                .andExpect(jsonPath("$.days[1].count").value(0))
                .andExpect(jsonPath("$.days[3].count").value(7))
                .andExpect(jsonPath("$.days[5].count").value(5))
                .andExpect(jsonPath("$.days[6].count").value(0));
    }
}
