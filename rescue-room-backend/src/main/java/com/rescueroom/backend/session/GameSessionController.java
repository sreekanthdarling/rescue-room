package com.rescueroom.backend.session;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.net.URI;

@RestController
@RequestMapping("/api/sessions")
public class GameSessionController {
    private final GameSessionService service;
    public GameSessionController(GameSessionService service) { this.service = service; }

    @PostMapping("/start")
    public ResponseEntity<SessionResponse> start() {
        SessionResponse session = service.start();
        return ResponseEntity.created(URI.create("/api/sessions/" + session.sessionId())).body(session);
    }
    @GetMapping("/{sessionId}")
    public SessionResponse get(@PathVariable String sessionId) { return service.get(sessionId); }
    @PostMapping("/{sessionId}/heartbeat")
    public SessionResponse heartbeat(@PathVariable String sessionId) { return service.heartbeat(sessionId); }
    @PostMapping("/{sessionId}/win")
    public SessionResponse win(@PathVariable String sessionId, @Valid @RequestBody WinRequest request) {
        return service.win(sessionId, request.remainingSeconds());
    }
    @PostMapping("/{sessionId}/lose")
    public SessionResponse lose(@PathVariable String sessionId) { return service.lose(sessionId); }
    @PostMapping("/{sessionId}/email")
    public SessionResponse email(@PathVariable String sessionId, @Valid @RequestBody EmailRequest request) {
        return service.email(sessionId, request);
    }
    @GetMapping({"", "/"})
    public void missingReadId() { throw missingId(); }
    @PostMapping({"/heartbeat", "/win", "/lose", "/email"})
    public void missingWriteId() { throw missingId(); }
    private ResponseStatusException missingId() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "sessionId is required");
    }
}
