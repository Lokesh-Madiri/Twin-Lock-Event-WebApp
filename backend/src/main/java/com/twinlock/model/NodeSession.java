package com.twinlock.model;

/**
 * In-memory session for one node.
 * Key: teamId + "_" + nodeId
 */
public class NodeSession {
    private String teamId;
    private String nodeId;
    private boolean authenticated;
    private int attempts; // attempts USED (not remaining)
    private boolean unlocked;
    private boolean permanentlyLocked;

    public NodeSession(String teamId, String nodeId) {
        this.teamId = teamId;
        this.nodeId = nodeId;
        this.authenticated = false;
        this.attempts = 0;
        this.unlocked = false;
        this.permanentlyLocked = false;
    }

    // ── Getters / Setters ──────────────────────────────────────

    public String getTeamId() {
        return teamId;
    }

    public String getNodeId() {
        return nodeId;
    }

    public boolean isAuthenticated() {
        return authenticated;
    }

    public void setAuthenticated(boolean b) {
        this.authenticated = b;
    }

    public int getAttempts() {
        return attempts;
    }

    public void incrementAttempts() {
        this.attempts++;
    }

    public int getAttemptsRemaining() {
        return Math.max(0, 3 - attempts);
    }

    public boolean isUnlocked() {
        return unlocked;
    }

    public void setUnlocked(boolean b) {
        this.unlocked = b;
    }

    public boolean isPermanentlyLocked() {
        return permanentlyLocked;
    }

    public void setPermanentlyLocked(boolean b) {
        this.permanentlyLocked = b;
    }
}
