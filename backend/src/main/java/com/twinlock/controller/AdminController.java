package com.twinlock.controller;

import com.twinlock.service.TwinLockService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Admin endpoints — all require X-Admin-Key header.
 *
 * POST /api/admin/start — Start the event (opens decryption window)
 * POST /api/admin/end — End the event early
 * GET /api/admin/status — See all node sessions
 * POST /api/admin/reset-node — Reset a specific node (unlock + re-enable)
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @Value("${twinlock.admin-key:TWINLOCK_ADMIN_2024}")
    private String adminKey;

    private final TwinLockService service;

    public AdminController(TwinLockService service) {
        this.service = service;
    }

    // ── Auth guard ────────────────────────────────────────────────
    private boolean unauthorized(String key) {
        return key == null || !key.equals(adminKey);
    }

    // ── Start event ───────────────────────────────────────────────
    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> startEvent(
            @RequestHeader(value = "X-Admin-Key", required = false) String key) {
        if (unauthorized(key))
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid admin key"));
        return ResponseEntity.ok(service.startEvent());
    }

    // ── End event ─────────────────────────────────────────────────
    @PostMapping("/end")
    public ResponseEntity<Map<String, Object>> endEvent(
            @RequestHeader(value = "X-Admin-Key", required = false) String key) {
        if (unauthorized(key))
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid admin key"));
        service.endEvent();
        return ResponseEntity.ok(Map.of(
                "status", "ENDED",
                "message", "Event ended. All windows sealed."));
    }

    // ── Status dashboard ──────────────────────────────────────────
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus(
            @RequestHeader(value = "X-Admin-Key", required = false) String key) {
        if (unauthorized(key))
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid admin key"));
        return ResponseEntity.ok(service.getAdminStatus());
    }

    // ── Reset a node ──────────────────────────────────────────────
    @PostMapping("/reset-node")
    public ResponseEntity<Map<String, Object>> resetNode(
            @RequestHeader(value = "X-Admin-Key", required = false) String key,
            @RequestBody Map<String, String> body) {
        if (unauthorized(key))
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid admin key"));
        String teamId = body.getOrDefault("teamId", "").toUpperCase().trim();
        String nodeId = body.getOrDefault("nodeId", "").toUpperCase().trim();
        service.resetNode(teamId, nodeId);
        return ResponseEntity.ok(Map.of(
                "status", "RESET",
                "teamId", teamId,
                "nodeId", nodeId));
    }

    // ── Credential sheet (all teams + keys) ──────────────────────
    @GetMapping("/credentials")
    public ResponseEntity<?> getCredentials(
            @RequestHeader(value = "X-Admin-Key", required = false) String key) {
        if (unauthorized(key))
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid admin key"));
        return ResponseEntity.ok(service.getCredentialsSheet());
    }
}
