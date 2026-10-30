package com.example.urlshortener.service;

import java.time.Clock;
import java.time.Instant;

import com.example.urlshortener.error.LinkExpiredException;

import org.springframework.stereotype.Service;

@Service
public class RedirectService {

    private final LinkService linkService;

    private final VisitRecorder visitRecorder;

    private final Clock clock;

    public RedirectService(LinkService linkService, VisitRecorder visitRecorder, Clock clock) {
        this.linkService = linkService;
        this.visitRecorder = visitRecorder;
        this.clock = clock;
    }

    /**
     * Resolves a code to its original URL and counts the visit.
     *
     * @throws com.example.urlshortener.error.LinkNotFoundException if the code is unknown
     * @throws LinkExpiredException                                 if the link expired
     */
    public String resolveAndCount(String code) {
        LinkTarget target = linkService.findTarget(code);
        Instant now = clock.instant();
        if (target.isExpired(now)) {
            linkService.evictTarget(code);
            throw new LinkExpiredException(code);
        }
        visitRecorder.recordVisit(target.linkId());
        return target.url();
    }
}
