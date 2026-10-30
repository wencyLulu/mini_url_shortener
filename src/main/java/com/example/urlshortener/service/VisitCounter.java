package com.example.urlshortener.service;

import java.time.Instant;
import java.time.LocalDate;

import com.example.urlshortener.domain.LinkDailyVisit;
import com.example.urlshortener.repository.LinkDailyVisitRepository;
import com.example.urlshortener.repository.LinkRepository;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records one visit: the lifetime total plus the per-day bucket.
 *
 * <p>All counting is expressed as SQL {@code SET count = count + 1}, so the database serialises
 * concurrent redirects on a row lock instead of the application doing read-modify-write.
 *
 * <p>The total is bumped first on purpose: {@code UPDATE links WHERE id = ?} takes the row lock for
 * the whole transaction, which serialises all redirects to the same link. That makes the
 * "create today's bucket if it is missing" step below practically race-free, because only one
 * transaction per link can be inside this method.
 */
@Component
public class VisitCounter {

    private final LinkRepository linkRepository;

    private final LinkDailyVisitRepository dailyVisitRepository;

    public VisitCounter(LinkRepository linkRepository, LinkDailyVisitRepository dailyVisitRepository) {
        this.linkRepository = linkRepository;
        this.dailyVisitRepository = dailyVisitRepository;
    }

    /**
     * @throws org.springframework.dao.DataIntegrityViolationException if another transaction created
     *                                                                 today's bucket concurrently
     *                                                                 (only possible on a database
     *                                                                 without row-level locking);
     *                                                                 the whole transaction is rolled
     *                                                                 back, so retrying is safe
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordVisit(long linkId, LocalDate day, Instant now) {
        linkRepository.incrementVisitCount(linkId, now);
        int updated = dailyVisitRepository.incrementVisitCount(linkId, day);
        if (updated == 0) {
            dailyVisitRepository.save(new LinkDailyVisit(linkId, day, 1));
        }
    }
}
