package com.example.urlshortener.service;

import java.security.SecureRandom;

import com.example.urlshortener.config.AppProperties;

import org.springframework.stereotype.Component;

/**
 * Random Base62 codes ({@code [0-9A-Za-z]}), 7 characters by default.
 *
 * <p>Random rather than sequential: an auto-increment id encoded in Base62 would make the total
 * number of links public and let anyone walk the whole code space. Random rather than
 * {@code hash(url)}: the chosen duplicate-URL policy issues a NEW code per submission, which a
 * deterministic hash of the URL cannot do.
 *
 * <p>Collisions are not avoided here — they are detected by the unique constraint on
 * {@code links.code} and handled by retrying (see {@code LinkService}).
 */
@Component
public class Base62CodeGenerator implements CodeGenerator {

    private static final char[] ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();

    private final SecureRandom random = new SecureRandom();

    private final int length;

    public Base62CodeGenerator(AppProperties properties) {
        this.length = properties.getCode().getLength();
    }

    @Override
    public String generate() {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHABET[random.nextInt(ALPHABET.length)]);
        }
        return sb.toString();
    }
}
