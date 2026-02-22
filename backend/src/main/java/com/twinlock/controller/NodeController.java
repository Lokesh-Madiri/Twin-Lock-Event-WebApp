package com.twinlock.controller;

import com.twinlock.model.SubmitRequest;
import com.twinlock.service.TwinLockService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/node")
public class NodeController {

    private final TwinLockService service;

    public NodeController(TwinLockService service) {
        this.service = service;
    }

    /**
     * GET /api/node/status?teamId=ALPHA&nodeId=SYS-01
     * Polled every 2-3 seconds by the terminal client.
     * Returns: eventActive, cipher (if active), timeRemaining, attemptsRemaining,
     * nodeLocked
     */
    @GetMapping("/status")
    public Map<String, Object> status(
            @RequestParam String teamId,
            @RequestParam String nodeId) {
        return service.getNodeStatus(
                teamId.toUpperCase().trim(),
                nodeId.toUpperCase().trim());
    }

    /**
     * POST /api/node/submit
     * Body: { teamId, nodeId, payload } payload = "innovation-133"
     * Returns: { status: UNLOCK|FAIL|LOCKED, formLink?, attemptsRemaining? }
     */
    @PostMapping("/submit")
    public Map<String, Object> submit(@RequestBody SubmitRequest req) {
        String teamId = req.getTeamId() != null ? req.getTeamId().toUpperCase().trim() : "";
        String nodeId = req.getNodeId() != null ? req.getNodeId().toUpperCase().trim() : "";
        String payload = req.getPayload() != null ? req.getPayload().trim() : "";
        return service.submit(teamId, nodeId, payload);
    }
}
