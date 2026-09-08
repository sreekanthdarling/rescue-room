package com.rescueroom.backend.admin;

import com.rescueroom.backend.session.SessionStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.Clock;
import java.util.Locale;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class AdminReadService {
    private final AdminReadRepository repository;
    private final AdminConfiguration.Settings settings;
    private final Clock clock;
    public AdminReadService(AdminReadRepository repository, AdminConfiguration.Settings settings, Clock clock) {
        this.repository = repository; this.settings = settings; this.clock = clock;
    }
    public AdminStatistics statistics() { return repository.statistics(clock.instant(), settings.inactivitySeconds()); }
    public AdminSessionPage sessions(String status, String search, int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw invalid("page must be nonnegative and size must be between 1 and 100");
        String query = search.strip().toLowerCase(Locale.ROOT);
        if (query.length() > 254) throw invalid("Search must be at most 254 characters");
        SessionStatus filter = null;
        if (!status.equalsIgnoreCase("ALL")) {
            try { filter = SessionStatus.valueOf(status.toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException error) { throw invalid("status must be ALL, PLAYING, WIN or LOSE"); }
        }
        return repository.sessions(filter, query, page, size, clock.instant(), settings.inactivitySeconds());
    }
    private ResponseStatusException invalid(String detail) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, detail); }
}
