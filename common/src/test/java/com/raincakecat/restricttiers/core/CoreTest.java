package com.raincakecat.restricttiers.core;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.raincakecat.restricttiers.core.Model.*;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.HttpClient;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

class CoreTest {
    static final String UUID = "123456781234123412341234567890ab";
    static final String JSON = """
        {"uuid":"123456781234123412341234567890ab","username":"180","nameUnavailable":false,
         "activeCount":1,"historyCount":1,
         "active":[{"server":"Example","reason":"Using illegal modifications","categories":["Cheating"],
          "restrictedAt":"2026-10-01T00:00:00Z","expiresAt":null,"archivedAt":null,"unverified":true,
          "sourceUrl":"https://discord.com/channels/12345678901234567/12345678901234568/12345678901234569"}],
         "history":[{"server":"Example","reason":"Past reason","categories":[],"restrictedAt":null,
          "expiresAt":null,"archivedAt":null,"unverified":false,"sourceUrl":null}]}
        """;
    @Test void completeIdentityValidation() {
        for (String s : List.of("180", "123", "Under_score", "___", "Using", "Illegal")) assertEquals(s, ApiClient.identifier(s));
        assertEquals(UUID, ApiClient.identifier("12345678-1234-1234-1234-1234567890AB"));
        assertEquals(UUID, ApiClient.identifier(UUID));
        for (String s : List.of("ab", "a.b", "Bad-name", "escaped\\_name", "someone name", "12345678-12341234-1234-1234567890ab", "12345678901234567")) assertThrows(IllegalArgumentException.class, () -> ApiClient.identifier(s));
    }
    @Test void strictUuidBindingAndProviderOutage() {
        assertThrows(IllegalArgumentException.class, () -> ApiClient.parse(JSON, "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"));
        var p = ApiClient.parse(JSON.replace("\"180\"", "null").replace("\"nameUnavailable\":false", "\"nameUnavailable\":true"), UUID);
        assertEquals(UUID, p.label()); assertTrue(p.nameUnavailable()); assertEquals(1, p.activeCount());
    }
    @Test void privateSnapshotPaginationAndSafeLinks() {
        var p = ApiClient.parse(JSON, "180");
        assertTrue(Results.summary(p, "/details", "token").stream().anyMatch(l -> p.url().equals(l.url())));
        var lines = Results.page(p, 1, "/details", "token");
        assertTrue(lines.stream().anyMatch(l -> l.url() != null && l.url().startsWith("https://discord.com/channels/")));
        assertTrue(lines.stream().anyMatch(l -> l.text().equals("Expires: Not recorded") || l.text().contains("Expires: Not recorded")));
        assertFalse(lines.stream().anyMatch(l -> l.text().toLowerCase().contains("permanent")));
        assertTrue(index(lines, "Active ·") < index(lines, "Past ·"));
        assertEquals(1, Results.page(p, 99, "/details", "token").size());
        assertNull(ApiClient.parse(JSON.replace("https://discord.com/channels/12345678901234567/12345678901234568/12345678901234569", "https://evil.example/"), "180").active().getFirst().sourceUrl());
    }
    private int index(List<Line> lines, String prefix) { for (int i = 0; i < lines.size(); i++) if (lines.get(i).text().startsWith(prefix)) return i; return -1; }
    @Test void truncatedRecordsAndLiteralReason() {
        var p = ApiClient.parse(JSON.replace("\"historyCount\":1", "\"historyCount\":55").replace("Using illegal modifications", "**Permanent** §c <click:run_command:/op>"), "180");
        assertTrue(p.incomplete());
        assertTrue(Results.summary(p, "/details", "token").stream().anyMatch(l -> l.text().contains("complete list")));
        assertTrue(Results.page(p, 1, "/details", "token").stream().anyMatch(l -> l.text().startsWith("**Permanent**") && l.command() == null && l.url() == null));
    }
    @Test void oneRequestCooldownRetryAndResultIsolation() {
        Session first = new Session(), second = new Session();
        assertNull(first.begin(1000)); assertNotNull(first.begin(8000));
        first.complete(new ApiClient.Outcome(ApiClient.parse(JSON, "180"), null, 0), 2000);
        assertNotNull(first.begin(5999)); assertNull(second.snapshot()); assertNotEquals(first.token(), second.token());
        String oldToken = first.token();
        assertNull(first.begin(6000)); assertNull(first.snapshot()); assertNotEquals(oldToken, first.token());
        first.complete(new ApiClient.Outcome(null, "rate limited", 60), 7000);
        assertNotNull(first.begin(66999)); assertNull(first.begin(67000));
    }
    @Test void multiplePagesRemainActiveFirstAndUseSnapshot() {
        var original = ApiClient.parse(JSON, "180");
        var player = new Player(UUID, "180", false, Collections.nCopies(4, original.active().getFirst()), 4, Collections.nCopies(4, original.history().getFirst()), 4);
        var first = Results.page(player, 1, "/details", "token");
        var second = Results.page(player, 2, "/details", "token");
        var third = Results.page(player, 3, "/details", "token");
        assertEquals(3, first.stream().filter(l -> l.text().startsWith("Active ·")).count());
        assertFalse(first.stream().anyMatch(l -> l.text().startsWith("Past ·")));
        assertTrue(index(second, "Active ·") < index(second, "Past ·"));
        assertEquals(2, third.stream().filter(l -> l.text().startsWith("Past ·")).count());
        assertTrue(first.stream().anyMatch(l -> "/details token 2".equals(l.command())));
        assertTrue(third.stream().anyMatch(l -> "/details token 2".equals(l.command())));
    }
    @Test void noQueryUntilLookupAndNoQueryForNavigation() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/player/", exchange -> {
            requests.incrementAndGet(); byte[] bytes = JSON.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        }); server.start();
        try {
            ApiClient api = new ApiClient(URI.create("http://127.0.0.1:" + server.getAddress().getPort()), HttpClient.newHttpClient());
            assertEquals(0, requests.get());
            var result = api.lookup("180").get(); assertTrue(result.success()); assertEquals(1, requests.get());
            Results.summary(result.player(), "/details", "token"); Results.page(result.player(), 1, "/details", "token");
            assertEquals(1, requests.get());
        } finally { server.stop(0); }
    }
    @Test void errorsAreNotCleanResultsAndNoAutomaticRetry() throws Exception {
        for (int status : List.of(400, 404, 429, 503)) {
            AtomicInteger count = new AtomicInteger();
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> { count.incrementAndGet(); exchange.getResponseHeaders().add("Retry-After", "37"); exchange.sendResponseHeaders(status, -1); exchange.close(); }); server.start();
            try {
                var outcome = new ApiClient(URI.create("http://127.0.0.1:" + server.getAddress().getPort()), HttpClient.newHttpClient()).lookup("180").get();
                assertFalse(outcome.success()); assertNotNull(outcome.error()); assertEquals(1, count.get());
                if (status == 429) assertEquals(37, outcome.retrySeconds());
            } finally { server.stop(0); }
        }
    }
    @Test void emptyAndMissingRecordsAreDistinct() {
        var p = new Player(UUID, "180", false, List.of(), 0, List.of(), 0);
        assertTrue(Results.summary(p, "/details", "token").stream().anyMatch(l -> l.text().contains("No recorded restrictions")));
        assertThrows(RuntimeException.class, () -> ApiClient.parse("{}", "180"));
    }
    @Test void retryAfterHeaderForms() {
        assertEquals(60, ApiClient.retrySeconds("garbage"));
        assertEquals(1, ApiClient.retrySeconds("0"));
        assertTrue(ApiClient.retrySeconds(java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC).plusSeconds(30).format(java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME)) >= 28);
    }
}
