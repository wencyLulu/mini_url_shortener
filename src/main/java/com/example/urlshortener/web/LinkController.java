package com.example.urlshortener.web;

import java.net.URI;

import com.example.urlshortener.service.LinkService;
import com.example.urlshortener.web.dto.CreateLinkRequest;
import com.example.urlshortener.web.dto.LinkDetailsResponse;
import com.example.urlshortener.web.dto.LinkResponse;
import com.example.urlshortener.web.dto.LinkStatsResponse;
import com.example.urlshortener.web.dto.PagedLinksResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/links")
public class LinkController {

    private final LinkService linkService;

    public LinkController(LinkService linkService) {
        this.linkService = linkService;
    }

    @PostMapping
    public ResponseEntity<LinkResponse> create(@RequestBody(required = false) CreateLinkRequest request) {
        LinkResponse created = linkService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .location(URI.create("/api/links/" + created.code()))
                .body(created);
    }

    @GetMapping("/{code}")
    public LinkDetailsResponse details(@PathVariable String code) {
        return linkService.getDetails(code);
    }

    @GetMapping("/{code}/stats")
    public LinkStatsResponse stats(@PathVariable String code) {
        return linkService.getStats(code);
    }

    @GetMapping
    public PagedLinksResponse list(@RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "20") int size) {
        return linkService.list(page, size);
    }
}
