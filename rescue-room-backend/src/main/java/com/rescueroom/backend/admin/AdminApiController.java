package com.rescueroom.backend.admin;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
public class AdminApiController {
    private final AdminReadService service;
    public AdminApiController(AdminReadService service) { this.service = service; }
    @GetMapping("/statistics")
    public ResponseEntity<AdminStatistics> statistics() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.statistics());
    }
    @GetMapping("/sessions")
    public ResponseEntity<AdminSessionPage> sessions(@RequestParam(defaultValue = "ALL") String status,
            @RequestParam(defaultValue = "") String search, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.sessions(status, search, page, size));
    }
}
