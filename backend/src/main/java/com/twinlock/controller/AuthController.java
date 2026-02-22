package com.twinlock.controller;

import com.twinlock.model.LoginRequest;
import com.twinlock.service.TwinLockService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final TwinLockService service;

    public AuthController(TwinLockService service) {
        this.service = service;
    }

    /** POST /api/auth/login — validate team/node credentials */
    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody LoginRequest req) {
        String teamId = req.getTeamId() != null ? req.getTeamId().toUpperCase().trim() : "";
        String nodeId = req.getNodeId() != null ? req.getNodeId().toUpperCase().trim() : "";
        String accessKey = req.getAccessKey() != null ? req.getAccessKey().trim() : "";
        return service.login(teamId, nodeId, accessKey);
    }

    /** POST /api/auth/restore — restore session after page refresh */
    @PostMapping("/restore")
    public Map<String, Object> restore(@RequestBody Map<String, String> body) {
        String teamId = body.getOrDefault("teamId", "").toUpperCase().trim();
        String nodeId = body.getOrDefault("nodeId", "").toUpperCase().trim();
        return service.restoreSession(teamId, nodeId);
    }
}
