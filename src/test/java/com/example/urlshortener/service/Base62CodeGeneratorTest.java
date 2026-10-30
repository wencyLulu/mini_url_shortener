package com.example.urlshortener.service;

import java.util.HashSet;
import java.util.Set;

import com.example.urlshortener.config.AppProperties;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class Base62CodeGeneratorTest {

    private final Base62CodeGenerator generator = new Base62CodeGenerator(properties(7));

    private static AppProperties properties(int length) {
        AppProperties properties = new AppProperties();
        properties.getCode().setLength(length);
        return properties;
    }

    @Test
    void generatesConfiguredLength() {
        assertThat(generator.generate()).hasSize(7);
        assertThat(new Base62CodeGenerator(properties(8)).generate()).hasSize(8);
    }

    @Test
    void usesOnlyAlphaNumericCharacters() {
        for (int i = 0; i < 500; i++) {
            assertThat(generator.generate()).matches("[A-Za-z0-9]{7}");
        }
    }

    @Test
    void producesDistinctCodes() {
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 20_000; i++) {
            codes.add(generator.generate());
        }
        assertThat(codes).hasSize(20_000);
    }
}
