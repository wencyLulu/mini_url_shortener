package com.example.urlshortener.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Records one visit. Day boundaries are UTC, so a visit always lands in the same bucket no matter
 * which timezone the service runs in.
 */
@Service
public class VisitRecorder {

    private static final Logger log = LoggerFactory.getLogger(VisitRecorder.class);

    private final VisitCounter counter;

    private final Clock clock;

    public VisitRecorder(VisitCounter counter, Clock clock) {
        this.counter = counter;
        this.clock = clock;
    }

    public void recordVisit(long linkId) {
        Instant now = clock.instant();
        LocalDate day = LocalDate.now(clock);

        try {
            counter.recordVisit(linkId, day, now);
        } catch (DataIntegrityViolationException ex) {
            // Safety net: today's bucket was created concurrently. The failed transaction rolled back
            // completely (total included), so replaying it yields the correct result.
            log.debug("Daily bucket for link {} was created concurrently, replaying", linkId);
            counter.recordVisit(linkId, day, now);
        }
    }
}
