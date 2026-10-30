package com.example.urlshortener.web;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;

import com.example.urlshortener.repository.LinkRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Concurrency on the write path: the unique constraint on {@code links.code} — not any in-process
 * lock — is what decides who wins a race for a code.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:createconcurrency;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=20000")
class LinkCreationConcurrencyTest {

    private static final int THREADS = 16;

    /** HTTP status and raw body of one concurrent request. */
    private record Response(int status, String body) {

        String code(ObjectMapper mapper) throws Exception {
            return mapper.readTree(body).get("code").asText();
        }
    }

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LinkRepository linkRepository;

    @BeforeEach
    void cleanDatabase() {
        linkRepository.deleteAll();
    }

    @Test
    void racingForTheSameCustomAliasProducesExactlyOneWinner() throws Exception {
        List<Response> responses = race(
                i -> "{\"url\":\"https://example.com/race/" + i + "\",\"code\":\"race01\"}");

        assertThat(responses).hasSize(THREADS);
        assertThat(responses).filteredOn(r -> r.status() == 201).hasSize(1);
        assertThat(responses).filteredOn(r -> r.status() == 409).hasSize(THREADS - 1);
        assertThat(linkRepository.findByCode("race01")).isPresent();
        assertThat(linkRepository.count()).isEqualTo(1L);
    }

    @Test
    void racingWithoutAnAliasGivesEveryRequestItsOwnCode() throws Exception {
        List<Response> responses = race(i -> "{\"url\":\"https://example.com/race/" + i + "\"}");

        assertThat(responses).filteredOn(r -> r.status() == 201).hasSize(THREADS);

        List<String> codes = new ArrayList<>();
        for (Response response : responses) {
            codes.add(response.code(objectMapper));
        }
        assertThat(codes).doesNotHaveDuplicates().hasSize(THREADS);
        assertThat(linkRepository.count()).isEqualTo((long) THREADS);
    }

    private List<Response> race(IntFunction<String> payloadOfThread) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch startGun = new CountDownLatch(1);
        List<Future<Response>> futures = new ArrayList<>();

        for (int t = 0; t < THREADS; t++) {
            int index = t;
            futures.add(pool.submit(() -> {
                startGun.await();
                HttpResponse<String> response = client.send(
                        HttpRequest.newBuilder(uri("/api/links"))
                                .header("Content-Type", "application/json")
                                .POST(HttpRequest.BodyPublishers.ofString(payloadOfThread.apply(index)))
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
                return new Response(response.statusCode(), response.body());
            }));
        }

        startGun.countDown();
        List<Response> responses = new ArrayList<>();
        for (Future<Response> future : futures) {
            responses.add(future.get(60, TimeUnit.SECONDS));
        }
        pool.shutdown();
        return responses;
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}
