package com.example.urlshortener.repository;

import java.time.Instant;
import java.util.Optional;

import com.example.urlshortener.domain.Link;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LinkRepository extends JpaRepository<Link, Long> {

    Optional<Link> findByCode(String code);

    boolean existsByCode(String code);

    /**
     * Atomically bumps the total counter. Doing the arithmetic in SQL (instead of
     * read-modify-write in Java) is what makes concurrent redirects loss-free.
     */
    @Modifying
    @Query("update Link l set l.visitCount = l.visitCount + 1, l.lastVisitedAt = :now where l.id = :id")
    int incrementVisitCount(@Param("id") long id, @Param("now") Instant now);
}
