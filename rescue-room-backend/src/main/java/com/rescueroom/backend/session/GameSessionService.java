package com.rescueroom.backend.session;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class GameSessionService {
    private final GameSessionRepository repository;
    private final Clock clock;
    public GameSessionService(GameSessionRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public SessionResponse start() {
        return SessionResponse.from(repository.save(new GameSession(UUID.randomUUID(), now())));
    }
    public SessionResponse get(String sessionId) {
        return SessionResponse.from(repository.findBySessionId(parseId(sessionId)).orElseThrow(this::notFound));
    }
    @Transactional
    public SessionResponse heartbeat(String sessionId) {
        GameSession session = locked(sessionId);
        requirePlaying(session);
        session.heartbeat(now());
        return SessionResponse.from(session);
    }
    @Transactional
    public SessionResponse win(String sessionId, Integer remainingSeconds) {
        if (remainingSeconds == null || remainingSeconds < 0 || remainingSeconds > 90) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "remainingSeconds must be an integer from 0 to 90");
        }
        return finish(sessionId, SessionStatus.WIN, remainingSeconds);
    }
    @Transactional
    public SessionResponse lose(String sessionId) { return finish(sessionId, SessionStatus.LOSE, 0); }
    @Transactional
    public SessionResponse email(String sessionId, EmailRequest request) {
        GameSession session = locked(sessionId);
        session.saveEmail(request.email());
        return SessionResponse.from(session);
    }

    private SessionResponse finish(String id, SessionStatus status, Integer remaining) {
        GameSession session = locked(id);
        requirePlaying(session);
        session.finish(status, remaining, now());
        return SessionResponse.from(session);
    }
    private GameSession locked(String id) {
        return repository.findForUpdate(parseId(id)).orElseThrow(this::notFound);
    }
    private Instant now() {
        // H2 TIMESTAMP(6) stores microseconds; response values must round-trip unchanged.
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
    private void requirePlaying(GameSession session) {
        if (session.getStatus() != SessionStatus.PLAYING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Session has already finished as " + session.getStatus());
        }
    }
    private UUID parseId(String id) {
        if (id == null || !id.matches("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sessionId must be a canonical UUID");
        }
        return UUID.fromString(id);
    }
    private ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found");
    }
}
