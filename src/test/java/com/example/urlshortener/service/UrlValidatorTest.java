package com.example.urlshortener.service;

import com.example.urlshortener.error.InvalidUrlException;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UrlValidatorTest {

    private final UrlValidator validator = new UrlValidator();

    @Test
    void acceptsHttpAndHttpsUrls() {
        assertThat(validator.validateAndNormalize("https://example.com/a/b?x=1")).isEqualTo("https://example.com/a/b?x=1");
        assertThat(validator.validateAndNormalize("http://example.com")).isEqualTo("http://example.com");
        assertThat(validator.validateAndNormalize("  https://example.com  ")).isEqualTo("https://example.com");
    }

    @Test
    void rejectsMissingUrl() {
        assertThatThrownBy(() -> validator.validateAndNormalize(null))
                .isInstanceOf(InvalidUrlException.class)
                .hasMessageContaining("non-empty");
        assertThatThrownBy(() -> validator.validateAndNormalize("   "))
                .isInstanceOf(InvalidUrlException.class);
    }

    @Test
    void rejectsNonHttpScheme() {
        assertThatThrownBy(() -> validator.validateAndNormalize("ftp://example.com"))
                .isInstanceOf(InvalidUrlException.class)
                .hasMessageContaining("http or https");
    }

    @Test
    void rejectsMissingScheme() {
        assertThatThrownBy(() -> validator.validateAndNormalize("not-a-url"))
                .isInstanceOf(InvalidUrlException.class);
    }

    @Test
    void rejectsUrlWithoutHost() {
        assertThatThrownBy(() -> validator.validateAndNormalize("https://"))
                .isInstanceOf(InvalidUrlException.class);
    }

    @Test
    void rejectsTooLongUrl() {
        String tooLong = "https://example.com/" + "a".repeat(UrlValidator.MAX_URL_LENGTH);
        assertThat(tooLong).hasSizeGreaterThan(UrlValidator.MAX_URL_LENGTH);
        assertThatThrownBy(() -> validator.validateAndNormalize(tooLong))
                .isInstanceOf(InvalidUrlException.class)
                .hasMessageContaining("2048");
    }
}
