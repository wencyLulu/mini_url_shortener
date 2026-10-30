package com.example.urlshortener;

import java.time.Clock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@EnableCaching
@ConfigurationPropertiesScan
public class UrlShortenerApplication {

    public static void main(String[] args) {
        SpringApplication.run(UrlShortenerApplication.class, args);
    }

    /**
     * Single source of "now". All day-bucket calculations use UTC so that the definition of a
     * calendar day does not depend on the timezone of the machine running the service.
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
