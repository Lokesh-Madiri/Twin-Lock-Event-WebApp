"use strict";

// ================================================================
//  TWIN LOCK — Cryptographic Node Simulation v3.2
//  Terminal Client
// ================================================================

// API base URL — injected into index.html by Vite at build time from VITE_API_URL.
// Local dev: empty string → Vite proxy forwards /api/* to localhost:8080.
// Production: full Render URL baked in by Vite.
var BACKEND = (window.__TWINLOCK_API__ && window.__TWINLOCK_API__ !== '%VITE_API_URL%')
    ? window.__TWINLOCK_API__ : '';
var POLL_MS = 2500;
var TYPE_DELAY = 40;

// ── Application State ────────────────────────────────────────────
var S = {
    phase: "BOOT",       // BOOT | LOGIN | WAITING | ACTIVE | UNLOCKED | LOCKED
    teamId: null,
    nodeId: null,
    attemptsRemaining: 3,
    cipher: null,
    cipherType: null,
    hints: [],
    hintCooldownUntil: 0,   // epoch ms — when hint command unlocks again
    hintCount: 0,           // how many hints used this session
    partnerUnlocked: false, // true once we receive partner unlock from poll
    formLink: null,
    inputEnabled: false,
    timerInterval: null,
    pollInterval: null
};

// ── DOM Refs ─────────────────────────────────────────────────────
var elOutput, elCmdLine, elPrompt, elSidenav, elProfilePic;

// ════════════════════════════════════════════════════════════════
//  UTILITIES
// ════════════════════════════════════════════════════════════════

function $id(id) { return document.getElementById(id); }

function scrollBottom() { window.scrollTo(0, document.body.scrollHeight); }

function esc(s) {
    return String(s)
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;");
}

function print(html, color) {
    var d = document.createElement("div");
    if (color) d.style.color = color;
    d.innerHTML = html;
    elOutput.appendChild(d);
    scrollBottom();
}

function println(text, color) { print(esc(text), color || ""); }

function br() { elOutput.appendChild(document.createElement("br")); }

function clearOutput() { elOutput.innerHTML = ""; }

// ── Sequential Line Printer ───────────────────────────────────────
// lines: array of [html, color] or just strings
function typeLines(lines, delay, onDone) {
    var i = 0;
    function next() {
        if (i >= lines.length) { if (onDone) onDone(); return; }
        var item = lines[i++];
        if (typeof item === "string") print(esc(item));
        else if (Array.isArray(item)) print(item[0], item[1] || "");
        setTimeout(next, delay || TYPE_DELAY);
    }
    next();
}

// ── API Helpers ───────────────────────────────────────────────────
function apiPost(path, body) {
    return fetch(BACKEND + path, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body)
    }).then(function (r) { return r.json(); });
}

function apiGet(path) {
    return fetch(BACKEND + path).then(function (r) { return r.json(); });
}

// ════════════════════════════════════════════════════════════════
//  HUD (Status Bar)
// ════════════════════════════════════════════════════════════════

function buildHUD() {
    if ($id("tl-hud")) return;
    var bar = document.createElement("div");
    bar.id = "tl-hud";
    bar.style.cssText = [
        "position:fixed", "top:0", "right:0",
        "width:calc(100% - 50px)", "z-index:50",
        "display:flex", "align-items:center", "justify-content:flex-end",
        "gap:22px", "padding:5px 18px",
        "background:rgba(0,0,0,0.90)",
        "border-bottom:1px solid #2a2a2a",
        "font-family:monospace", "font-size:9.5pt", "flex-wrap:wrap"
    ].join(";");
    bar.innerHTML =
        '<span id="hud-team"  style="color:#555">TEAM: —</span>' +
        '<span id="hud-node"  style="color:#555">NODE: —</span>' +
        '<span id="hud-atts"  style="color:#555">ATTEMPTS: —</span>' +
        '<span id="hud-timer" style="color:#e09f14;font-weight:bold;letter-spacing:2px;font-size:10.5pt">——:——</span>';
    document.body.insertBefore(bar, document.body.firstChild);
    var c = $id("container");
    if (c) c.style.paddingTop = "40px";
}

function updateHUD() {
    var t = $id("hud-team");
    var n = $id("hud-node");
    var a = $id("hud-atts");
    if (t) t.textContent = S.teamId ? "TEAM: " + S.teamId : "TEAM: —";
    if (n) n.textContent = S.nodeId ? "NODE: " + S.nodeId : "NODE: —";
    if (a) a.textContent = (S.attemptsRemaining !== null)
        ? "ATTEMPTS: " + S.attemptsRemaining + "/3"
        : "ATTEMPTS: —";
}

function setTimerDisplay(text, danger) {
    var el = $id("hud-timer");
    if (!el) return;
    el.textContent = text;
    el.style.color = danger ? "#ff3333" : "#e09f14";
}

// ════════════════════════════════════════════════════════════════
//  INPUT CONTROL
// ════════════════════════════════════════════════════════════════

function setPrompt(text) { elPrompt.textContent = text; }

function enableInput() {
    elCmdLine.disabled = false;
    S.inputEnabled = true;
    elCmdLine.focus();
    scrollBottom();
}

function disableInput() {
    elCmdLine.disabled = true;
    S.inputEnabled = false;
}

// ════════════════════════════════════════════════════════════════
//  PHASE 0: BOOT
// ════════════════════════════════════════════════════════════════

function runBoot() {
    clearOutput();
    setTimerDisplay("——:——", false);
    var lines = [
        ["", ""],
        ["╔══════════════════════════════════════════════════╗", "#00ccff"],
        ["║    TWINLOCK PROTOCOL v3.2  —  NODE TERMINAL     ║", "#00ccff"],
        ["╚══════════════════════════════════════════════════╝", "#00ccff"],
        ["", ""],
        ["[SYS] Initializing cryptographic modules...", "#00ccff"],
        ["[SYS] Loading cipher engine...                [OK]", "#00ccff"],
        ["[SYS] Establishing encrypted channel...      [OK]", "#00ccff"],
        ["[SYS] Verifying node integrity...            [OK]", "#00ccff"],
        ["[SYS] Secure node detected.", "#00ccff"],
        ["", ""],
        ["[AUTH] Awaiting authentication...", "#e09f14"],
        ["", ""],
        ["  Usage  :  login &lt;teamId&gt; &lt;nodeId&gt; &lt;accessKey&gt;", "#555"],
        ["  Example:  login ALPHA SYS-01 ALPHA-NODE1-2024", "#555"],
        ["", ""]
    ];
    typeLines(lines, TYPE_DELAY, function () {
        S.phase = "LOGIN";
        setPrompt("twinlock@auth:~$");
        enableInput();
    });
}

// ════════════════════════════════════════════════════════════════
//  COMMAND ROUTER
// ════════════════════════════════════════════════════════════════

function handleCommand(raw) {
    var trimmed = raw.trim();
    // Echo the typed command
    print(
        '<span style="color:#e09f14">' + esc(elPrompt.textContent) + '</span> ' + esc(trimmed)
    );
    if (!trimmed) { enableInput(); return; }
    var parts = trimmed.split(/\s+/);
    var cmd = parts[0].toLowerCase();
    disableInput();
    if (S.phase === "LOGIN") phaseLogin(cmd, parts);
    else if (S.phase === "WAITING") phaseWait(cmd, parts);
    else if (S.phase === "ACTIVE") phaseActive(cmd, parts);
    else {
        println("[SYS] Terminal is sealed. No further commands accepted.", "#ff3333");
    }
}

// ════════════════════════════════════════════════════════════════
//  PHASE 1: LOGIN
// ════════════════════════════════════════════════════════════════

function phaseLogin(cmd, parts) {
    if (cmd === "login") {
        if (parts.length < 4) {
            println("[ERR] Usage: login <teamId> <nodeId> <accessKey>", "#ff3333");
            enableInput(); return;
        }
        var teamId = parts[1].toUpperCase();
        var nodeId = parts[2].toUpperCase();
        var accessKey = parts[3];
        println("[AUTH] Authenticating credentials...", "#00ccff");
        apiPost("/api/auth/login", { teamId: teamId, nodeId: nodeId, accessKey: accessKey })
            .then(function (d) {
                if (d.status === "OK") {
                    doAuthSuccess(d);
                } else {
                    println("[AUTH] Authentication failed. Invalid credentials.", "#ff3333");
                    println("[AUTH] Verify teamId, nodeId, and accessKey then retry.", "#ff3333");
                    br();
                    enableInput();
                }
            })
            .catch(function () {
                println("[ERR] Cannot reach central authority. Check network.", "#ff3333");
                enableInput();
            });
    } else if (cmd === "help") {
        br();
        println("[SYS] Available Commands:", "#00ccff");
        println("  login <teamId> <nodeId> <accessKey>  — Authenticate node", "#555");
        println("  help                                 — Show this menu", "#555");
        println("  clear                                — Clear terminal", "#555");
        br(); enableInput();
    } else if (cmd === "clear") {
        runBoot();
    } else {
        println("[ERR] " + parts[0] + ": command not available. Use 'login' to authenticate.", "#ff3333");
        enableInput();
    }
}

function doAuthSuccess(data) {
    S.teamId = data.teamId;
    S.nodeId = data.nodeId;
    S.attemptsRemaining = 3;
    updateHUD();
    saveSession();
    typeLines([
        ["", ""],
        ["┌─────────────────────────────────────────────┐", "#00ccff"],
        ["│        AUTHENTICATION SUCCESSFUL            │", "#00ccff"],
        ["└─────────────────────────────────────────────┘", "#00ccff"],
        ["  Node ID            : " + data.nodeId, "#00ccff"],
        ["  Team               : " + data.teamId, "#00ccff"],
        ["  Authorization Level: Participant", "#00ccff"],
        ["  Decryption Window  : Pending", "#00ccff"],
        ["", ""],
        ["[SYS] Node locked. Awaiting central authority signal...", "#e09f14"],
        ["", ""]
    ], TYPE_DELAY, function () {
        S.phase = "WAITING";
        setPrompt(data.nodeId + "@twinlock:~$");
        startPolling();
        enableInput();
    });
}

// ════════════════════════════════════════════════════════════════
//  PHASE 2: WAITING
// ════════════════════════════════════════════════════════════════

function phaseWait(cmd, parts) {
    if (cmd === "status") {
        br();
        println("[SYS] Node Status", "#00ccff");
        println("  Team   : " + S.teamId, "#00ccff");
        println("  Node   : " + S.nodeId, "#00ccff");
        println("  Phase  : LOCKED — Pending Start", "#e09f14");
        println("  Signal : Awaiting central authority...", "#e09f14");
        br();
    } else if (cmd === "help") {
        br();
        println("[SYS] Available Commands:", "#00ccff");
        println("  status   — Show node status", "#555");
        println("  time     — Show time remaining", "#555");
        println("  help     — Show this menu", "#555");
        println("  clear    — Clear terminal", "#555");
        br();
    } else if (cmd === "time") {
        var timerEl = $id("hud-timer");
        var t = timerEl ? timerEl.textContent : "——:——";
        br();
        println("[SYS] Decryption Window: " + t, "#e09f14");
        println("[SYS] Event has not started yet.", "#555");
        br();
    } else if (cmd === "clear") {
        clearOutput();
        println("[SYS] System locked. Awaiting central authority signal...", "#e09f14");
    } else {
        println("[SYS] Command rejected. Node is locked pending event start.", "#e09f14");
    }
    enableInput();
}

// ════════════════════════════════════════════════════════════════
//  PHASE 3: EVENT ACTIVE
// ════════════════════════════════════════════════════════════════

function phaseActive(cmd, parts) {
    if (cmd === "submit") {
        if (parts.length < 2) {
            println("[ERR] Usage: submit <keyword>-<checksum>", "#ff3333");
            enableInput(); return;
        }
        doSubmit(parts[1]);
    } else if (cmd === "time") {
        var timerEl = $id("hud-timer");
        var t = timerEl ? timerEl.textContent : "——:——";
        br();
        println("[SYS] Decryption Window Remaining: " + t, "#e09f14");
        br(); enableInput();
    } else if (cmd === "hint") {
        showHints();
    } else if (cmd === "status") {
        br();
        println("[SYS] Node Status", "#00ccff");
        println("  Team              : " + S.teamId, "#00ccff");
        println("  Node              : " + S.nodeId, "#00ccff");
        println("  Attempts Remaining: " + S.attemptsRemaining + "/3", "#00ccff");
        println("  Decryption Window : OPEN", "#00ccff");
        br(); enableInput();
    } else if (cmd === "help") {
        br();
        println("[SYS] Available Commands:", "#00ccff");
        println("  submit <keyword>-<checksum>  — Submit decrypted answer", "#555");
        println("  time                         — Check time remaining", "#555");
        println("  hint                         — Request decryption hints", "#555");
        println("  status                       — Show node status", "#555");
        println("  help                         — Show this menu", "#555");
        println("  clear                        — Clear terminal", "#555");
        br(); enableInput();
    } else if (cmd === "clear") {
        clearOutput(); redisplayCipher();
    } else {
        println("[ERR] " + parts[0] + ": command rejected.", "#ff3333");
        println("[SYS] Available: submit, time, hint, status, help, clear", "#555");
        enableInput();
    }
}


// Groups flat hint lines into [[hint1 lines], [hint2 lines], [hint3 lines]]
function groupHints(hints) {
    var groups = [];
    var current = null;
    (hints || []).forEach(function (line) {
        if (/^\[HINT \d+\]/.test(line)) {
            if (current) groups.push(current);
            current = [line];
        } else if (current) {
            current.push(line);
        }
    });
    if (current) groups.push(current);
    // Fallback if no [HINT N] markers found — treat all as one group
    if (groups.length === 0 && hints && hints.length > 0) groups.push(hints);
    return groups;
}

function showHints() {
    var now = Date.now();
    var groups = groupHints(S.hints);
    var total = groups.length || 3;

    // All hints already shown
    if (S.hintCount >= total) {
        br();
        println("[SYS] All " + total + " hints have been revealed.", "#ff3333");
        println("[SYS] No more hints available. Good luck.", "#555");
        br();
        enableInput(); return;
    }

    // Cooldown check (skip for very first hint)
    if (S.hintCount > 0 && now < S.hintCooldownUntil) {
        var waitMs = S.hintCooldownUntil - now;
        var waitMins = Math.floor(waitMs / 60000);
        var waitSecs = Math.floor((waitMs % 60000) / 1000);
        br();
        println("[SYS] Hint cooldown active.", "#ff3333");
        println("[SYS] Hint " + (S.hintCount + 1) + " of " + total +
            " unlocks in: " + (waitMins > 0 ? waitMins + "m " : "") + waitSecs + "s", "#ff3333");
        br();
        enableInput(); return;
    }

    // Reveal the next hint
    var idx = S.hintCount;   // 0-based index of hint to show NOW
    var hintLines = groups[idx] || ["[HINT " + (idx + 1) + "] No hint available."];

    // Apply cooldown BEFORE showing (30 seconds for testing, change to 5 * 60 * 1000 for production)
    var HINT_COOLDOWN_MS = 30 * 1000;
    S.hintCount++;
    S.hintCooldownUntil = Date.now() + HINT_COOLDOWN_MS;

    br();
    println("[SYS] Hint " + S.hintCount + " of " + total + " \u2014 Authorized Release", "#e09f14");
    println("[SYS] Cipher Type: " + (S.cipherType || "ENCRYPTED"), "#e09f14");
    println("\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500", "#333");
    hintLines.forEach(function (line) { println(line, "#e09f14"); });

    // Always append checksum concept with Hint 1
    if (idx === 0) {
        br();
        println("[SYS] \u2500\u2500 How to submit your answer \u2500\u2500", "#00ccff");
        println("      1. Decode the cipher to find the keyword", "#555");
        println("      2. Checksum = add each letter's position (A=1 to Z=26)", "#555");
        println("         Example: LOCK \u2192 L=12+O=15+C=3+K=11 = 41", "#555");
        println("      3. Type:  submit <keyword>-<checksum>", "#555");
        println("         Example: submit lock-41", "#555");
    }

    println("\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500", "#333");

    if (S.hintCount < total) {
        println("[SYS] Hint " + (S.hintCount + 1) + " of " + total +
            " available in 5 minutes.", "#555");
    } else {
        println("[SYS] All hints have been revealed.", "#555");
    }
    br();
    enableInput();
}



function redisplayCipher() {
    if (S.cipher) {
        println("[SYS] Cipher Stream — Re-Loaded", "#00ccff");
        print(
            '<span style="color:#00ff41;font-size:1.1em;letter-spacing:0.2em;' +
            'text-shadow:0 0 8px #00ff41">' + esc(S.cipher) + '</span>'
        );
        br();
    }
    enableInput();
}

// ════════════════════════════════════════════════════════════════
//  SUBMIT LOGIC
// ════════════════════════════════════════════════════════════════

function doSubmit(payload) {
    println("[SYS] Transmitting payload to central authority...", "#00ccff");
    apiPost("/api/node/submit", {
        teamId: S.teamId,
        nodeId: S.nodeId,
        payload: payload
    })
        .then(function (d) {
            if (d.status === "UNLOCK") doUnlock(d);
            else if (d.status === "LOCKED") doPermanentLock();
            else doFail(d);
        })
        .catch(function () {
            println("[ERR] Transmission error. Central authority unreachable.", "#ff3333");
            enableInput();
        });
}

function doUnlock(data) {
    S.phase = "UNLOCKED";
    stopPolling(); stopTimer();
    typeLines([
        ["", ""],
        ["╔══════════════════════════════════════════════════╗", "#00ff41"],
        ["║           VALIDATION SUCCESSFUL                  ║", "#00ff41"],
        ["╚══════════════════════════════════════════════════╝", "#00ff41"],
        ["", ""],
        ["[SYS] Node authenticated.", "#00ff41"],
        ["[SYS] Synchronization link generated.", "#00ff41"],
        ["", ""],
        ["[SYS] *** CRITICAL — Open the synchronization link NOW ***", "#e09f14"],
        ["[SYS] Both nodes must submit within 10 seconds of each other.", "#e09f14"],
        ["", ""]
    ], TYPE_DELAY, function () {
        print(
            '<span style="color:#00ccff">[SYNC] Link: </span>' +
            '<a href="' + esc(data.formLink) + '" target="_blank" ' +
            'style="color:#00ff41;text-shadow:0 0 8px #00ff41;text-decoration:underline">' +
            esc(data.formLink) + '</a>'
        );
        br();
        println("[SYS] Terminal sealed. Input disabled.", "#555");
        setTimerDisplay("UNLOCKED", false);
        clearSession();
    });
}

function doPermanentLock() {
    S.phase = "LOCKED";
    S.attemptsRemaining = 0;
    stopPolling(); stopTimer();
    updateHUD();
    typeLines([
        ["", ""],
        ["╔══════════════════════════════════════════════════╗", "#ff3333"],
        ["║          SECURITY BREACH DETECTED                ║", "#ff3333"],
        ["╚══════════════════════════════════════════════════╝", "#ff3333"],
        ["", ""],
        ["[SEC] Node locked permanently.", "#ff3333"],
        ["[SEC] Access revoked.", "#ff3333"],
        ["[SEC] All further input rejected.", "#ff3333"],
        ["", ""],
        ["[SYS] Contact event authority for assistance.", "#555"]
    ], TYPE_DELAY, function () {
        setTimerDisplay("LOCKED", true);
        clearSession();
    });
}

function doFail(data) {
    S.attemptsRemaining = data.attemptsRemaining;
    updateHUD();
    var used = 3 - S.attemptsRemaining;
    var alertText = used === 1 ? "LOW" : used === 2 ? "MEDIUM" : "HIGH";
    var alertCol = used >= 2 ? "#ff3333" : "#e09f14";
    br();
    println("[SEC] Validation Failed.", "#ff3333");
    println("[SEC] Attempt Counter     : " + used + " / 3", "#ff3333");
    println("[SEC] Security Alert Level: " + alertText, alertCol);
    // Attempt indicator dots
    var dots = "";
    for (var i = 0; i < 3; i++) {
        var c = i < used ? "#ff3333" : "#00ff41";
        dots += '<span style="display:inline-block;width:10px;height:10px;border-radius:50%;' +
            'background:' + c + ';border:1px solid ' + c + ';margin:0 3px;box-shadow:0 0 5px ' + c + '"></span>';
    }
    print('<span style="color:#555">[SEC] Attempts: </span>' + dots);
    br();
    println("[SYS] " + S.attemptsRemaining + " attempt(s) remaining before permanent lockout.",
        S.attemptsRemaining === 1 ? "#ff3333" : "#e09f14");
    br();
    enableInput();
}

// ════════════════════════════════════════════════════════════════
//  EVENT START — Shown when admin triggers /api/admin/start
// ════════════════════════════════════════════════════════════════

function showEventStart(data) {
    clearOutput();
    typeLines([
        ["", ""],
        ["╔══════════════════════════════════════════════════╗", "#00ccff"],
        ["║      CENTRAL AUTHORITY SIGNAL RECEIVED           ║", "#00ccff"],
        ["╚══════════════════════════════════════════════════╝", "#00ccff"],
        ["", ""],
        ["[SYS] Decryption Window Opened.", "#00ccff"],
        ["[SYS] Time Remaining: " + formatTime(data.timeRemainingSeconds), "#00ccff"],
        ["", ""],
        ["[SYS] Receiving encrypted payload...", "#00ccff"],
        ["[SYS] Parsing fragments...", "#00ccff"],
        ["[SYS] Cipher Stream Loaded.", "#00ccff"],
        ["", ""],
        ["[TYPE] " + (data.cipherType || "ENCRYPTED"), "#e09f14"],
        ["──────────────────────────────────────────────────", "#333"]
    ], TYPE_DELAY, function () {
        // Cipher may be multi-line (Polybius square, Logic Gates)
        var cipherLines = (data.cipher || "").split("\n");
        cipherLines.forEach(function (line) {
            print(
                '<span style="color:#00ff41;font-size:1.05em;letter-spacing:0.15em;' +
                'text-shadow:0 0 8px #00ff41;font-family:monospace">' + esc(line) + '</span>'
            );
        });
        print('<span style="color:#333">──────────────────────────────────────────────────</span>');
        br();
        println("[SYS] Decrypt the cipher. Type 'hint' if you need help.", "#555");
        println("[SYS] submit <keyword>-<checksum>", "#555");
        br();
        startTimer(data.timeRemainingSeconds);
        startPolling();
        enableInput();
    });
}

// ════════════════════════════════════════════════════════════════
//  EVENT END
// ════════════════════════════════════════════════════════════════

function handleEventEnd() {
    if (S.phase === "UNLOCKED" || S.phase === "LOCKED") return;
    S.phase = "LOCKED";
    stopPolling(); stopTimer();
    disableInput();
    setTimerDisplay("00:00", true);
    br();
    println("╔══════════════════════════════════════════════════╗", "#ff3333");
    println("║           DECRYPTION WINDOW CLOSED               ║", "#ff3333");
    println("╚══════════════════════════════════════════════════╝", "#ff3333");
    br();
    println("[SYS] Payload destroyed.", "#ff3333");
    println("[SYS] System sealed.", "#ff3333");
    println("[SYS] Contact event authority for assistance.", "#555");
    clearSession();
}

// ════════════════════════════════════════════════════════════════
//  POLLING
// ════════════════════════════════════════════════════════════════

function startPolling() {
    if (S.pollInterval) return;
    S.pollInterval = setInterval(doPoll, POLL_MS);
}

function stopPolling() {
    if (S.pollInterval) { clearInterval(S.pollInterval); S.pollInterval = null; }
}

function doPoll() {
    if (!S.teamId || !S.nodeId) return;
    apiGet(
        "/api/node/status?teamId=" + encodeURIComponent(S.teamId) +
        "&nodeId=" + encodeURIComponent(S.nodeId)
    )
        .then(function (d) {
            // Event just started
            if (d.eventActive && S.phase === "WAITING") {
                S.phase = "ACTIVE";
                S.cipher = d.cipher;
                S.cipherType = d.cipherType || "ENCRYPTED";
                S.hints = d.hints || [];
                S.attemptsRemaining = d.attemptsRemaining;
                updateHUD();
                stopPolling();
                showEventStart(d);
                return;
            }
            // Event ended while we were active
            if (!d.eventActive && S.phase === "ACTIVE") {
                handleEventEnd(); return;
            }
            // Node externally locked
            if (d.nodeLocked && S.phase === "ACTIVE") {
                doPermanentLock(); return;
            }
            // Partner just unlocked — show broadcast message (only once)
            if (d.partnerUnlocked && !S.partnerUnlocked && S.phase === "ACTIVE") {
                S.partnerUnlocked = true;
                showPartnerUnlockedAlert(d.partnerNodeId || "PARTNER");
            }
            // Sync attempts count
            if (typeof d.attemptsRemaining !== "undefined") {
                S.attemptsRemaining = d.attemptsRemaining;
                updateHUD();
            }
            // Keep hints updated
            if (d.hints) S.hints = d.hints;
            // Sync timer
            if (S.phase === "ACTIVE" && typeof d.timeRemainingSeconds !== "undefined") {
                syncTimer(d.timeRemainingSeconds);
            }
        })
        .catch(function () { /* silent */ });
}

function showPartnerUnlockedAlert(partnerNodeId) {
    disableInput();
    br();
    println("╔═════════════════════════════════════════════════╗", "#00ff41");
    println("║     TWIN-LOCK SYNCHRONIZATION SIGNAL RECEIVED     ║", "#00ff41");
    println("╚═════════════════════════════════════════════════╝", "#00ff41");
    br();
    println("[BROADCAST] Node " + partnerNodeId + " has UNLOCKED.", "#00ff41");
    println("[BROADCAST] Your partner has decoded their cipher!", "#00ff41");
    println("──────────────────────────────────────────────", "#e09f14");
    println("[SYS] Your node must ALSO unlock to complete the", "#e09f14");
    println("      Twin-Lock sequence. Decode YOUR cipher now!", "#e09f14");
    println("──────────────────────────────────────────────", "#e09f14");
    br();
    enableInput();
}

// ════════════════════════════════════════════════════════════════
//  TIMER
// ════════════════════════════════════════════════════════════════

function formatTime(secs) {
    if (secs <= 0) return "00:00";
    var m = Math.floor(secs / 60);
    var s = secs % 60;
    return pad2(m) + ":" + pad2(s);
}

function pad2(n) { return n < 10 ? "0" + n : "" + n; }

var _timerSecs = 0;

function startTimer(initialSecs) {
    stopTimer();
    _timerSecs = initialSecs;
    setTimerDisplay(formatTime(_timerSecs), _timerSecs <= 60);
    S.timerInterval = setInterval(function () {
        _timerSecs--;
        if (_timerSecs <= 0) {
            _timerSecs = 0;
            setTimerDisplay("00:00", true);
            stopTimer();
            handleEventEnd();
        } else {
            setTimerDisplay(formatTime(_timerSecs), _timerSecs <= 60);
        }
    }, 1000);
}

function syncTimer(backendSecs) {
    // Correct drift — only if difference > 3 seconds
    var diff = Math.abs(_timerSecs - backendSecs);
    if (diff > 3) {
        stopTimer();
        startTimer(backendSecs);
    }
}

function stopTimer() {
    if (S.timerInterval) { clearInterval(S.timerInterval); S.timerInterval = null; }
}

// ════════════════════════════════════════════════════════════════
//  SESSION PERSISTENCE (survive page refresh)
// ════════════════════════════════════════════════════════════════

function saveSession() {
    try {
        sessionStorage.setItem("tl_sess", JSON.stringify({
            teamId: S.teamId, nodeId: S.nodeId, attemptsRemaining: S.attemptsRemaining
        }));
    } catch (e) { }
}

function clearSession() {
    try { sessionStorage.removeItem("tl_sess"); } catch (e) { }
}

function restoreSession(onDone) {
    try {
        var raw = sessionStorage.getItem("tl_sess");
        if (!raw) { onDone(false); return; }
        var sess = JSON.parse(raw);
        if (!sess.teamId || !sess.nodeId) { onDone(false); return; }
        // Re-validate with backend
        apiPost("/api/auth/restore", { teamId: sess.teamId, nodeId: sess.nodeId })
            .then(function (d) {
                if (d.status === "OK") {
                    S.teamId = sess.teamId;
                    S.nodeId = sess.nodeId;
                    S.attemptsRemaining = d.attemptsRemaining;
                    S.phase = d.eventActive ? "WAITING" : "WAITING"; // will be updated by poll
                    updateHUD();
                    onDone(true, d);
                } else {
                    clearSession(); onDone(false);
                }
            })
            .catch(function () { clearSession(); onDone(false); });
    } catch (e) { clearSession(); onDone(false); }
}

// ════════════════════════════════════════════════════════════════
//  SIDENAV (keep existing behaviour, minimal override)
// ════════════════════════════════════════════════════════════════

function initSideNav() {
    var nav = elSidenav;
    if (!nav) return;
    var btn = $id("sidenavBtn");
    var open = false;
    if (btn) {
        btn.addEventListener("click", function (e) {
            e.stopPropagation();
            open = !open;
            nav.style.width = open ? "200px" : "50px";
            btn.innerHTML = open ? "&times;" : "&#9776;";
            if (elProfilePic) elProfilePic.style.opacity = open ? "1" : "0";
        });
    }
    document.body.addEventListener("click", function () {
        if (open) {
            open = false;
            nav.style.width = "50px";
            if (btn) btn.innerHTML = "&#9776;";
            if (elProfilePic) elProfilePic.style.opacity = "0";
        }
        if (S.inputEnabled) elCmdLine.focus();
    });
}

// ════════════════════════════════════════════════════════════════
//  BOOT — Entry Point
// ════════════════════════════════════════════════════════════════

window.onload = function () {
    elOutput = $id("output");
    elCmdLine = $id("cmdline");
    elPrompt = $id("prompt");
    elSidenav = $id("sidenav");
    elProfilePic = $id("profilePic");

    if (!elOutput || !elCmdLine || !elPrompt) return;

    // Auto-fullscreen on load
    function tryFullscreen() {
        var el = document.documentElement;
        if (el.requestFullscreen) el.requestFullscreen();
        else if (el.webkitRequestFullscreen) el.webkitRequestFullscreen();
        else if (el.mozRequestFullScreen) el.mozRequestFullScreen();
        else if (el.msRequestFullscreen) el.msRequestFullscreen();
    }
    // Try immediately; browsers may require a user gesture — fallback on first click
    try { tryFullscreen(); } catch (e) { }
    document.body.addEventListener("click", function fsOnce() {
        if (!document.fullscreenElement) { try { tryFullscreen(); } catch (e) { } }
        document.body.removeEventListener("click", fsOnce);
    }, { once: true });

    buildHUD();
    initSideNav();

    // Enter key
    elCmdLine.addEventListener("keydown", function (e) {
        if ((e.which === 13 || e.keyCode === 13) && S.inputEnabled) {
            var val = elCmdLine.value;
            elCmdLine.value = "";
            disableInput();
            handleCommand(val);
            e.preventDefault();
        }
    });

    // Click anywhere → refocus input
    document.body.addEventListener("click", function () {
        if (S.inputEnabled) elCmdLine.focus();
    });

    // Try to restore previous session (page refresh recovery)
    restoreSession(function (restored, data) {
        if (restored && data) {
            // Abbreviated boot then straight to waiting/active
            typeLines([
                ["", ""],
                ["[SYS] Reconnecting to encrypted channel...", "#00ccff"],
                ["[SYS] Session restored for " + S.teamId + " / " + S.nodeId, "#00ccff"],
                ["", ""]
            ], TYPE_DELAY, function () {
                S.phase = "WAITING";
                setPrompt(S.nodeId + "@twinlock:~$");
                startPolling();
                println("[SYS] Node locked. Awaiting central authority signal...", "#e09f14");
                enableInput();
            });
        } else {
            runBoot();
        }
    });
};
