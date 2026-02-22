package com.twinlock.service;

import com.twinlock.model.NodeSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TwinLockService {

    private static final Logger log = LoggerFactory.getLogger(TwinLockService.class);

    // ════════════════════════════════════════════════════════════════
    // PUZZLE CONFIGURATIONS (5 different type combinations)
    // ════════════════════════════════════════════════════════════════

    private static class PuzzleSet {
        final String keyword;
        final int checksum; // sum of letter positions % 10000
        final String node1Type;
        final String node1Cipher;
        final String[] node1Hints;
        final String node2Type;
        final String node2Cipher;
        final String[] node2Hints;

        PuzzleSet(String kw, int cs,
                String n1t, String n1c, String[] n1h,
                String n2t, String n2c, String[] n2h) {
            keyword = kw;
            checksum = cs;
            node1Type = n1t;
            node1Cipher = n1c;
            node1Hints = n1h;
            node2Type = n2t;
            node2Cipher = n2c;
            node2Hints = n2h;
        }
    }

    /**
     * Team → Puzzle mapping.
     * SYS-01 always gets node1 cipher; SYS-02 gets node2 cipher.
     *
     * Checksums (A=1…Z=26, sum % 10000):
     * VICTORY = V22+I9+C3+T20+O15+R18+Y25 = 112
     * UNLOCK = U21+N14+L12+O15+C3+K11 = 76
     * FREEDOM = F6+R18+E5+E5+D4+O15+M13 = 66
     * CIPHER = C3+I9+P16+H8+E5+R18 = 59
     * SIGNAL = S19+I9+G7+N14+A1+L12 = 62
     */
    private static final Map<String, PuzzleSet> PUZZLES = new LinkedHashMap<>();
    static {

        // ── ALPHA: Caesar Cipher (SYS-01) + Number Pattern (SYS-02) → VICTORY ──
        // Caesar shift +7 applied to "VICTORY IS THE KEY TO SUCCESS"
        PUZZLES.put("ALPHA", new PuzzleSet(
                "victory", 112,
                "CAESAR CIPHER",
                "CPJAVYF PZ AOL RLF AV ZBJJLZZ",
                new String[] {
                        "[HINT 1] This is a Caesar Cipher. Every letter has been",
                        "         shifted FORWARD by a fixed number of positions.",
                        "[HINT 2] The shift value is 7. To decode: shift each",
                        "         cipher letter BACKWARDS by 7 positions.",
                        "         Example: C -> V, P -> I, J -> C ...",
                        "[HINT 3] The answer is the FIRST word of the decoded",
                        "         phrase. 7 letters long. Submit in lowercase."
                },
                "NUMBER PATTERN",
                "22 -- 09 -- 03 -- 20 -- 15 -- 18 -- 25",
                new String[] {
                        "[HINT 1] Each number = a letter's POSITION in the alphabet.",
                        "         A=1, B=2, C=3, ... Z=26.",
                        "[HINT 2] Map each number to its letter:",
                        "         01=A  05=E  09=I  14=N  15=O  18=R  22=V  25=Y",
                        "[HINT 3] 7 numbers = 7 letters. Submit the word lowercase."
                }));

        // ── BETA: Morse Code (SYS-01) + Math Sequence (SYS-02) → UNLOCK ──
        PUZZLES.put("BETA", new PuzzleSet(
                "unlock", 76,
                "MORSE CODE",
                "..- / -. / .-.. / --- / -.-. / -.-",
                new String[] {
                        "[HINT 1] This is International Morse Code.",
                        "         Each group separated by '/' = one letter.",
                        "[HINT 2] Dot (.) = dit (short).  Dash (-) = dah (long).",
                        "         Reference: ..-=U  -.=N  .-..=L  ---=O",
                        "[HINT 3] Decode all 6 groups left to right.",
                        "         6 groups = 6 letters. Submit the word lowercase."
                },
                "MATH SEQUENCE",
                "[ 3x7 ] -> [ 7x2 ] -> [ 4x3 ] -> [ 5x3 ] -> [ 9/3 ] -> [ 11x1 ]",
                new String[] {
                        "[HINT 1] Solve each math expression inside the brackets.",
                        "         Each result is a number between 1 and 26.",
                        "[HINT 2] Convert each result to a letter (A=1, Z=26).",
                        "         Example: 3x7=21 -> 21st letter of alphabet = U",
                        "[HINT 3] 6 expressions = 6 letters. Submit word lowercase."
                }));

        // ── GAMMA: Anagram (SYS-01) + Binary Decode (SYS-02) → FREEDOM ──
        PUZZLES.put("GAMMA", new PuzzleSet(
                "freedom", 66,
                "ANAGRAM",
                "SCRAMBLED: M -- O -- E -- R -- F -- D -- E",
                new String[] {
                        "[HINT 1] All 7 letters shown form a scrambled word.",
                        "         Rearrange ALL of them to find the answer.",
                        "[HINT 2] The word relates to liberation and independence.",
                        "         It contains a repeated letter.",
                        "[HINT 3] The answer is 7 letters long. Submit lowercase."
                },
                "BINARY DECODE",
                "00110 | 10010 | 00101 | 00101 | 00100 | 01111 | 01101",
                new String[] {
                        "[HINT 1] Each 5-bit binary group encodes one letter.",
                        "         Convert binary -> decimal (base-2 to base-10).",
                        "[HINT 2] Decimal = letter position  (A=1, B=2, ... Z=26).",
                        "         Example: 00001=1=A   00110=6=F   01101=13=M",
                        "[HINT 3] 7 groups = 7 letters. Submit the word lowercase."
                }));

        // ── DELTA: Polybius Square (SYS-01) + Logic Gate Binary (SYS-02) → CIPHER ──
        PUZZLES.put("DELTA", new PuzzleSet(
                "cipher", 59,
                "POLYBIUS SQUARE",
                "   [1][2][3][4][5]\n" +
                        "1: [A][B][C][D][E]\n" +
                        "2: [F][G][H][I][K]\n" +
                        "3: [L][M][N][O][P]\n" +
                        "4: [Q][R][S][T][U]\n" +
                        "5: [V][W][X][Y][Z]\n" +
                        "SEQUENCE: (1,3)-(2,4)-(3,5)-(2,3)-(1,5)-(4,2)",
                new String[] {
                        "[HINT 1] Use the 5x5 grid as a lookup table.",
                        "         Each pair (row,col) maps to one letter.",
                        "[HINT 2] Read row first, then column.",
                        "         Example: (1,3) = Row 1, Column 3 = C",
                        "[HINT 3] 6 coordinate pairs = 6 letters. Submit lowercase."
                },
                "LOGIC GATE OUTPUT",
                "GATE-1: 0.0.0.1.1\n" +
                        "GATE-2: 0.1.0.0.1\n" +
                        "GATE-3: 1.0.0.0.0\n" +
                        "GATE-4: 0.1.0.0.0\n" +
                        "GATE-5: 0.0.1.0.1\n" +
                        "GATE-6: 1.0.0.1.0\n" +
                        "[KEY: A=00001  Z=11010  dots separate bits]",
                new String[] {
                        "[HINT 1] Each GATE row is a 5-bit binary number.",
                        "         Ignore the dots — they just separate the bits.",
                        "[HINT 2] Convert each 5-bit binary to decimal.",
                        "         That decimal = letter position (A=1, Z=26).",
                        "[HINT 3] 6 gates = 6 letters. Submit the word lowercase."
                }));

        // ── SIGMA: Binary Decode (SYS-01) + Morse Code (SYS-02) → SIGNAL (cs=62) ──
        PUZZLES.put("SIGMA", new PuzzleSet(
                "signal", 62,
                "BINARY DECODE",
                "10011 | 01001 | 00111 | 01110 | 00001 | 01100",
                new String[] {
                        "[HINT 1] Each 5-bit binary group encodes one letter.",
                        "         Convert binary to decimal (base 2 → base 10).",
                        "[HINT 2] Decimal = letter position (A=1, B=2 ... Z=26).",
                        "[HINT 3] 6 groups = 6 letters. Submit the word lowercase."
                },
                "MORSE CODE",
                "... .. --. -. .- .-..",
                new String[] {
                        "[HINT 1] International Morse Code. Each group = one letter.",
                        "         Dot (.) = dit (short). Dash (-) = dah (long).",
                        "[HINT 2] ...=S  ..=I  --.=G  -.=N  .-=A  .-..=L",
                        "[HINT 3] 6 groups = 6 letters. Submit the word lowercase."
                }));

        // ── THETA: Anagram (SYS-01) + Number Pattern (SYS-02) → PHOENIX (cs=91) ──
        // PHOENIX = P16+H8+O15+E5+N14+I9+X24 = 91
        PUZZLES.put("THETA", new PuzzleSet(
                "phoenix", 91,
                "ANAGRAM",
                "SCRAMBLED: N -- X -- O -- P -- H -- I -- E",
                new String[] {
                        "[HINT 1] All 7 letters form a scrambled word.",
                        "         Rearrange ALL of them to find the answer.",
                        "[HINT 2] The word is a mythological bird that rises from ashes.",
                        "         It starts with the letter P.",
                        "[HINT 3] 7 letters long. Submit the word lowercase."
                },
                "NUMBER PATTERN",
                "16 -- 08 -- 15 -- 05 -- 14 -- 09 -- 24",
                new String[] {
                        "[HINT 1] Each number = a letter's POSITION in the alphabet.",
                        "         A=1, B=2, C=3 ... Z=26.",
                        "[HINT 2] Map numbers to letters:",
                        "         05=E  08=H  09=I  14=N  15=O  16=P  24=X",
                        "[HINT 3] 7 numbers = 7 letters. Submit the word lowercase."
                }));

        // ── KAPPA: Caesar+5 (SYS-01) + Binary (SYS-02) → QUANTUM (cs=107) ──
        // QUANTUM = Q17+U21+A1+N14+T20+U21+M13 = 107
        // Caesar shift +5: "QUANTUM IS THE KEY TO POWER" → "VZFSYZR NX YMJ PJD YT
        // UTBJW"
        PUZZLES.put("KAPPA", new PuzzleSet(
                "quantum", 107,
                "CAESAR CIPHER",
                "VZFSYZR NX YMJ PJD YT UTBJW",
                new String[] {
                        "[HINT 1] This is a Caesar Cipher. Shift every letter BACK",
                        "         by a fixed number of positions to decode.",
                        "[HINT 2] The shift value is 5. Example: V→Q, Z→U, F→A.",
                        "[HINT 3] The answer is the FIRST word of the decoded phrase.",
                        "         7 letters long. Submit in lowercase."
                },
                "BINARY DECODE",
                "10001 | 10101 | 00001 | 01110 | 10100 | 10101 | 01101",
                new String[] {
                        "[HINT 1] Each 5-bit binary group encodes one letter.",
                        "         Convert binary to decimal (base 2 → base 10).",
                        "[HINT 2] Decimal = letter position (A=1, B=2 ... Z=26).",
                        "         10001=17=Q  10101=21=U  00001=1=A",
                        "[HINT 3] 7 groups = 7 letters. Submit the word lowercase."
                }));

        // ── LAMBDA: Morse (SYS-01) + Polybius (SYS-02) → VORTEX (cs=104) ──
        // VORTEX = V22+O15+R18+T20+E5+X24 = 104
        PUZZLES.put("LAMBDA", new PuzzleSet(
                "vortex", 104,
                "MORSE CODE",
                "...- / --- / .-. / - / . / -..-",
                new String[] {
                        "[HINT 1] International Morse Code. Each group separated by '/'",
                        "         represents one letter of the answer.",
                        "[HINT 2] ...-=V  ---=O  .-.=R  -=T  .=E  -..=X",
                        "[HINT 3] 6 groups = 6 letters. Submit the word lowercase."
                },
                "POLYBIUS SQUARE",
                "   [1][2][3][4][5]\n" +
                        "1: [A][B][C][D][E]\n" +
                        "2: [F][G][H][I][K]\n" +
                        "3: [L][M][N][O][P]\n" +
                        "4: [Q][R][S][T][U]\n" +
                        "5: [V][W][X][Y][Z]\n" +
                        "SEQUENCE: (5,1)-(3,4)-(4,2)-(4,4)-(1,5)-(5,3)",
                new String[] {
                        "[HINT 1] Use the 5x5 grid to look up each letter.",
                        "         Each pair (row, column) maps to one letter.",
                        "[HINT 2] Read row first, then column.",
                        "         Example: (5,1) = Row 5, Column 1 = V",
                        "[HINT 3] 6 coordinate pairs = 6 letters. Submit lowercase."
                }));

        // ── MU: Number Pattern (SYS-01) + Math Sequence (SYS-02) → ZENITH (cs=82) ──
        // ZENITH = Z26+E5+N14+I9+T20+H8 = 82
        PUZZLES.put("MU", new PuzzleSet(
                "zenith", 82,
                "NUMBER PATTERN",
                "26 -- 05 -- 14 -- 09 -- 20 -- 08",
                new String[] {
                        "[HINT 1] Each number = a letter's POSITION in the alphabet.",
                        "         A=1, B=2, C=3 ... Z=26.",
                        "[HINT 2] Map numbers to letters:",
                        "         05=E  08=H  09=I  14=N  20=T  26=Z",
                        "[HINT 3] 6 numbers = 6 letters. Submit the word lowercase."
                },
                "MATH SEQUENCE",
                "[ 13x2 ] -> [ 15-10 ] -> [ 7x2 ] -> [ 3x3 ] -> [ 4x5 ] -> [ 2x4 ]",
                new String[] {
                        "[HINT 1] Solve each math expression inside the brackets.",
                        "         Each result is a number between 1 and 26.",
                        "[HINT 2] Convert each result to a letter (A=1, Z=26).",
                        "         13x2=26=Z  15-10=5=E  7x2=14=N",
                        "[HINT 3] 6 expressions = 6 letters. Submit word lowercase."
                }));

        // ── NU: Binary (SYS-01) + Caesar+3 (SYS-02) → ENIGMA (cs=49) ──
        // ENIGMA = E5+N14+I9+G7+M13+A1 = 49
        // Caesar shift+3: "ENIGMA IS THE HIDDEN MYSTERY" → "HQLJPD LV WKH KLGGHQ
        // PBVWHUB"
        PUZZLES.put("NU", new PuzzleSet(
                "enigma", 49,
                "BINARY DECODE",
                "00101 | 01110 | 01001 | 00111 | 01101 | 00001",
                new String[] {
                        "[HINT 1] Each 5-bit binary group encodes one letter.",
                        "         Convert binary to decimal (base 2 → base 10).",
                        "[HINT 2] Decimal = letter position (A=1, B=2 ... Z=26).",
                        "         00101=5=E  01110=14=N  01001=9=I",
                        "[HINT 3] 6 groups = 6 letters. Submit the word lowercase."
                },
                "CAESAR CIPHER",
                "HQLJPD LV WKH KLGGHQ PBVWHUB",
                new String[] {
                        "[HINT 1] This is a Caesar Cipher. Shift every letter BACK",
                        "         by a fixed number of positions to decode.",
                        "[HINT 2] The shift value is 3. Example: H→E, Q→N, L→I.",
                        "[HINT 3] The answer is the FIRST word of the decoded phrase.",
                        "         6 letters long. Submit in lowercase."
                }));
    }

    // ── Spring Config ─────────────────────────────────────────────
    @Value("${twinlock.duration-minutes:30}")
    private int durationMinutes;

    // Set duration-seconds > 0 to override duration-minutes (useful for testing)
    @Value("${twinlock.duration-seconds:0}")
    private int durationSeconds;

    @Value("${twinlock.secret-salt:TWINLOCK_DEFAULT_SALT}")
    private String secretSalt;

    // Auto-team generation: if team-count > 0, all credentials are derived
    // automatically
    @Value("${twinlock.team-count:0}")
    private int teamCount;

    @Value("${twinlock.team-prefix:TEAM}")
    private String teamPrefix;

    @Value("${twinlock.google-form-link-node1:https://forms.gle/REPLACEME_NODE1}")
    private String googleFormLinkNode1;

    @Value("${twinlock.google-form-link-node2:https://forms.gle/REPLACEME_NODE2}")
    private String googleFormLinkNode2;

    @Value("${twinlock.hint-cooldown-minutes:5}")
    private int hintCooldownMinutes;

    private final Environment env;

    // ── In-Memory State ───────────────────────────────────────────
    private final ConcurrentHashMap<String, NodeSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, String> credentials = new HashMap<>();

    private volatile boolean eventStarted = false;
    private volatile LocalDateTime eventStartTime = null;

    public TwinLockService(Environment env) {
        this.env = env;
    }

    // ── Credential init: auto-generate OR load from properties ──────
    @PostConstruct
    public void init() {
        if (teamCount > 0) {
            // AUTO MODE — derive all credentials from team count + secret salt
            log.info("[INIT] Auto-generating credentials for {} teams ({}{:02d}...{}{})",
                    teamCount, teamPrefix, 1, teamPrefix, teamCount);
            for (int i = 1; i <= teamCount; i++) {
                String teamId = teamPrefix + String.format("%02d", i);
                credentials.put(teamId + "_SYS-01", deriveKey(teamId, "SYS-01"));
                credentials.put(teamId + "_SYS-02", deriveKey(teamId, "SYS-02"));
            }
            log.info("[INIT] Auto-generated {} credential pairs.", teamCount);
        } else {
            // MANUAL MODE — load twinlock.cred.* from application.properties
            try {
                ((org.springframework.core.env.AbstractEnvironment) env)
                        .getPropertySources().forEach(ps -> {
                            if (ps instanceof org.springframework.core.env.EnumerablePropertySource<?> eps) {
                                for (String name : eps.getPropertyNames()) {
                                    if (name.startsWith("twinlock.cred.")) {
                                        String rest = name.substring("twinlock.cred.".length());
                                        int dot = rest.lastIndexOf('.');
                                        if (dot > 0) {
                                            String teamId = rest.substring(0, dot).toUpperCase();
                                            String nodeId = rest.substring(dot + 1).toUpperCase();
                                            credentials.put(teamId + "_" + nodeId, env.getProperty(name));
                                            log.info("[CRED] Loaded: {} / {}", teamId, nodeId);
                                        }
                                    }
                                }
                            }
                        });
            } catch (Exception e) {
                log.error("Failed to load credentials", e);
            }
        }
        log.info("[INIT] TwinLock ready. {} credentials loaded, {} puzzle configs.",
                credentials.size(), PUZZLES.size());
    }

    /**
     * Derives an 8-char uppercase key from teamId+nodeId using HMAC-SHA256 +
     * secretSalt.
     */
    private String deriveKey(String teamId, String nodeId) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretSalt.getBytes("UTF-8"), "HmacSHA256"));
            byte[] hash = mac.doFinal((teamId + "_" + nodeId).getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash)
                sb.append(String.format("%02X", b));
            return sb.substring(0, 8); // 8 uppercase hex chars
        } catch (Exception e) {
            return (teamId + nodeId).toUpperCase().replaceAll("[^A-Z0-9]", "").substring(0, 8);
        }
    }

    // ════════════════════════════════════════════════════════════════
    // AUTH
    // ════════════════════════════════════════════════════════════════

    public Map<String, Object> login(String teamId, String nodeId, String accessKey) {
        Map<String, Object> resp = new HashMap<>();
        String expected = credentials.get(teamId + "_" + nodeId);
        if (expected == null || !expected.equals(accessKey)) {
            resp.put("status", "FAIL");
            return resp;
        }
        sessions.computeIfAbsent(teamId + "_" + nodeId, k -> new NodeSession(teamId, nodeId))
                .setAuthenticated(true);
        resp.put("status", "OK");
        resp.put("teamId", teamId);
        resp.put("nodeId", nodeId);
        log.info("[AUTH] {} / {} authenticated", teamId, nodeId);
        return resp;
    }

    public Map<String, Object> restoreSession(String teamId, String nodeId) {
        Map<String, Object> resp = new HashMap<>();
        NodeSession s = sessions.get(teamId + "_" + nodeId);
        if (s == null || !s.isAuthenticated()) {
            resp.put("status", "FAIL");
            return resp;
        }
        resp.put("status", "OK");
        resp.put("teamId", teamId);
        resp.put("nodeId", nodeId);
        resp.put("attemptsRemaining", s.getAttemptsRemaining());
        resp.put("eventActive", isActive());
        return resp;
    }

    // ════════════════════════════════════════════════════════════════
    // NODE STATUS (polled every 2-3s)
    // ════════════════════════════════════════════════════════════════

    public Map<String, Object> getNodeStatus(String teamId, String nodeId) {
        Map<String, Object> resp = new HashMap<>();
        NodeSession s = sessions.get(teamId + "_" + nodeId);
        boolean active = isActive();
        resp.put("eventActive", active);

        if (s == null) {
            resp.put("authenticated", false);
            return resp;
        }

        resp.put("authenticated", true);
        resp.put("attemptsRemaining", s.getAttemptsRemaining());
        resp.put("nodeLocked", s.isPermanentlyLocked());
        resp.put("unlocked", s.isUnlocked());

        // Partner node status (same team, different node)
        final String[] partnerNodeId = { "" };
        final boolean[] partnerUnlocked = { false };
        final boolean[] partnerConnected = { false };
        sessions.forEach((k, sess) -> {
            if (teamId.equals(sess.getTeamId()) && !nodeId.equals(sess.getNodeId()) && sess.isAuthenticated()) {
                partnerNodeId[0] = sess.getNodeId();
                partnerUnlocked[0] = sess.isUnlocked();
                partnerConnected[0] = true;
            }
        });
        resp.put("partnerConnected", partnerConnected[0]);
        resp.put("partnerUnlocked", partnerUnlocked[0]);
        if (partnerConnected[0])
            resp.put("partnerNodeId", partnerNodeId[0]);

        if (active) {
            resp.put("timeRemainingSeconds", getTimeRemainingSeconds());
            resp.put("cipher", getCipherText(teamId, nodeId));
            resp.put("cipherType", getCipherType(teamId, nodeId));
            resp.put("hints", Arrays.asList(getHints(teamId, nodeId)));
        }
        return resp;
    }

    // ════════════════════════════════════════════════════════════════
    // SUBMIT
    // ════════════════════════════════════════════════════════════════

    public Map<String, Object> submit(String teamId, String nodeId, String payload) {
        Map<String, Object> resp = new HashMap<>();
        NodeSession s = sessions.get(teamId + "_" + nodeId);

        if (s == null || !s.isAuthenticated()) {
            resp.put("status", "FAIL");
            resp.put("message", "Not authenticated");
            return resp;
        }
        if (s.isPermanentlyLocked() || s.isUnlocked()) {
            resp.put("status", "LOCKED");
            return resp;
        }
        if (!isActive()) {
            resp.put("status", "FAIL");
            resp.put("message", "Event not active");
            return resp;
        }
        if (s.getAttemptsRemaining() <= 0) {
            s.setPermanentlyLocked(true);
            resp.put("status", "LOCKED");
            return resp;
        }

        PuzzleSet puzzle = getPuzzle(teamId);
        String[] parts = payload.toLowerCase().split("-", 2);
        String keyword = parts[0];
        String csStr = parts.length > 1 ? parts[1] : "";

        if (puzzle.keyword.equals(keyword) && csStr.equals(String.valueOf(puzzle.checksum))) {
            s.setUnlocked(true);
            resp.put("status", "UNLOCK");
            resp.put("formLink", isNode1(nodeId) ? googleFormLinkNode1 : googleFormLinkNode2);
            resp.put("nodeRole", isNode1(nodeId) ? "PARTNER-A" : "PARTNER-B");
            log.info("[UNLOCK] {} / {}", teamId, nodeId);
        } else {
            s.incrementAttempts();
            log.info("[FAIL] {} / {} attempt {}", teamId, nodeId, s.getAttempts());
            if (s.getAttemptsRemaining() <= 0) {
                s.setPermanentlyLocked(true);
                resp.put("status", "LOCKED");
                log.warn("[LOCK] {} / {} permanently locked", teamId, nodeId);
            } else {
                resp.put("status", "FAIL");
                resp.put("attemptsRemaining", s.getAttemptsRemaining());
            }
        }
        return resp;
    }

    // ════════════════════════════════════════════════════════════════
    // ADMIN
    // ════════════════════════════════════════════════════════════════

    public Map<String, Object> startEvent() {
        if (isActive()) {
            log.info("[ADMIN] Start ignored — event already active.");
            return Map.of("status", "ALREADY_RUNNING",
                    "message", "Event is already running. Timer not reset.",
                    "timeRemainingSeconds", getTimeRemainingSeconds());
        }
        eventStarted = true;
        eventStartTime = LocalDateTime.now();
        log.info("[ADMIN] Event STARTED at {}", eventStartTime);
        return Map.of("status", "STARTED", "message", "Event started. Decryption window open.");
    }

    public void endEvent() {
        eventStarted = false;
        log.info("[ADMIN] Event ENDED");
    }

    public Map<String, Object> getAdminStatus() {
        Map<String, Object> resp = new HashMap<>();
        resp.put("eventActive", isActive());
        resp.put("eventStarted", eventStarted);
        resp.put("timeRemainingSeconds", getTimeRemainingSeconds());
        resp.put("durationMinutes", durationMinutes);

        List<Map<String, Object>> nodes = new ArrayList<>();
        sessions.forEach((key, s) -> {
            Map<String, Object> n = new HashMap<>();
            n.put("teamId", s.getTeamId());
            n.put("nodeId", s.getNodeId());
            n.put("authenticated", s.isAuthenticated());
            n.put("attemptsUsed", s.getAttempts());
            n.put("attemptsRemaining", s.getAttemptsRemaining());
            n.put("unlocked", s.isUnlocked());
            n.put("locked", s.isPermanentlyLocked());
            PuzzleSet p = PUZZLES.get(s.getTeamId());
            n.put("keyword", p != null ? p.keyword : "N/A");
            n.put("checksum", p != null ? p.checksum : 0);
            nodes.add(n);
        });
        nodes.sort(Comparator.comparing(m -> m.get("teamId").toString() + m.get("nodeId").toString()));
        resp.put("nodes", nodes);
        return resp;
    }

    public void resetNode(String teamId, String nodeId) {
        String credKey = teamId + "_" + nodeId;
        NodeSession fresh = new NodeSession(teamId, nodeId);
        fresh.setAuthenticated(true);
        sessions.put(credKey, fresh);
        log.info("[ADMIN] Node reset: {} / {}", teamId, nodeId);
    }

    /** Returns the full credential sheet for admin download. */
    public List<Map<String, String>> getCredentialsSheet() {
        List<Map<String, String>> sheet = new ArrayList<>();
        credentials.forEach((key, accessKey) -> {
            String[] parts = key.split("_", 2);
            if (parts.length == 2) {
                PuzzleSet p = getPuzzle(parts[0]);
                Map<String, String> row = new LinkedHashMap<>();
                row.put("teamId", parts[0]);
                row.put("nodeId", parts[1]);
                row.put("accessKey", accessKey);
                row.put("cipher", parts[1].endsWith("01") ? p.node1Type : p.node2Type);
                row.put("keyword", p.keyword);
                row.put("checksum", String.valueOf(p.checksum));
                sheet.add(row);
            }
        });
        sheet.sort(Comparator.comparing(m -> m.get("teamId") + "_" + m.get("nodeId")));
        return sheet;
    }

    // ════════════════════════════════════════════════════════════════
    // CIPHER HELPERS
    // ════════════════════════════════════════════════════════════════

    /**
     * Returns the puzzle for a team.
     * Named teams (ALPHA/BETA/...) get their config directly.
     * Numbered teams (TEAM01...TEAM50) cycle through all available puzzles fairly.
     */
    private PuzzleSet getPuzzle(String teamId) {
        PuzzleSet p = PUZZLES.get(teamId);
        if (p != null)
            return p;
        // Numbered team: extract the number and cycle through puzzles
        List<PuzzleSet> all = new ArrayList<>(PUZZLES.values());
        int num = 0;
        try {
            num = Integer.parseInt(teamId.replaceAll("[^0-9]", ""));
        } catch (Exception ignored) {
        }
        return all.get(num % all.size());
    }

    /** Is this node the first (SYS-01) or second (SYS-02)? */
    private boolean isNode1(String nodeId) {
        return nodeId.endsWith("01") || nodeId.endsWith("-1") || nodeId.equals("SYS-01");
    }

    public String getCipherText(String teamId, String nodeId) {
        PuzzleSet p = getPuzzle(teamId);
        return isNode1(nodeId) ? p.node1Cipher : p.node2Cipher;
    }

    public String getCipherType(String teamId, String nodeId) {
        PuzzleSet p = getPuzzle(teamId);
        return isNode1(nodeId) ? p.node1Type : p.node2Type;
    }

    public String[] getHints(String teamId, String nodeId) {
        PuzzleSet p = getPuzzle(teamId);
        return isNode1(nodeId) ? p.node1Hints : p.node2Hints;
    }

    // ════════════════════════════════════════════════════════════════
    // TIMER
    // ════════════════════════════════════════════════════════════════

    private LocalDateTime eventEnd() {
        if (eventStartTime == null)
            return LocalDateTime.MIN;
        return durationSeconds > 0
                ? eventStartTime.plusSeconds(durationSeconds)
                : eventStartTime.plusMinutes(durationMinutes);
    }

    public boolean isActive() {
        if (!eventStarted || eventStartTime == null)
            return false;
        return LocalDateTime.now().isBefore(eventEnd());
    }

    public long getTimeRemainingSeconds() {
        if (!isActive())
            return 0;
        return Math.max(0, Duration.between(LocalDateTime.now(), eventEnd()).getSeconds());
    }
}
