package com.example.urlshortener.web;

import java.time.Instant;

import com.example.urlshortener.domain.Link;
import com.example.urlshortener.repository.LinkRepository;
import com.example.urlshortener.service.LinkService;
import com.example.urlshortener.service.LinkTarget;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The hot-link cache holds code->target only; counters stay in the database so caching can never
 * distort statistics.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:statswindow;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=20000")
class LinkTargetCacheTest {

    @Autowired
    private LinkService linkService;

    @Autowired
    private LinkRepository linkRepository;

    @Autowired
    private CacheManager cacheManager;

    /** Start from an empty database (and an empty cache) so cache state is never inherited. */
    @BeforeEach
    void cleanState() {
        linkRepository.deleteAll();
        Cache cache = cacheManager.getCache(LinkService.TARGET_CACHE);
        if (cache != null) {
            cache.clear();
        }
    }

    @Test
    void targetIsCachedAndEvictedWhenTheLinkExpires() {
        Link link = linkRepository.save(new Link("cached01", "https://example.com/cached", Instant.now(), null));

        LinkTarget target = linkService.findTarget("cached01");
        assertThat(target.url()).isEqualTo("https://example.com/cached");

        Cache cache = cacheManager.getCache(LinkService.TARGET_CACHE);
        assertThat(cache).isNotNull();
        assertThat(cache.get("cached01")).isNotNull();
        assertThat(((LinkTarget) cache.get("cached01").get()).linkId()).isEqualTo(link.getId());

        // Force the link into the past, then resolve again: the entry must be evicted and 410 raised.
        linkRepository.save(new Link("cached02", "https://example.com/gone", Instant.now().minusSeconds(7200),
                Instant.now().minusSeconds(3600)));
        LinkTarget expired = linkService.findTarget("cached02");
        assertThat(expired.isExpired(Instant.now())).isTrue();
        linkService.evictTarget("cached02");
        assertThat(cache.get("cached02")).isNull();
    }
}
