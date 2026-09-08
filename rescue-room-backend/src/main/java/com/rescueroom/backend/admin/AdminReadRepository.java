package com.rescueroom.backend.admin;

import com.rescueroom.backend.session.SessionStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/** Read-only SQL, independent of lifecycle persistence and state changes. */
@Repository
public class AdminReadRepository {
    private final NamedParameterJdbcTemplate jdbc;
    public AdminReadRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public AdminStatistics statistics(Instant now, int threshold) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) AS total,
                  COALESCE(SUM(CASE WHEN status='PLAYING' AND last_seen_at BETWEEN :cutoff AND :now THEN 1 ELSE 0 END),0) AS active,
                  COALESCE(SUM(CASE WHEN status='WIN' THEN 1 ELSE 0 END),0) AS wins,
                  COALESCE(SUM(CASE WHEN status='LOSE' THEN 1 ELSE 0 END),0) AS losses,
                  COALESCE(SUM(CASE WHEN email IS NOT NULL AND TRIM(email)<>'' THEN 1 ELSE 0 END),0) AS emails
                FROM game_sessions
                """, times(now, threshold), (rs, row) -> new AdminStatistics(rs.getLong("total"),
                rs.getLong("active"), rs.getLong("wins"), rs.getLong("losses"), rs.getLong("emails"), threshold, now));
    }

    public AdminSessionPage sessions(SessionStatus status, String search, int page, int size, Instant now, int threshold) {
        var parameters = times(now, threshold).addValue("limit", size).addValue("offset", (long) page * size);
        String where = " WHERE 1=1";
        if (status != null) { where += " AND status=:status"; parameters.addValue("status", status.name()); }
        if (!search.isEmpty()) {
            where += " AND (LOWER(CAST(session_id AS VARCHAR(36))) LIKE :search ESCAPE '!' OR LOWER(email) LIKE :search ESCAPE '!')";
            parameters.addValue("search", "%" + search.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%");
        }
        long count = jdbc.queryForObject("SELECT COUNT(*) FROM game_sessions" + where, parameters, Long.class);
        var rows = jdbc.query("SELECT * FROM game_sessions" + where + " ORDER BY started_at DESC, id DESC LIMIT :limit OFFSET :offset",
                parameters, (rs, row) -> {
                    Instant seen = instant(rs, "last_seen_at");
                    SessionStatus state = SessionStatus.valueOf(rs.getString("status"));
                    boolean active = state == SessionStatus.PLAYING && !seen.isBefore(now.minusSeconds(threshold)) && !seen.isAfter(now);
                    return new AdminSession(rs.getObject("session_id", UUID.class), rs.getString("email"), state,
                            instant(rs, "started_at"), instant(rs, "finished_at"), rs.getObject("remaining_seconds", Integer.class), seen, active);
                });
        return new AdminSessionPage(rows, count, page, size, (count + size - 1) / size);
    }
    private MapSqlParameterSource times(Instant now, int threshold) {
        return new MapSqlParameterSource("now", now.atOffset(ZoneOffset.UTC))
                .addValue("cutoff", now.minusSeconds(threshold).atOffset(ZoneOffset.UTC));
    }
    private Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
