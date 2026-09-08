package com.rescueroom.backend.admin;

import com.rescueroom.backend.session.SessionStatus;
import java.time.Instant;
import java.util.UUID;

public record AdminSession(UUID sessionId, String email, SessionStatus status, Instant startedAt,
        Instant finishedAt, Integer remainingSeconds, Instant lastSeenAt, boolean activeNow) { }
