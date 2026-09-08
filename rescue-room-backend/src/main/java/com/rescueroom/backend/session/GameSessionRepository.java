package com.rescueroom.backend.session;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

public interface GameSessionRepository extends JpaRepository<GameSession, Long> {
    Optional<GameSession> findBySessionId(UUID sessionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from GameSession s where s.sessionId = :sessionId")
    Optional<GameSession> findForUpdate(@Param("sessionId") UUID sessionId);
}
