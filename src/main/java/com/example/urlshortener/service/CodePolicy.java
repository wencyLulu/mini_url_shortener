package com.example.urlshortener.service;

import java.util.regex.Pattern;

import com.example.urlshortener.config.AppProperties;
import com.example.urlshortener.error.InvalidRequestException;

import org.springframework.stereotype.Component;

/** Rules a caller-chosen alias must satisfy. Generated codes are always valid by construction. */
@Component
public class CodePolicy {

    private static final Pattern CUSTOM_CODE = Pattern.compile("^[A-Za-z0-9]{3,32}$");

    private final AppProperties properties;

    public CodePolicy(AppProperties properties) {
        this.properties = properties;
    }

    public void validateCustomCode(String code) {
        if (!CUSTOM_CODE.matcher(code).matches()) {
            throw new InvalidRequestException("INVALID_CODE",
                    "code must be 3-32 characters from [A-Za-z0-9]");
        }
        for (String reserved : properties.getCode().getReservedCodes()) {
            if (code.equalsIgnoreCase(reserved)) {
                throw new InvalidRequestException("INVALID_CODE",
                        "code '" + code + "' is reserved");
            }
        }
    }

    /** Returns {@code null} for a null/blank input, otherwise the trimmed value. */
    public static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
