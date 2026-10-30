package com.example.urlshortener.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.example.urlshortener.config.AppProperties;
import com.example.urlshortener.domain.Link;
import com.example.urlshortener.domain.LinkDailyVisit;
import com.example.urlshortener.error.CodeAlreadyExistsException;
import com.example.urlshortener.error.CodeAllocationFailedException;
import com.example.urlshortener.error.InvalidRequestException;
import com.example.urlshortener.error.LinkNotFoundException;
import com.example.urlshortener.repository.LinkDailyVisitRepository;
import com.example.urlshortener.repository.LinkRepository;
import com.example.urlshortener.web.dto.CreateLinkRequest;
import com.example.urlshortener.web.dto.DailyCountResponse;
import com.example.urlshortener.web.dto.LinkDetailsResponse;
import com.example.urlshortener.web.dto.LinkResponse;
import com.example.urlshortener.web.dto.LinkStatsResponse;
import com.example.urlshortener.web.dto.PagedLinksResponse;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LinkService {

    public static final String TARGET_CACHE = "codeToTarget";

    private static final int MAX_PAGE_SIZE = 100;

    private final LinkRepository linkRepository;

    private final LinkDailyVisitRepository dailyVisitRepository;

    private final LinkStore linkStore;

    private final CodeGenerator codeGenerator;

    private final UrlValidator urlValidator;

    private final CodePolicy codePolicy;

    private final AppProperties properties;

    private final Clock clock;

    public LinkService(LinkRepository linkRepository,
                       LinkDailyVisitRepository dailyVisitRepository,
                       LinkStore linkStore,
                       CodeGenerator codeGenerator,
                       UrlValidator urlValidator,
                       CodePolicy codePolicy,
                       AppProperties properties,
                       Clock clock) {
        this.linkRepository = linkRepository;
        this.dailyVisitRepository = dailyVisitRepository;
        this.linkStore = linkStore;
        this.codeGenerator = codeGenerator;
        this.urlValidator = urlValidator;
        this.codePolicy = codePolicy;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Creates a short link. A submitted URL always yields a NEW code (see README), so no
     * lookup/upsert race exists on the write path.
     */
    public LinkResponse create(CreateLinkRequest request) {
        if (request == null) {
            throw new InvalidRequestException("MALFORMED_JSON", "request body is required");
        }
        String url = urlValidator.validateAndNormalize(request.url());
        Instant now = clock.instant();
        Instant expiresAt = validateExpiry(request.expiresAt(), now);

        String requestedCode = CodePolicy.blankToNull(request.code());
        Link link;
        if (requestedCode == null) {
            link = insertWithGeneratedCode(url, now, expiresAt);
        } else {
            codePolicy.validateCustomCode(requestedCode);
            link = insertCustomCode(new Link(requestedCode, url, now, expiresAt));
        }
        return toResponse(link);
    }

    @Transactional(readOnly = true)
    public LinkDetailsResponse getDetails(String code) {
        return toDetails(requireLink(code));
    }

    @Transactional(readOnly = true)
    public LinkStatsResponse getStats(String code) {
        Link link = requireLink(code);

        int window = Math.max(1, properties.getStats().getWindowDays());
        LocalDate today = LocalDate.now(clock);
        LocalDate fromInclusive = today.minusDays(window - 1L);

        Map<LocalDate, Long> countsByDay = new HashMap<>();
        for (LinkDailyVisit visit : dailyVisitRepository.findByLinkIdAndVisitDayBetween(
                link.getId(), fromInclusive, today)) {
            countsByDay.put(visit.getVisitDay(), visit.getVisitCount());
        }

        List<DailyCountResponse> days = new ArrayList<>(window);
        for (int offset = window - 1; offset >= 0; offset--) {
            LocalDate day = today.minusDays(offset);
            days.add(new DailyCountResponse(day, countsByDay.getOrDefault(day, 0L)));
        }
        return new LinkStatsResponse(link.getCode(), days);
    }

    @Transactional(readOnly = true)
    public PagedLinksResponse list(int page, int size) {
        if (page < 0) {
            throw new InvalidRequestException("INVALID_PAGINATION", "page must be >= 0");
        }
        int pageSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        // id is a tie-breaker: two links can share the same created_at on clocks with ms precision.
        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"));
        PageRequest pageRequest = PageRequest.of(page, pageSize, sort);
        Page<Link> result = linkRepository.findAll(pageRequest);
        return new PagedLinksResponse(
                result.getContent().stream().map(this::toDetails).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    /**
     * Code -> target lookup used by redirects. Cached because traffic is read-heavy; the entry only
     * holds immutable data, and visit counters are counted separately on every hit.
     */
    @Cacheable(cacheNames = TARGET_CACHE, key = "#code")
    public LinkTarget findTarget(String code) {
        Link link = requireLink(code);
        return new LinkTarget(link.getId(), link.getUrl(), link.getExpiresAt());
    }

    @CacheEvict(cacheNames = TARGET_CACHE, key = "#code")
    public void evictTarget(String code) {
        // Cache entries expire by TTL; this explicit evict covers the "link just expired" case.
    }

    private Link insertWithGeneratedCode(String url, Instant now, Instant expiresAt) {
        int maxAttempts = Math.max(1, properties.getCode().getMaxGenerationAttempts());
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            try {
                return linkStore.insert(new Link(codeGenerator.generate(), url, now, expiresAt));
            } catch (DataIntegrityViolationException ex) {
                // Unique constraint on links.code rejected the candidate; try another one.
            }
        }
        throw new CodeAllocationFailedException(maxAttempts);
    }

    private Link insertCustomCode(Link link) {
        try {
            return linkStore.insert(link);
        } catch (DataIntegrityViolationException ex) {
            throw new CodeAlreadyExistsException(link.getCode());
        }
    }

    private Instant validateExpiry(Instant expiresAt, Instant now) {
        if (expiresAt == null) {
            return null;
        }
        if (!expiresAt.isAfter(now)) {
            throw new InvalidRequestException("INVALID_EXPIRY", "expiresAt must be in the future");
        }
        return expiresAt;
    }

    private Link requireLink(String code) {
        return linkRepository.findByCode(code)
                .orElseThrow(() -> new LinkNotFoundException(code));
    }

    private LinkResponse toResponse(Link link) {
        return new LinkResponse(link.getCode(), shortUrl(link.getCode()), link.getUrl(),
                link.getCreatedAt(), link.getExpiresAt());
    }

    private LinkDetailsResponse toDetails(Link link) {
        return new LinkDetailsResponse(link.getCode(), link.getUrl(), link.getCreatedAt(),
                link.getExpiresAt(), link.getVisitCount(), link.getLastVisitedAt());
    }

    private String shortUrl(String code) {
        String base = properties.getBaseUrl();
        if (base != null && base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/" + code;
    }
}
