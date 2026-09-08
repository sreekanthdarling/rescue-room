package com.rescueroom.backend.session;

import java.time.Instant;
import java.util.UUID;

public record SessionResponse(Long id, UUID sessionId, SessionStatus status, String email,
        Instant startedAt, Instant finishedAt, Integer remainingSeconds, Instant lastSeenAt) {
    static SessionResponse from(GameSession s) {
        return new SessionResponse(s.getId(), s.getSessionId(), s.getStatus(), s.getEmail(),
                s.getStartedAt(), s.getFinishedAt(), s.getRemainingSeconds(), s.getLastSeenAt());
    }
}
