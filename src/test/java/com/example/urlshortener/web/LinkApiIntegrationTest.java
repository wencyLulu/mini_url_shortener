package com.example.urlshortener.web;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import com.example.urlshortener.domain.Link;
import com.example.urlshortener.repository.LinkRepository;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end coverage of the four core capabilities: create -> redirect -> details -> stats.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:linkapi;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=20000")
class LinkApiIntegrationTest {

    private static final String LONG_URL = "https://example.com/some/very/long/path?query=1";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LinkRepository linkRepository;

    /**
     * Every case starts from an empty database (link_daily_visit cascades from links), so no result
     * ever depends on execution order or on which cases ran before.
     */
    @BeforeEach
    void cleanDatabase() {
        linkRepository.deleteAll();
    }

    @Test
    void createRedirectDetailsAndStats() throws Exception {
        String code = createLink("{\"url\":\"" + LONG_URL + "\"}");

        // 1. create
        assertThat(code).matches("[A-Za-z0-9]{7}");

        // 2. redirect
        mockMvc.perform(get("/" + code))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", LONG_URL));

        // 3. details
        mockMvc.perform(get("/api/links/" + code))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.url").value(LONG_URL))
                .andExpect(jsonPath("$.created_at").exists())
                .andExpect(jsonPath("$.visit_count").value(1));

        // 4. stats: 7 days, oldest first, today contains the single redirect
        String today = LocalDate.now(ZoneOffset.UTC).toString();
        mockMvc.perform(get("/api/links/" + code + "/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.days.length()").value(7))
                .andExpect(jsonPath("$.days[0].date").value(LocalDate.now(ZoneOffset.UTC).minusDays(6).toString()))
                .andExpect(jsonPath("$.days[6].date").value(today))
                .andExpect(jsonPath("$.days[6].count").value(1))
                .andExpect(jsonPath("$.days[0].count").value(0));
    }

    @Test
    void createReturnsShortUrlAndCreatedAt() throws Exception {
        String response = mockMvc.perform(post("/api/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.url").value("https://example.com"))
                .andExpect(jsonPath("$.created_at").exists())
                .andExpect(jsonPath("$.expires_at").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(response);
        assertThat(json.get("short_url").asText()).isEqualTo("http://localhost:8080/" + json.get("code").asText());
    }

    @Test
    void unknownCodeReturnsNotFoundOnEveryEndpoint() throws Exception {
        mockMvc.perform(get("/zzzzzzz"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").exists());

        mockMvc.perform(get("/api/links/zzzzzzz"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));

        mockMvc.perform(get("/api/links/zzzzzzz/stats"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    void invalidUrlsAreRejected() throws Exception {
        assertInvalidUrl("{\"url\":\"not-a-url\"}");
        assertInvalidUrl("{\"url\":\"\"}");
        assertInvalidUrl("{\"url\":\"ftp://example.com\"}");
        assertInvalidUrl("{\"url\":\"https://\"}");
        assertInvalidUrl("{\"url\":\"https://example.com/" + "a".repeat(2048) + "\"}");

        mockMvc.perform(post("/api/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"not-a-url\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_URL"));
    }

    @Test
    void malformedJsonIsRejected() throws Exception {
        mockMvc.perform(post("/api/links").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("MALFORMED_JSON"));
    }

    @Test
    void sameUrlSubmittedTwiceGetsTwoDifferentCodes() throws Exception {
        String first = createLink("{\"url\":\"" + LONG_URL + "\"}");
        String second = createLink("{\"url\":\"" + LONG_URL + "\"}");

        assertThat(first).isNotEqualTo(second);
        mockMvc.perform(get("/" + first)).andExpect(header().string("Location", LONG_URL));
        mockMvc.perform(get("/" + second)).andExpect(header().string("Location", LONG_URL));
    }

    @Test
    void customAliasIsHonouredAndRejectedWhenTaken() throws Exception {
        mockMvc.perform(post("/api/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com\",\"code\":\"myalias\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("myalias"));

        mockMvc.perform(get("/myalias")).andExpect(status().isFound()).andExpect(header().string("Location", "https://example.com"));

        mockMvc.perform(post("/api/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com\",\"code\":\"myalias\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CODE_ALREADY_EXISTS"));
    }

    @Test
    void invalidCustomAliasIsRejected() throws Exception {
        mockMvc.perform(post("/api/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com\",\"code\":\"ab\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_CODE"));

        mockMvc.perform(post("/api/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com\",\"code\":\"api\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_CODE"));
    }

    @Test
    void expiredLinkIsGoneButStillInspectable() throws Exception {
        Instant now = Instant.now();
        linkRepository.save(new Link("expired1", LONG_URL, now.minusSeconds(7200), now.minusSeconds(3600)));

        mockMvc.perform(get("/expired1"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.error").value("LINK_EXPIRED"));

        mockMvc.perform(get("/api/links/expired1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expires_at").exists());
    }

    @Test
    void expiryInThePastIsRejected() throws Exception {
        mockMvc.perform(post("/api/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com\",\"expires_at\":\"2020-01-01T00:00:00Z\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_EXPIRY"));
    }

    @Test
    void listingReturnsNewestLinksFirst() throws Exception {
        String first = createLink("{\"url\":\"https://example.com/one\"}");
        String second = createLink("{\"url\":\"https://example.com/two\"}");

        mockMvc.perform(get("/api/links").param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].code").value(second))
                .andExpect(jsonPath("$.content[1].code").value(first))
                .andExpect(jsonPath("$.total_elements").value(2))
                .andExpect(jsonPath("$.total_pages").value(1))
                .andExpect(jsonPath("$.page").value(0));
    }

    @Test
    void emptyBodyIsRejected() throws Exception {
        mockMvc.perform(post("/api/links").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("MALFORMED_JSON"));
    }

    @Test
    void aliasWithIllegalCharacterIsRejected() throws Exception {
        mockMvc.perform(post("/api/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com\",\"code\":\"ab!\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_CODE"));
    }

    @Test
    void unsupportedMethodReturns405() throws Exception {
        mockMvc.perform(delete("/api/links/somecode"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.error").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void rootServesTheMinimalUi() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("迷你短链服务")));
    }

    private void assertInvalidUrl(String body) throws Exception {
        mockMvc.perform(post("/api/links").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("INVALID_URL"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.path").value("/api/links"));
    }

    private String createLink(String body) throws Exception {
        String response = mockMvc.perform(post("/api/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("code").asText();
    }
}
