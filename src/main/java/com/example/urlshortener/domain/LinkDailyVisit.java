package com.example.urlshortener.domain;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Per-day visit aggregate. One row per (link, day) — see README "Daily statistics storage".
 */
@Entity
@Table(
        name = "link_daily_visit",
        uniqueConstraints = @UniqueConstraint(name = "uq_link_daily_visit", columnNames = {"link_id", "visit_day"})
)
public class LinkDailyVisit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "link_id", nullable = false)
    private long linkId;

    @Column(name = "visit_day", nullable = false)
    private LocalDate visitDay;

    @Column(name = "visit_count", nullable = false)
    private long visitCount;

    protected LinkDailyVisit() {
        // for JPA
    }

    public LinkDailyVisit(long linkId, LocalDate visitDay, long visitCount) {
        this.linkId = linkId;
        this.visitDay = visitDay;
        this.visitCount = visitCount;
    }

    public Long getId() {
        return id;
    }

    public long getLinkId() {
        return linkId;
    }

    public LocalDate getVisitDay() {
        return visitDay;
    }

    public long getVisitCount() {
        return visitCount;
    }
}
