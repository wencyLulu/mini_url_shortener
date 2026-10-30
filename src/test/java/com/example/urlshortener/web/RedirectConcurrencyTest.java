package com.example.urlshortener.web;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.example.urlshortener.repository.LinkRepository;
import com.example.urlshortener.service.LinkService;
import com.example.urlshortener.web.dto.LinkStatsResponse;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The requirement "visit counting must be accurate under concurrent access" verified over real HTTP.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:concurrency;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=20000")
class RedirectConcurrencyTest {

    private static final int THREADS = 32;

    private static final int REQUESTS_PER_THREAD = 20;

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LinkRepository linkRepository;

    @Autowired
    private LinkService linkService;

    @Test
    void noVisitIsLostWhenManyClientsRedirectAtOnce() throws Exception {
        String code = createLink("https://example.com/concurrent");
        int expected = THREADS * REQUESTS_PER_THREAD;

        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch startGun = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();

        for (int t = 0; t < THREADS; t++) {
            futures.add(pool.submit(() -> {
                startGun.await();
                int redirects = 0;
                for (int i = 0; i < REQUESTS_PER_THREAD; i++) {
                    HttpResponse<Void> response = client.send(
                            HttpRequest.newBuilder(uri("/" + code)).GET().build(),
                            HttpResponse.BodyHandlers.discarding());
                    if (response.statusCode() == 302) {
                        redirects++;
                    }
                }
                return redirects;
            }));
        }

        startGun.countDown();
        int observed = 0;
        for (Future<Integer> future : futures) {
            observed += future.get(60, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertThat(observed).isEqualTo(expected);
        assertThat(linkRepository.findByCode(code).orElseThrow().getVisitCount()).isEqualTo(expected);

        LinkStatsResponse stats = linkService.getStats(code);
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        assertThat(stats.days()).hasSize(7);
        assertThat(stats.days().get(6).date()).isEqualTo(today);
        assertThat(stats.days().get(6).count()).isEqualTo(expected);
    }

    private String createLink(String url) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        String payload = "{\"url\":\"" + url + "\"}";
        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(uri("/api/links"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(payload))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(201);
        return objectMapper.readTree(response.body()).get("code").asText();
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}
