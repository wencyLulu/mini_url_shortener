package com.example.urlshortener.web;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the (optional) minimal UI.
 *
 * <p>An explicit mapping is required because {@code RedirectController} owns {@code /{code}}, and
 * request mappings always win over the static-resource handler — otherwise the welcome page would be
 * swallowed as an unknown short code.
 */
@Controller
public class IndexController {

    private final Resource indexPage = new ClassPathResource("static/index.html");

    @GetMapping({"/", "/index.html"})
    public ResponseEntity<Resource> index() {
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(indexPage);
    }
}
