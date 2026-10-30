package com.example.urlshortener.service;

import com.example.urlshortener.domain.Link;
import com.example.urlshortener.repository.LinkRepository;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Inserts a link in its own (new) transaction.
 *
 * <p>The isolation matters: a {@code code} collision aborts the INSERT and would mark an ambient
 * transaction rollback-only, which would poison the retry loop in {@code LinkService}. Running every
 * attempt in a fresh transaction keeps "try another code" possible.
 */
@Component
public class LinkStore {

    private final LinkRepository linkRepository;

    public LinkStore(LinkRepository linkRepository) {
        this.linkRepository = linkRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Link insert(Link link) {
        return linkRepository.save(link);
    }
}
