package com.rescueroom.backend.session;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "game_sessions")
public class GameSession {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "session_id", nullable = false, unique = true, updatable = false)
    private UUID sessionId;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 16)
    private SessionStatus status;
    @Column(length = 254)
    private String email;
    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;
    @Column(name = "finished_at")
    private Instant finishedAt;
    @Column(name = "remaining_seconds")
    private Integer remainingSeconds;
    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    protected GameSession() { }
    GameSession(UUID sessionId, Instant now) {
        this.sessionId = sessionId;
        this.status = SessionStatus.PLAYING;
        this.startedAt = now;
        this.lastSeenAt = now;
    }
    void heartbeat(Instant now) { lastSeenAt = now; }
    void finish(SessionStatus outcome, Integer remaining, Instant now) {
        status = outcome;
        finishedAt = now;
        remainingSeconds = remaining;
        lastSeenAt = now;
    }
    void saveEmail(String email) { this.email = email; }
    public Long getId() { return id; }
    public UUID getSessionId() { return sessionId; }
    public SessionStatus getStatus() { return status; }
    public String getEmail() { return email; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public Integer getRemainingSeconds() { return remainingSeconds; }
    public Instant getLastSeenAt() { return lastSeenAt; }
}
