package com.rescueroom.backend;

import com.rescueroom.backend.session.*;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:session-tests;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "RESCUE_ADMIN_USERNAME=admin",
        "RESCUE_ADMIN_PASSWORD=changeme"
})
@Import(SessionLifecycleTests.TestTime.class)
class SessionLifecycleTests {
    static final Instant START = Instant.parse("2026-09-08T10:00:00Z");
    @LocalServerPort int port;
    @Autowired ObjectMapper mapper;
    @Autowired GameSessionRepository repository;
    @Autowired JdbcTemplate jdbc;
    @Autowired MutableClock clock;
    final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach void reset() { repository.deleteAll(); clock.set(START); }

    @Test void startCreatesUniquePersistedPlayingSessions() throws Exception {
        var first = request("POST", "/start", "");
        var session = session(first);
        var second = start();
        assertThat(first.statusCode()).isEqualTo(201);
        assertThat(first.headers().firstValue("Location")).contains("/api/sessions/" + session.sessionId());
        assertThat(session.id()).isPositive();
        assertThat(session.sessionId()).isNotEqualTo(second.sessionId());
        assertThat(session.status()).isEqualTo(SessionStatus.PLAYING);
        assertThat(session.startedAt()).isEqualTo(START);
        assertThat(session.lastSeenAt()).isEqualTo(START);
        assertThat(session.email()).isNull();
        assertThat(session.finishedAt()).isNull();
        assertThat(session.remainingSeconds()).isNull();
        assertThat(get(session)).isEqualTo(session);
        assertThat(repository.count()).isEqualTo(2);
    }
    @Test void heartbeatUpdatesLastSeenWithoutChangingStartOrOutcome() throws Exception {
        var s = start(); clock.set(START.plusSeconds(12));
        var response = request("POST", path(s, "heartbeat"), "");
        assertThat(response.statusCode()).isEqualTo(200);
        var updated = get(s);
        assertThat(updated.lastSeenAt()).isEqualTo(START.plusSeconds(12));
        assertThat(updated.startedAt()).isEqualTo(START);
        assertThat(updated.status()).isEqualTo(SessionStatus.PLAYING);
        assertThat(updated.finishedAt()).isNull();
    }
    @Test void timestampsRoundTripAtDatabasePrecision() throws Exception {
        clock.set(START.plusNanos(123456789));
        var s = start();
        assertThat(s.startedAt()).isEqualTo(START.plusNanos(123456000));
        assertThat(get(s)).isEqualTo(s);
        clock.set(START.plusSeconds(1).plusNanos(987654321));
        var heartbeat = session(request("POST", path(s, "heartbeat"), ""));
        assertThat(get(s)).isEqualTo(heartbeat);
        clock.set(START.plusSeconds(2).plusNanos(555555999));
        var won = session(request("POST", path(s, "win"), "{\"remainingSeconds\":88}"));
        assertThat(get(s)).isEqualTo(won);
    }
    @Test void winStoresFinishTimeAndRemainingSeconds() throws Exception {
        var s = start(); clock.set(START.plusSeconds(67));
        assertThat(request("POST", path(s, "win"), "{\"remainingSeconds\":23}").statusCode()).isEqualTo(200);
        var won = get(s);
        assertThat(won.status()).isEqualTo(SessionStatus.WIN);
        assertThat(won.remainingSeconds()).isEqualTo(23);
        assertThat(won.finishedAt()).isEqualTo(START.plusSeconds(67));
        assertThat(won.lastSeenAt()).isEqualTo(won.finishedAt());
    }
    @Test void loseStoresFinishTimeAndZeroRemaining() throws Exception {
        var s = start(); clock.set(START.plusSeconds(90));
        assertThat(request("POST", path(s, "lose"), "").statusCode()).isEqualTo(200);
        var lost = get(s);
        assertThat(lost.status()).isEqualTo(SessionStatus.LOSE);
        assertThat(lost.remainingSeconds()).isZero();
        assertThat(lost.finishedAt()).isEqualTo(START.plusSeconds(90));
    }
    @ParameterizedTest @EnumSource(value = SessionStatus.class, names = {"WIN", "LOSE"})
    void terminalStatesRejectAllFurtherOutcomesAndHeartbeats(SessionStatus outcome) throws Exception {
        var s = start(); finish(s, outcome); var finished = get(s);
        clock.set(START.plusSeconds(100));
        assertProblem(request("POST", path(s, "win"), "{\"remainingSeconds\":10}"), 409);
        assertProblem(request("POST", path(s, "lose"), ""), 409);
        assertProblem(request("POST", path(s, "heartbeat"), ""), 409);
        assertThat(get(s)).isEqualTo(finished);
    }
    @ParameterizedTest @EnumSource(SessionStatus.class)
    void emailIsValidatedTrimmedAndUpdatableInEveryState(SessionStatus status) throws Exception {
        var s = start(); if (status != SessionStatus.PLAYING) finish(s, status);
        var before = get(s);
        assertThat(request("POST", path(s, "email"), "{\"email\":\"  Player@example.com  \"}").statusCode()).isEqualTo(200);
        assertThat(get(s).email()).isEqualTo("Player@example.com");
        assertThat(request("POST", path(s, "email"), "{\"email\":\"new@example.com\"}").statusCode()).isEqualTo(200);
        var after = get(s);
        assertThat(after.email()).isEqualTo("new@example.com");
        assertThat(after.status()).isEqualTo(before.status());
        assertThat(after.finishedAt()).isEqualTo(before.finishedAt());
        assertThat(after.remainingSeconds()).isEqualTo(before.remainingSeconds());
    }
    @ParameterizedTest @ValueSource(strings = {"bad-id", "1-1-1-1-1", "00000000-0000-0000-0000-00000000000Z"})
    void malformedIdsAreBadRequests(String id) throws Exception {
        assertProblem(request("GET", "/" + id, ""), 400);
        assertProblem(request("POST", "/" + id + "/heartbeat", ""), 400);
        assertProblem(request("POST", "/" + id + "/win", "{\"remainingSeconds\":10}"), 400);
        assertProblem(request("POST", "/" + id + "/lose", ""), 400);
        assertProblem(request("POST", "/" + id + "/email", "{\"email\":\"a@example.com\"}"), 400);
    }
    @Test void missingIdsAreBadRequests() throws Exception {
        assertProblem(request("GET", "", ""), 400);
        for (String action : List.of("heartbeat", "win", "lose", "email")) {
            assertProblem(request("POST", "/" + action, ""), 400);
        }
    }
    @Test void unknownIdsAreNotFoundForEveryOperation() throws Exception {
        String id = "/" + UUID.randomUUID();
        assertProblem(request("GET", id, ""), 404);
        assertProblem(request("POST", id + "/heartbeat", ""), 404);
        assertProblem(request("POST", id + "/win", "{\"remainingSeconds\":10}"), 404);
        assertProblem(request("POST", id + "/lose", ""), 404);
        assertProblem(request("POST", id + "/email", "{\"email\":\"a@example.com\"}"), 404);
    }
    @ParameterizedTest @ValueSource(strings = {"", "{}", "null", "{", "{\"remainingSeconds\":null}",
            "{\"remainingSeconds\":-1}", "{\"remainingSeconds\":91}", "{\"remainingSeconds\":1.5}",
            "{\"remainingSeconds\":\"no\"}", "{\"remainingSeconds\":12,\"status\":\"LOSE\"}"})
    void invalidWinBodiesNeverFinishSession(String body) throws Exception {
        var s = start(); assertProblem(request("POST", path(s, "win"), body), 400);
        assertThat(get(s)).isEqualTo(s);
    }
    @ParameterizedTest @ValueSource(ints = {0, 90})
    void inclusiveWinBoundsAreAccepted(int remaining) throws Exception {
        var s = start();
        assertThat(request("POST", path(s, "win"), "{\"remainingSeconds\":" + remaining + "}").statusCode()).isEqualTo(200);
        assertThat(get(s).remainingSeconds()).isEqualTo(remaining);
    }
    @ParameterizedTest @ValueSource(strings = {"", "{}", "{\"email\":null}", "{\"email\":\"   \"}",
            "{\"email\":\"not-an-email\"}", "{\"email\":\"a@@example.com\"}"})
    void invalidEmailDoesNotMutateSession(String body) throws Exception {
        var s = start(); assertProblem(request("POST", path(s, "email"), body), 400);
        assertThat(get(s)).isEqualTo(s);
    }
    @Test void overlongEmailIsRejected() throws Exception {
        var s = start();
        assertProblem(request("POST", path(s, "email"), "{\"email\":\"" + "a".repeat(255) + "@example.com\"}"), 400);
        assertThat(get(s).email()).isNull();
    }
    @Test void concurrentWinAndLoseHaveExactlyOneWinner() throws Exception {
        var s = start();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var ready = new CountDownLatch(2); var go = new CountDownLatch(1);
            Callable<Integer> win = () -> { ready.countDown(); go.await(); return request("POST", path(s, "win"), "{\"remainingSeconds\":10}").statusCode(); };
            Callable<Integer> lose = () -> { ready.countDown(); go.await(); return request("POST", path(s, "lose"), "").statusCode(); };
            var winning = executor.submit(win); var losing = executor.submit(lose);
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue(); go.countDown();
            assertThat(List.of(winning.get(15, TimeUnit.SECONDS), losing.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
            assertThat(get(s).status()).isIn(SessionStatus.WIN, SessionStatus.LOSE);
            assertThat(repository.count()).isEqualTo(1);
        }
    }
    @Test void databaseEnforcesPublicIdentifierUniqueness() throws Exception {
        var s = start();
        assertThatThrownBy(() -> jdbc.update("INSERT INTO game_sessions(session_id,status,started_at,last_seen_at) VALUES(?, 'PLAYING', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", s.sessionId()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(repository.count()).isEqualTo(1);
    }

    SessionResponse start() throws Exception {
        var response = request("POST", "/start", ""); assertThat(response.statusCode()).isEqualTo(201);
        return session(response);
    }
    SessionResponse get(SessionResponse s) throws Exception {
        var response = request("GET", "/" + s.sessionId(), ""); assertThat(response.statusCode()).isEqualTo(200);
        return session(response);
    }
    void finish(SessionResponse s, SessionStatus status) throws Exception {
        var response = request("POST", path(s, status == SessionStatus.WIN ? "win" : "lose"),
                status == SessionStatus.WIN ? "{\"remainingSeconds\":10}" : "");
        assertThat(response.statusCode()).isEqualTo(200);
    }
    String path(SessionResponse s, String action) { return "/" + s.sessionId() + "/" + action; }
    SessionResponse session(HttpResponse<String> response) { return mapper.readValue(response.body(), SessionResponse.class); }
    HttpResponse<String> request(String method, String path, String body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/sessions" + path))
                .timeout(Duration.ofSeconds(15)).header("Content-Type", "application/json")
                .method(method, body.isEmpty() ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }
    void assertProblem(HttpResponse<String> response, int status) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
        assertThat(response.headers().firstValue("Content-Type").orElse("")).contains("application/problem+json");
        assertThat(mapper.readValue(response.body(), Map.class).get("status")).isEqualTo(status);
    }
    static class MutableClock extends Clock {
        private final AtomicReference<Instant> time = new AtomicReference<>(START);
        void set(Instant instant) { time.set(instant); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(instant(), zone); }
        @Override public Instant instant() { return time.get(); }
    }
    @TestConfiguration static class TestTime {
        @Bean @Primary MutableClock testClock() { return new MutableClock(); }
    }
}
