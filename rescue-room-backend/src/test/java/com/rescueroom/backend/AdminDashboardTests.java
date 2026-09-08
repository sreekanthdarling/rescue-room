package com.rescueroom.backend;

import com.rescueroom.backend.admin.*;
import com.rescueroom.backend.session.SessionStatus;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:admin-tests;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "admin.inactivity-seconds=45",
        "RESCUE_ADMIN_USERNAME=admin",
        "RESCUE_ADMIN_PASSWORD=changeme"
})
@Import(AdminDashboardTests.TestTime.class)
class AdminDashboardTests {
    static final Instant NOW = Instant.parse("2026-09-08T12:00:00Z");
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach void clear() { jdbc.update("DELETE FROM game_sessions"); }
    @Test void emptyDatabaseReturnsZeroStatisticsAndEmptyPage() throws Exception {
        var stats = stats();
        assertThat(List.of(stats.totalPlayers(), stats.playingNow(), stats.winners(), stats.losers(), stats.emailsSubmitted()))
                .containsOnly(0L);
        var page = sessions("");
        assertThat(page.sessions()).isEmpty();assertThat(page.totalElements()).isZero();assertThat(page.totalPages()).isZero();
        assertThat(page.page()).isZero();assertThat(page.size()).isEqualTo(25);
    }
    @Test void statisticsCountSessionsOutcomesAndOnlyNonblankEmails() throws Exception {
        seed(SessionStatus.PLAYING, "a@example.com", NOW.minusSeconds(10), NOW);
        seed(SessionStatus.PLAYING, null, NOW.minusSeconds(80), NOW.minusSeconds(70));
        seed(SessionStatus.WIN, "b@example.com", NOW.minusSeconds(100), NOW);
        seed(SessionStatus.WIN, "", NOW.minusSeconds(100), NOW);
        seed(SessionStatus.LOSE, "   ", NOW.minusSeconds(100), NOW);
        var stats = stats();
        assertThat(stats.totalPlayers()).isEqualTo(5);assertThat(stats.playingNow()).isEqualTo(1);
        assertThat(stats.winners()).isEqualTo(2);assertThat(stats.losers()).isEqualTo(1);assertThat(stats.emailsSubmitted()).isEqualTo(2);
    }
    @Test void playingNowUsesConfiguredInclusiveThresholdAndExcludesFutureOrFinishedSessions() throws Exception {
        var boundary = seed(SessionStatus.PLAYING, null, NOW.minusSeconds(100), NOW.minusSeconds(45));
        var recent = seed(SessionStatus.PLAYING, null, NOW.minusSeconds(100), NOW.minusSeconds(38));
        seed(SessionStatus.PLAYING, null, NOW.minusSeconds(100), NOW.minusSeconds(45).minusNanos(1000));
        seed(SessionStatus.PLAYING, null, NOW, NOW.plusSeconds(1));
        seed(SessionStatus.WIN, null, NOW.minusSeconds(100), NOW);
        seed(SessionStatus.LOSE, null, NOW.minusSeconds(100), NOW);
        assertThat(stats().inactivitySeconds()).isEqualTo(45);
        assertThat(stats().playingNow()).isEqualTo(2);
        assertThat(sessions("").sessions().stream().filter(AdminSession::activeNow).map(AdminSession::sessionId).toList())
                .containsExactlyInAnyOrder(boundary, recent);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM game_sessions WHERE status='PLAYING'", Long.class)).isEqualTo(4);
    }
    @Test void sessionsSortNewestFirstWithDeterministicIdTieBreaker() throws Exception {
        var oldest = seed(SessionStatus.PLAYING, null, NOW.minusSeconds(100), NOW);
        var newestFirst = seed(SessionStatus.WIN, null, NOW.minusSeconds(10), NOW);
        var middle = seed(SessionStatus.LOSE, null, NOW.minusSeconds(50), NOW);
        var newestSecond = seed(SessionStatus.PLAYING, null, NOW.minusSeconds(10), NOW);
        assertThat(sessions("").sessions().stream().map(AdminSession::sessionId).toList())
                .containsExactly(newestSecond, newestFirst, middle, oldest);
    }
    @ParameterizedTest @EnumSource(SessionStatus.class)
    void statusFiltersReturnOnlyRequestedStatus(SessionStatus status) throws Exception {
        for (var value : SessionStatus.values()) seed(value, null, NOW.minusSeconds(100), NOW.minusSeconds(80));
        var page = sessions("status=" + status);
        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(page.sessions()).extracting(AdminSession::status).containsOnly(status);
    }
    @Test void allAndLowercaseFiltersAreSupported() throws Exception {
        for (var value : SessionStatus.values()) seed(value, null, NOW.minusSeconds(100), NOW);
        assertThat(sessions("status=ALL").totalElements()).isEqualTo(3);
        assertThat(sessions("status=win").sessions()).extracting(AdminSession::status).containsOnly(SessionStatus.WIN);
    }
    @Test void emailSearchIsCaseInsensitiveAndTrimmed() throws Exception {
        var matching = seed(SessionStatus.WIN, "Player+demo@Example.com", NOW.minusSeconds(100), NOW);
        seed(SessionStatus.LOSE, "other@example.com", NOW.minusSeconds(100), NOW);
        assertThat(sessions("search=" + encode("  PLAYER+DEMO  ")).sessions()).extracting(AdminSession::sessionId).containsExactly(matching);
    }
    @Test void partialSessionIdSearchIsCaseInsensitive() throws Exception {
        var matching = seed(SessionStatus.PLAYING, null, NOW, NOW);
        seed(SessionStatus.WIN, null, NOW.minusSeconds(100), NOW);
        assertThat(sessions("search=" + matching.toString().substring(0, 18).toUpperCase(Locale.ROOT)).sessions())
                .extracting(AdminSession::sessionId).containsExactly(matching);
    }
    @Test void filterAndSearchCombineWithoutChangingGlobalStatistics() throws Exception {
        seed(SessionStatus.WIN, "find@example.com", NOW.minusSeconds(100), NOW);
        seed(SessionStatus.LOSE, "find@example.com", NOW.minusSeconds(100), NOW);
        assertThat(sessions("status=WIN&search=find").totalElements()).isEqualTo(1);
        assertThat(sessions("status=WIN&search=missing").sessions()).isEmpty();
        assertThat(stats().totalPlayers()).isEqualTo(2);
    }
    @Test void searchTreatsSqlWildcardsAndQuotesAsLiteralText() throws Exception {
        var literal = seed(SessionStatus.WIN, "percent%_!@example.com", NOW.minusSeconds(100), NOW);
        seed(SessionStatus.PLAYING, "normal@example.com", NOW, NOW);
        assertThat(sessions("search=" + encode("%_!")).sessions()).extracting(AdminSession::sessionId).containsExactly(literal);
        assertThat(sessions("search=" + encode("' OR 1=1 --")).sessions()).isEmpty();
    }
    @Test void paginationIsBoundedAndRetainsOrderAndTotals() throws Exception {
        for (int i = 0; i < 5; i++) seed(SessionStatus.PLAYING, null, NOW.minusSeconds(i), NOW);
        var first = sessions("page=0&size=2");var last = sessions("page=2&size=2");
        assertThat(first.sessions()).hasSize(2);assertThat(first.totalElements()).isEqualTo(5);assertThat(first.totalPages()).isEqualTo(3);
        assertThat(last.sessions()).hasSize(1);assertThat(last.sessions().getFirst().startedAt()).isEqualTo(NOW.minusSeconds(4));
        assertThat(sessions("page=10&size=2").sessions()).isEmpty();
    }
    @ParameterizedTest @ValueSource(strings = {"status=INVALID", "page=-1", "size=0", "size=101", "page=abc"})
    void invalidFiltersAndPagingAreRejected(String query) throws Exception {
        assertThat(get("/api/admin/sessions?" + query).statusCode()).isEqualTo(400);
    }
    @Test void excessiveSearchIsRejected() throws Exception {
        assertThat(get("/api/admin/sessions?search=" + "a".repeat(255)).statusCode()).isEqualTo(400);
    }
    @Test void listContainsRequestedFieldsAndPreservesNulls() throws Exception {
        var id = seed(SessionStatus.PLAYING, null, NOW, NOW);
        var item = sessions("").sessions().getFirst();
        assertThat(item.sessionId()).isEqualTo(id);assertThat(item.startedAt()).isEqualTo(NOW);assertThat(item.lastSeenAt()).isEqualTo(NOW);
        assertThat(item.email()).isNull();assertThat(item.finishedAt()).isNull();assertThat(item.remainingSeconds()).isNull();
    }
    @Test void dashboardAndAssetsAreServedLocallyWithoutTemplateDependencies() throws Exception {
        for (String path : List.of("/admin", "/admin/")) {
            var response = get(path);assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).contains("TOTAL PLAYERS", "PLAYING NOW", "EMAILS SUBMITTED", "/admin/admin.js");
        }
        assertThat(get("/admin/admin.js").statusCode()).isEqualTo(200);
        assertThat(get("/admin/admin.css").statusCode()).isEqualTo(200);
    }
    @Test void adminReadsDoNotMutateRowsAndResponsesAreNotCached() throws Exception {
        seed(SessionStatus.PLAYING, null, NOW.minusSeconds(100), NOW.minusSeconds(100));
        var before = jdbc.queryForList("SELECT * FROM game_sessions");
        var response = get("/api/admin/statistics");
        assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");sessions("");
        assertThat(jdbc.queryForList("SELECT * FROM game_sessions")).isEqualTo(before);
        var write = HttpRequest.newBuilder(URI.create(base() + "/api/admin/sessions"))
                .header("Authorization", "Basic " + Base64.getEncoder().encodeToString("admin:changeme".getBytes()))
                .POST(HttpRequest.BodyPublishers.noBody()).build();
        assertThat(http.send(write, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(405);
    }
    UUID seed(SessionStatus status, String email, Instant started, Instant seen) {
        var id = UUID.randomUUID();
        jdbc.update("INSERT INTO game_sessions(session_id,status,email,started_at,finished_at,remaining_seconds,last_seen_at) VALUES(?,?,?,?,?,?,?)",
                id, status.name(), email, started.atOffset(ZoneOffset.UTC), status == SessionStatus.PLAYING ? null : NOW.atOffset(ZoneOffset.UTC),
                status == SessionStatus.PLAYING ? null : status == SessionStatus.WIN ? 12 : 0, seen.atOffset(ZoneOffset.UTC));
        return id;
    }
    AdminStatistics stats() throws Exception { var response = get("/api/admin/statistics");assertThat(response.statusCode()).as(response.body()).isEqualTo(200);return mapper.readValue(response.body(), AdminStatistics.class); }
    AdminSessionPage sessions(String query) throws Exception { var response = get("/api/admin/sessions?" + query);assertThat(response.statusCode()).as(response.body()).isEqualTo(200);return mapper.readValue(response.body(), AdminSessionPage.class); }
    String base() { return "http://127.0.0.1:" + port; }
    HttpResponse<String> get(String path) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(base() + path))
                .header("Authorization", "Basic " + Base64.getEncoder().encodeToString("admin:changeme".getBytes()))
                .timeout(Duration.ofSeconds(15)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }
    String encode(String text) { return URLEncoder.encode(text, StandardCharsets.UTF_8); }
    @TestConfiguration static class TestTime { @Bean @Primary Clock testClock() { return Clock.fixed(NOW, ZoneOffset.UTC); } }
}
