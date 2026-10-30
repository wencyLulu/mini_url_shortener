package com.example.urlshortener.web;

import java.net.URI;

import com.example.urlshortener.error.LinkNotFoundException;
import com.example.urlshortener.service.RedirectService;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RedirectController {

    private final RedirectService redirectService;

    public RedirectController(RedirectService redirectService) {
        this.redirectService = redirectService;
    }

    /**
     * {@code GET /{code}} — answers {@code 302 Found} with the original URL in {@code Location}.
     *
     * <p>302 (not 301) because browsers cache 301 permanently and would stop coming back, which
     * would silently undercount visits.
     */
    @GetMapping("/{code}")
    public ResponseEntity<Void> redirect(@PathVariable String code) {
        if (code.indexOf('.') >= 0) {
            // Static assets (/index.html, /favicon.ico, ...) must not be treated as short codes.
            throw new LinkNotFoundException(code);
        }
        String target = redirectService.resolveAndCount(code);
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, target)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .build();
    }
}
