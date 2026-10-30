package com.example.urlshortener.repository;

import java.time.LocalDate;
import java.util.List;

import com.example.urlshortener.domain.LinkDailyVisit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LinkDailyVisitRepository extends JpaRepository<LinkDailyVisit, Long> {

    /**
     * Atomically bumps today's bucket. Returns 0 when no row exists for that (link, day) yet.
     */
    @Modifying
    @Query("update LinkDailyVisit v set v.visitCount = v.visitCount + 1 "
            + "where v.linkId = :linkId and v.visitDay = :day")
    int incrementVisitCount(@Param("linkId") long linkId, @Param("day") LocalDate day);

    List<LinkDailyVisit> findByLinkIdAndVisitDayBetween(long linkId, LocalDate fromInclusive, LocalDate toInclusive);
}
