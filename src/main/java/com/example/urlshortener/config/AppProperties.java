package com.example.urlshortener.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * All tunables of the application. Bound from {@code application.yml} (prefix {@code app}).
 */
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    /** Base URL used to build the {@code short_url} returned when a link is created. */
    private String baseUrl = "http://localhost:8080";

    private final Code code = new Code();

    private final Stats stats = new Stats();

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public Code getCode() {
        return code;
    }

    public Stats getStats() {
        return stats;
    }

    public static class Code {

        /** Length of generated codes (spec allows 6-8). */
        private int length = 7;

        /** How many times a generated code is retried after a unique-constraint collision. */
        private int maxGenerationAttempts = 10;

        /** Aliases that callers may not claim, because they would shadow API or static paths. */
        private List<String> reservedCodes = new ArrayList<>(List.of("api", "static", "actuator", "health", "error"));

        public int getLength() {
            return length;
        }

        public void setLength(int length) {
            this.length = length;
        }

        public int getMaxGenerationAttempts() {
            return maxGenerationAttempts;
        }

        public void setMaxGenerationAttempts(int maxGenerationAttempts) {
            this.maxGenerationAttempts = maxGenerationAttempts;
        }

        public List<String> getReservedCodes() {
            return reservedCodes;
        }

        public void setReservedCodes(List<String> reservedCodes) {
            this.reservedCodes = reservedCodes;
        }
    }

    public static class Stats {

        /** Size of the "daily visits" window, including today. */
        private int windowDays = 7;

        public int getWindowDays() {
            return windowDays;
        }

        public void setWindowDays(int windowDays) {
            this.windowDays = windowDays;
        }
    }
}
