package com.twinlock.model;

public class NodeSession {
    private String teamId;
    private String nodeId;
    private boolean authenticated;
    private int currentLevel = 1; // 1=EASY 2=MEDIUM 3=HARD
    private int levelAttempts = 0; // attempts used in THIS level (max 3)
    private boolean unlocked;
    private boolean permanentlyLocked;

    public NodeSession(String teamId, String nodeId) {
        this.teamId = teamId;
        this.nodeId = nodeId;
    }

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

    public int getCurrentLevel() {
        return currentLevel;
    }

    public void advanceLevel() {
        this.currentLevel++;
        this.levelAttempts = 0;
    }

    public int getLevelAttempts() {
        return levelAttempts;
    }

    public void incrementLevelAttempts() {
        this.levelAttempts++;
    }

    public int getLevelAttemptsRemaining() {
        return Math.max(0, 3 - levelAttempts);
    }

    // kept for admin/compat
    public int getAttempts() {
        return levelAttempts;
    }

    public int getAttemptsRemaining() {
        return getLevelAttemptsRemaining();
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
