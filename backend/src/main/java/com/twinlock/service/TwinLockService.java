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
    // DATA MODEL — one Level = one stage of the puzzle
    // ════════════════════════════════════════════════════════════════

    private static class Level {
        final String keyword;
        final int checksum;
        final String cipherType;
        final String cipherText;
        final String[] hints; // each element = one printed line

        Level(String kw, int cs, String type, String cipher, String... hints) {
            keyword = kw;
            checksum = cs;
            cipherType = type;
            cipherText = cipher;
            this.hints = hints;
        }
    }

    /**
     * Each PuzzleSet holds 3 Levels per node.
     * levels[0] = EASY (unrelated warm-up keyword)
     * levels[1] = MEDIUM (keyword that unlocks the method for levels[2])
     * levels[2] = HARD (final keyword — same as the old single-level answer)
     */
    private static class PuzzleSet {
        final Level[] node1Levels;
        final Level[] node2Levels;
        // kept for admin / credential sheet
        final String keyword;
        final int checksum;
        final String node1Type;
        final String node2Type;

        PuzzleSet(Level[] n1, Level[] n2) {
            node1Levels = n1;
            node2Levels = n2;
            keyword = n2[2].keyword;
            checksum = n2[2].checksum;
            node1Type = n1[2].cipherType;
            node2Type = n2[2].cipherType;
        }
    }

    // ════════════════════════════════════════════════════════════════
    // PUZZLE CONFIGURATIONS
    // L1 (EASY) — warm-up, team-name themed, generous hints
    // L2 (MEDIUM) — decodes a word that IS the key / method for L3
    // L3 (HARD) — the actual final keyword; hints reference L2
    // ════════════════════════════════════════════════════════════════

    private static final Map<String, PuzzleSet> PUZZLES = new LinkedHashMap<>();
    static {

        // ── ALPHA → final keyword: VICTORY (cs=112) ──────────────────────
        PUZZLES.put("ALPHA", new PuzzleSet(
                new Level[] {
                        // L1 EASY — Number Pattern → "alpha" (cs=38)
                        new Level("alpha", 38, "NUMBER PATTERN",
                                "01 -- 12 -- 16 -- 08 -- 01",
                                "[HINT 1] Each number is a letter's position (A=1 … Z=26).",
                                "[HINT 2] Map: 01=A  08=H  12=L  16=P",
                                "[HINT 3] 5 numbers → 5 letters. The word is your team code-name."),

                        // L2 MEDIUM — Anagram → "seven" (cs=65)
                        new Level("seven", 65, "ANAGRAM",
                                "SCRAMBLED: V -- E -- S -- E -- N",
                                "[HINT 1] Rearrange ALL 5 letters to form a common English number word.",
                                "[HINT 2] The word names a quantity between one and ten. It has two E's.",
                                "[HINT 3] Answer: SEVEN. This is the Caesar SHIFT KEY for Level 3." +
                                        " Shift each letter BACK by 7. Example: C→V, P→I."),

                        // L3 HARD — Caesar +7 → "victory" (cs=112)
                        new Level("victory", 112, "CAESAR CIPHER",
                                "CPJAVYF PZ AOL RLF AV ZBJJLZZ",
                                "[HINT 1] Caesar Cipher. You know the shift from Level 2." +
                                        " Shift every letter BACKWARD by that value.",
                                "[HINT 2] Shift = 7. Decode: C→V  P→I  J→C  A→T  V→O  Y→R  F→Y",
                                "[HINT 3] First decoded word = VICTORY (7 letters). Submit: victory-112")
                },
                new Level[] {
                        // L1 EASY — Morse → "alpha" (cs=38)
                        new Level("alpha", 38, "MORSE CODE",
                                ".- / .-.. / .--. / .... / .-",
                                "[HINT 1] International Morse Code. Each group (/) = one letter.",
                                "[HINT 2] .-=A  .-..=L  .--. =P  ....=H",
                                "[HINT 3] 5 groups → 5 letters. The word is your team code-name."),

                        // L2 MEDIUM — Number Pattern → "seven" (cs=65)
                        new Level("seven", 65, "NUMBER PATTERN",
                                "19 -- 05 -- 22 -- 05 -- 14",
                                "[HINT 1] Each number is a letter's position (A=1 … Z=26).",
                                "[HINT 2] Map: 05=E  14=N  19=S  22=V",
                                "[HINT 3] Answer: SEVEN — the Caesar shift key for Level 3." +
                                        " Shift each Level-3 letter BACK by 7."),

                        // L3 HARD — Number Pattern → "victory" (cs=112)
                        new Level("victory", 112, "NUMBER PATTERN",
                                "22 -- 09 -- 03 -- 20 -- 15 -- 18 -- 25",
                                "[HINT 1] You used this method in Level 1. Each number = letter position.",
                                "[HINT 2] Map: 03=C  09=I  15=O  18=R  20=T  22=V  25=Y",
                                "[HINT 3] 7 numbers → VICTORY. Checksum = 22+9+3+20+15+18+25 = 112." +
                                        " Submit: victory-112")
                }));

        // ── BETA → final keyword: UNLOCK (cs=76) ─────────────────────────
        PUZZLES.put("BETA", new PuzzleSet(
                new Level[] {
                        // L1 EASY — Number Pattern → "beta" (cs=28)
                        new Level("beta", 28, "NUMBER PATTERN",
                                "02 -- 05 -- 20 -- 01",
                                "[HINT 1] Each number is a letter's position (A=1 … Z=26).",
                                "[HINT 2] Map: 01=A  02=B  05=E  20=T",
                                "[HINT 3] 4 numbers → 4 letters. The word is your team code-name."),

                        // L2 MEDIUM — Number Pattern → "morse" (cs=70)
                        new Level("morse", 70, "NUMBER PATTERN",
                                "13 -- 15 -- 18 -- 19 -- 05",
                                "[HINT 1] Each number is a letter's position (A=1 … Z=26).",
                                "[HINT 2] Map: 05=E  13=M  15=O  18=R  19=S",
                                "[HINT 3] Answer: MORSE — the cipher TYPE used in Level 3." +
                                        " In Morse each '/' group is one letter. Dot(.)=short Dash(-)=long."),

                        // L3 HARD — Morse → "unlock" (cs=76)
                        new Level("unlock", 76, "MORSE CODE",
                                "..- / -. / .-.. / --- / -.-. / -.-",
                                "[HINT 1] Morse Code — you decoded the name of this cipher in Level 2.",
                                "[HINT 2] ..-=U  -.=N  .-..=L  ---=O  -.-.=C  -.-=K",
                                "[HINT 3] 6 groups → UNLOCK. Checksum=21+14+12+15+3+11=76. Submit: unlock-76")
                },
                new Level[] {
                        // L1 EASY — Number Pattern → "beta" (cs=28)
                        new Level("beta", 28, "NUMBER PATTERN",
                                "02 -- 05 -- 20 -- 01",
                                "[HINT 1] Each number is a letter's position (A=1 … Z=26).",
                                "[HINT 2] Map: 01=A  02=B  05=E  20=T",
                                "[HINT 3] 4 numbers → 4 letters. The word is your team code-name."),

                        // L2 MEDIUM — Anagram → "morse" (cs=70)
                        new Level("morse", 70, "ANAGRAM",
                                "SCRAMBLED: M -- O -- E -- S -- R",
                                "[HINT 1] Rearrange ALL 5 letters to form a word.",
                                "[HINT 2] The word is the NAME of a famous signal code invented in the 1800s.",
                                "[HINT 3] Answer: MORSE — the cipher type for Level 3." +
                                        " Decode using dots and dashes."),

                        // L3 HARD — Math Sequence → "unlock" (cs=76)
                        new Level("unlock", 76, "MATH SEQUENCE",
                                "[ 3x7 ] -> [ 7x2 ] -> [ 4x3 ] -> [ 5x3 ] -> [ 9/3 ] -> [ 11x1 ]",
                                "[HINT 1] Solve each bracket. Each result is a number 1-26.",
                                "[HINT 2] Convert result → letter (A=1 … Z=26). 3×7=21=U  7×2=14=N",
                                "[HINT 3] 6 results → UNLOCK. Checksum=76. Submit: unlock-76")
                }));

        // ── GAMMA → final keyword: FREEDOM (cs=66) ───────────────────────
        PUZZLES.put("GAMMA", new PuzzleSet(
                new Level[] {
                        // L1 EASY — Number Pattern → "gamma" (cs=35)
                        new Level("gamma", 35, "NUMBER PATTERN",
                                "07 -- 01 -- 13 -- 13 -- 01",
                                "[HINT 1] Each number is a letter's position (A=1 … Z=26).",
                                "[HINT 2] Map: 01=A  07=G  13=M",
                                "[HINT 3] 5 numbers → 5 letters. The word is your team code-name."),

                        // L2 MEDIUM — Number Pattern → "order" (cs=60)
                        new Level("order", 60, "NUMBER PATTERN",
                                "15 -- 18 -- 04 -- 05 -- 18",
                                "[HINT 1] Each number is a letter's position (A=1 … Z=26).",
                                "[HINT 2] Map: 04=D  05=E  15=O  18=R",
                                "[HINT 3] Answer: ORDER — the key skill for Level 3." +
                                        " Level 3 is an ANAGRAM: rearrange ALL letters into correct ORDER."),

                        // L3 HARD — Anagram → "freedom" (cs=66)
                        new Level("freedom", 66, "ANAGRAM",
                                "SCRAMBLED: M -- O -- E -- R -- F -- D -- E",
                                "[HINT 1] You decoded the word ORDER in Level 2. Now apply it here.",
                                "[HINT 2] All 7 letters rearranged form a word about liberation." +
                                        " It contains a repeated letter.",
                                "[HINT 3] FREEDOM. Checksum=6+18+5+5+4+15+13=66. Submit: freedom-66")
                },
                new Level[] {
                        // L1 EASY — Number Pattern → "gamma" (cs=35)
                        new Level("gamma", 35, "NUMBER PATTERN",
                                "07 -- 01 -- 13 -- 13 -- 01",
                                "[HINT 1] Each number is a letter's position (A=1 … Z=26).",
                                "[HINT 2] Map: 01=A  07=G  13=M",
                                "[HINT 3] 5 numbers → 5 letters. The word is your team code-name."),

                        // L2 MEDIUM — Morse → "order" (cs=60)
                        new Level("order", 60, "MORSE CODE",
                                "--- / .-. / -.. / . / .-.",
                                "[HINT 1] International Morse Code. Each group = one letter.",
                                "[HINT 2] ---=O  .-.=R  -..=D  .=E",
                                "[HINT 3] Answer: ORDER. In Level 3 you must rearrange (ORDER) the" +
                                        " scrambled letters to spell a 7-letter word about liberation."),

                        // L3 HARD — Binary → "freedom" (cs=66)
                        new Level("freedom", 66, "BINARY DECODE",
                                "00110 | 10010 | 00101 | 00101 | 00100 | 01111 | 01101",
                                "[HINT 1] Each 5-bit group = one letter. Convert binary → decimal.",
                                "[HINT 2] Decimal = letter position. 00110=6=F  10010=18=R  00101=5=E",
                                "[HINT 3] 7 groups → FREEDOM. Checksum=66. Submit: freedom-66")
                }));

        // ── DELTA → final keyword: CIPHER (cs=59) ────────────────────────
        PUZZLES.put("DELTA", new PuzzleSet(
                new Level[] {
                        // L1 EASY — Number Pattern → "delta" (cs=42)
                        new Level("delta", 42, "NUMBER PATTERN",
                                "04 -- 05 -- 12 -- 20 -- 01",
                                "[HINT 1] Each number is a letter's position (A=1 … Z=26).",
                                "[HINT 2] Map: 01=A  04=D  05=E  12=L  20=T",
                                "[HINT 3] 5 numbers → 5 letters. The word is your team code-name."),

                        // L2 MEDIUM — Morse → "grid" (cs=38)
                        new Level("grid", 38, "MORSE CODE",
                                "--. / .-. / .. / -..",
                                "[HINT 1] International Morse Code. Each group = one letter.",
                                "[HINT 2] --.=G  .-.=R  ..=I  -..=D",
                                "[HINT 3] Answer: GRID — the tool for Level 3 is a 5×5 GRID (Polybius)." +
                                        " Find (row,col) pairs in the grid to get each letter."),

                        // L3 HARD — Polybius → "cipher" (cs=59)
                        new Level("cipher", 59, "POLYBIUS SQUARE",
                                "   [1][2][3][4][5]\n" +
                                        "1: [A][B][C][D][E]\n" +
                                        "2: [F][G][H][I][K]\n" +
                                        "3: [L][M][N][O][P]\n" +
                                        "4: [Q][R][S][T][U]\n" +
                                        "5: [V][W][X][Y][Z]\n" +
                                        "SEQUENCE: (1,3)-(2,4)-(3,5)-(2,3)-(1,5)-(4,2)",
                                "[HINT 1] You decoded GRID in Level 2. Now use the 5×5 GRID above.",
                                "[HINT 2] Each (row,col) pair → one letter. (1,3)=C  (2,4)=I  (3,5)=P",
                                "[HINT 3] 6 pairs → CIPHER. Checksum=3+9+16+8+5+18=59. Submit: cipher-59")
                },
                new Level[] {
                        // L1 EASY — Number Pattern → "delta" (cs=42)
                        new Level("delta", 42, "NUMBER PATTERN",
                                "04 -- 05 -- 12 -- 20 -- 01",
                                "[HINT 1] Each number is a letter's position (A=1 … Z=26).",
                                "[HINT 2] Map: 01=A  04=D  05=E  12=L  20=T",
                                "[HINT 3] 5 numbers → 5 letters. The word is your team code-name."),

                        // L2 MEDIUM — Number Pattern → "grid" (cs=38)
                        new Level("grid", 38, "NUMBER PATTERN",
                                "07 -- 18 -- 09 -- 04",
                                "[HINT 1] Each number is a letter's position (A=1 … Z=26).",
                                "[HINT 2] Map: 04=D  07=G  09=I  18=R",
                                "[HINT 3] Answer: GRID. Level 3 uses a 5×5 grid (Polybius Square)." +
                                        " Each (row,col) coordinate maps to one letter."),

                        // L3 HARD — Logic Gates → "cipher" (cs=59)
                        new Level("cipher", 59, "LOGIC GATE OUTPUT",
                                "GATE-1: 0.0.0.1.1\n" +
                                        "GATE-2: 0.1.0.0.1\n" +
                                        "GATE-3: 1.0.0.0.0\n" +
                                        "GATE-4: 0.1.0.0.0\n" +
                                        "GATE-5: 0.0.1.0.1\n" +
                                        "GATE-6: 1.0.0.1.0\n" +
                                        "[KEY: A=00001  Z=11010  dots separate bits]",
                                "[HINT 1] Each GATE row = 5-bit binary. Ignore dots.",
                                "[HINT 2] Convert binary → decimal = letter position (A=1 … Z=26)." +
                                        " GATE-1: 00011=3=C  GATE-2: 01001=9=I",
                                "[HINT 3] 6 gates → CIPHER. Checksum=59. Submit: cipher-59")
                }));

        // ── SIGMA → final keyword: SIGNAL (cs=62) ────────────────────────
        PUZZLES.put("SIGMA", new PuzzleSet(
                new Level[] {
                        // L1 EASY — Number Pattern → "sigma" (cs=49)
                        new Level("sigma", 49, "NUMBER PATTERN",
                                "19 -- 09 -- 07 -- 13 -- 01",
                                "[HINT 1] Each number is a letter's position (A=1 … Z=26).",
                                "[HINT 2] Map: 01=A  07=G  09=I  13=M  19=S",
                                "[HINT 3] 5 numbers → 5 letters. The word is your team code-name."),

                        // L2 MEDIUM — Morse → "binary" (cs=69)
                        new Level("binary", 69, "MORSE CODE",
                                "-... / .. / -. / .- / .-. / -.--",
                                "[HINT 1] International Morse Code. Each group = one letter.",
                                "[HINT 2] -...=B  ..=I  -.=N  .-=A  .-.=R  -.--=Y",
                                "[HINT 3] Answer: BINARY — the encoding method in Level 3." +
                                        " Convert each 5-bit binary group to decimal, then to a letter."),

                        // L3 HARD — Binary → "signal" (cs=62)
                        new Level("signal", 62, "BINARY DECODE",
                                "10011 | 01001 | 00111 | 01110 | 00001 | 01100",
                                "[HINT 1] You decoded BINARY in Level 2. Now use that method here.",
                                "[HINT 2] Convert each 5-bit group: 10011=19=S  01001=9=I  00111=7=G",
                                "[HINT 3] 6 groups → SIGNAL. Checksum=19+9+7+14+1+12=62. Submit: signal-62")
                },
                new Level[] {
                        // L1 EASY — Number Pattern → "sigma" (cs=49)
                        new Level("sigma", 49, "NUMBER PATTERN",
                                "19 -- 09 -- 07 -- 13 -- 01",
                                "[HINT 1] Each number is a letter's position (A=1 … Z=26).",
                                "[HINT 2] Map: 01=A  07=G  09=I  13=M  19=S",
                                "[HINT 3] 5 numbers → 5 letters. The word is your team code-name."),

                        // L2 MEDIUM — Number Pattern → "binary" (cs=69)
                        new Level("binary", 69, "NUMBER PATTERN",
                                "02 -- 09 -- 14 -- 01 -- 18 -- 25",
                                "[HINT 1] Each number is a letter's position (A=1 … Z=26).",
                                "[HINT 2] Map: 01=A  02=B  09=I  14=N  18=R  25=Y",
                                "[HINT 3] Answer: BINARY — Level 3 uses Morse Code (dots & dashes)." +
                                        " ...=S  ..=I  --.=G  -.=N  .-=A  .-..=L"),

                        // L3 HARD — Morse → "signal" (cs=62)
                        new Level("signal", 62, "MORSE CODE",
                                "... .. --. -. .- .-..",
                                "[HINT 1] Morse Code — spaces separate letters.",
                                "[HINT 2] ...=S  ..=I  --.=G  -.=N  .-=A  .-..=L",
                                "[HINT 3] 6 groups → SIGNAL. Checksum=62. Submit: signal-62")
                }));

        // ── THETA → final keyword: PHOENIX (cs=91) ───────────────────────
        PUZZLES.put("THETA", new PuzzleSet(
                new Level[] {
                        new Level("theta", 54, "NUMBER PATTERN",
                                "20 -- 08 -- 05 -- 20 -- 01",
                                "[HINT 1] Each number is a letter's position (A=1 … Z=26).",
                                "[HINT 2] Map: 01=A  05=E  08=H  20=T",
                                "[HINT 3] 5 numbers → 5 letters. Your team code-name."),
                        new Level("bird", 33, "MORSE CODE",
                                "-... / .. / .-. / -..",
                                "[HINT 1] Morse Code. Each group = one letter.",
                                "[HINT 2] -...=B  ..=I  .-.=R  -..=D",
                                "[HINT 3] Answer: BIRD. Level 3 is an ANAGRAM of a mythical BIRD name." +
                                        " Letters: N,X,O,P,H,I,E — rearrange to name the fiery bird."),
                        new Level("phoenix", 91, "ANAGRAM",
                                "SCRAMBLED: N -- X -- O -- P -- H -- I -- E",
                                "[HINT 1] Level 2 told you the answer is a mythical BIRD name.",
                                "[HINT 2] 7 letters. The bird is reborn from ashes. Starts with P.",
                                "[HINT 3] PHOENIX. Checksum=16+8+15+5+14+9+24=91. Submit: phoenix-91")
                },
                new Level[] {
                        new Level("theta", 54, "NUMBER PATTERN",
                                "20 -- 08 -- 05 -- 20 -- 01",
                                "[HINT 1] Each number is a letter's position (A=1 … Z=26).",
                                "[HINT 2] Map: 01=A  05=E  08=H  20=T",
                                "[HINT 3] 5 numbers → 5 letters. Your team code-name."),
                        new Level("bird", 33, "NUMBER PATTERN",
                                "02 -- 09 -- 18 -- 04",
                                "[HINT 1] Each number is a letter's position (A=1 … Z=26).",
                                "[HINT 2] Map: 02=B  04=D  09=I  18=R",
                                "[HINT 3] Answer: BIRD. Level 3 maps numbers directly to PHOENIX letters." +
                                        " P=16 H=8 O=15 E=5 N=14 I=9 X=24"),
                        new Level("phoenix", 91, "NUMBER PATTERN",
                                "16 -- 08 -- 15 -- 05 -- 14 -- 09 -- 24",
                                "[HINT 1] Level 2 said BIRD — now decode the bird's name from numbers.",
                                "[HINT 2] Map: 05=E  08=H  09=I  14=N  15=O  16=P  24=X",
                                "[HINT 3] 7 numbers → PHOENIX. Checksum=91. Submit: phoenix-91")
                }));

        // ── KAPPA → final keyword: QUANTUM (cs=107) ──────────────────────
        PUZZLES.put("KAPPA", new PuzzleSet(
                new Level[] {
                        new Level("kappa", 45, "NUMBER PATTERN",
                                "11 -- 01 -- 16 -- 16 -- 01",
                                "[HINT 1] Each number is a letter's position (A=1 … Z=26).",
                                "[HINT 2] Map: 01=A  11=K  16=P",
                                "[HINT 3] 5 numbers → 5 letters. Your team code-name."),
                        new Level("five", 42, "ANAGRAM",
                                "SCRAMBLED: I -- V -- E -- F",
                                "[HINT 1] Rearrange ALL 4 letters to form a number word.",
                                "[HINT 2] The number is less than ten. It has an F.",
                                "[HINT 3] Answer: FIVE — the Caesar shift value for Level 3." +
                                        " Shift each cipher letter BACKWARD by 5. V→Q  Z→U  F→A."),
                        new Level("quantum", 107, "CAESAR CIPHER",
                                "VZFSYZR NX YMJ PJD YT UTBJW",
                                "[HINT 1] Caesar Cipher. Level 2 gave you the shift value.",
                                "[HINT 2] Shift = 5. Decode: V→Q  Z→U  F→A  S→N  Y→T  R→M",
                                "[HINT 3] First word = QUANTUM. Checksum=107. Submit: quantum-107")
                },
                new Level[] {
                        new Level("kappa", 45, "NUMBER PATTERN",
                                "11 -- 01 -- 16 -- 16 -- 01",
                                "[HINT 1] Each number is a letter's position (A=1 … Z=26).",
                                "[HINT 2] Map: 01=A  11=K  16=P",
                                "[HINT 3] 5 numbers → 5 letters. Your team code-name."),
                        new Level("five", 42, "NUMBER PATTERN",
                                "06 -- 09 -- 22 -- 05",
                                "[HINT 1] Each number is a letter's position (A=1 … Z=26).",
                                "[HINT 2] Map: 05=E  06=F  09=I  22=V",
                                "[HINT 3] Answer: FIVE — each Level-3 binary group decodes to a number." +
                                        " Q=17=10001  U=21=10101  A=1=00001  N=14=01110  T=20=10100"),
                        new Level("quantum", 107, "BINARY DECODE",
                                "10001 | 10101 | 00001 | 01110 | 10100 | 10101 | 01101",
                                "[HINT 1] Binary decode. Level 2 hinted the letter positions.",
                                "[HINT 2] 10001=17=Q  10101=21=U  00001=1=A  01110=14=N",
                                "[HINT 3] 7 groups → QUANTUM. Checksum=107. Submit: quantum-107")
                }));

        // ── LAMBDA → final keyword: VORTEX (cs=104) ──────────────────────
        PUZZLES.put("LAMBDA", new PuzzleSet(
                new Level[] {
                        new Level("lambda", 33, "NUMBER PATTERN",
                                "12 -- 01 -- 13 -- 02 -- 04 -- 01",
                                "[HINT 1] Each number is a letter's position (A=1 … Z=26).",
                                "[HINT 2] Map: 01=A  02=B  04=D  12=L  13=M",
                                "[HINT 3] 6 numbers → 6 letters. Your team code-name."),
                        new Level("code", 27, "NUMBER PATTERN",
                                "03 -- 15 -- 04 -- 05",
                                "[HINT 1] Each number is a letter's position (A=1 … Z=26).",
                                "[HINT 2] Map: 03=C  04=D  05=E  15=O",
                                "[HINT 3] Answer: CODE — Level 3 is MORSE CODE." +
                                        " ...-=V  ---=O  .-.=R  -=T  .=E  -..=X"),
                        new Level("vortex", 104, "MORSE CODE",
                                "...- / --- / .-. / - / . / -..-",
                                "[HINT 1] Morse Code. Level 2 told you this cipher's name.",
                                "[HINT 2] ...-=V  ---=O  .-.=R  -=T  .=E  -..=X",
                                "[HINT 3] 6 groups → VORTEX. Checksum=22+15+18+20+5+24=104. Submit: vortex-104")
                },
                new Level[] {
                        new Level("lambda", 33, "NUMBER PATTERN",
                                "12 -- 01 -- 13 -- 02 -- 04 -- 01",
                                "[HINT 1] Each number is a letter's position (A=1 … Z=26).",
                                "[HINT 2] Map: 01=A  02=B  04=D  12=L  13=M",
                                "[HINT 3] 6 numbers → 6 letters. Your team code-name."),
                        new Level("code", 27, "ANAGRAM",
                                "SCRAMBLED: O -- D -- C -- E",
                                "[HINT 1] Rearrange ALL 4 letters to form a common word.",
                                "[HINT 2] The word means a system of rules or a cipher system.",
                                "[HINT 3] Answer: CODE — Level 3 is a Polybius Square." +
                                        " (5,1)=V  (3,4)=O  (4,2)=R  (4,4)=T  (1,5)=E  (5,3)=X"),
                        new Level("vortex", 104, "POLYBIUS SQUARE",
                                "   [1][2][3][4][5]\n" +
                                        "1: [A][B][C][D][E]\n" +
                                        "2: [F][G][H][I][K]\n" +
                                        "3: [L][M][N][O][P]\n" +
                                        "4: [Q][R][S][T][U]\n" +
                                        "5: [V][W][X][Y][Z]\n" +
                                        "SEQUENCE: (5,1)-(3,4)-(4,2)-(4,4)-(1,5)-(5,3)",
                                "[HINT 1] Use the 5×5 grid. Each (row,col) → one letter.",
                                "[HINT 2] (5,1)=V  (3,4)=O  (4,2)=R  (4,4)=T  (1,5)=E  (5,3)=X",
                                "[HINT 3] 6 pairs → VORTEX. Checksum=104. Submit: vortex-104")
                }));

        // ── MU → final keyword: ZENITH (cs=82) ───────────────────────────
        PUZZLES.put("MU", new PuzzleSet(
                new Level[] {
                        new Level("north", 75, "NUMBER PATTERN",
                                "14 -- 15 -- 18 -- 20 -- 08",
                                "[HINT 1] Each number is a letter's position (A=1 … Z=26).",
                                "[HINT 2] Map: 08=H  14=N  15=O  18=R  20=T",
                                "[HINT 3] 5 numbers → 5 letters. A direction word. Submit lowercase."),
                        new Level("math", 42, "MORSE CODE",
                                "-- / .- / - / ....",
                                "[HINT 1] Morse Code. Each group = one letter.",
                                "[HINT 2] --=M  .-=A  -=T  ....=H",
                                "[HINT 3] Answer: MATH — Level 3 uses MATH expressions." +
                                        " Solve each bracket, convert result to letter (A=1 … Z=26)."),
                        new Level("zenith", 82, "MATH SEQUENCE",
                                "[ 13x2 ] -> [ 15-10 ] -> [ 7x2 ] -> [ 3x3 ] -> [ 4x5 ] -> [ 2x4 ]",
                                "[HINT 1] Solve each bracket. Result = letter position (A=1 … Z=26).",
                                "[HINT 2] 13x2=26=Z  15-10=5=E  7x2=14=N  3x3=9=I",
                                "[HINT 3] 6 results → ZENITH. Checksum=26+5+14+9+20+8=82. Submit: zenith-82")
                },
                new Level[] {
                        new Level("north", 75, "NUMBER PATTERN",
                                "14 -- 15 -- 18 -- 20 -- 08",
                                "[HINT 1] Each number is a letter's position (A=1 … Z=26).",
                                "[HINT 2] Map: 08=H  14=N  15=O  18=R  20=T",
                                "[HINT 3] 5 numbers → 5 letters. A direction word. Submit lowercase."),
                        new Level("math", 42, "NUMBER PATTERN",
                                "13 -- 01 -- 20 -- 08",
                                "[HINT 1] Each number is a letter's position (A=1 … Z=26).",
                                "[HINT 2] Map: 01=A  08=H  13=M  20=T",
                                "[HINT 3] Answer: MATH. Level 3 uses math: 13×2=26=Z  15-10=5=E  7×2=14=N" +
                                        "  3×3=9=I  4×5=20=T  2×4=8=H → ZENITH"),
                        new Level("zenith", 82, "MATH SEQUENCE",
                                "[ 13x2 ] -> [ 15-10 ] -> [ 7x2 ] -> [ 3x3 ] -> [ 4x5 ] -> [ 2x4 ]",
                                "[HINT 1] Solve each bracket. Result = letter position (A=1 … Z=26).",
                                "[HINT 2] 13x2=26=Z  15-10=5=E  7x2=14=N  3x3=9=I",
                                "[HINT 3] 6 results → ZENITH. Checksum=82. Submit: zenith-82")
                }));

        // ── NU → final keyword: ENIGMA (cs=49) ───────────────────────────
        PUZZLES.put("NU", new PuzzleSet(
                new Level[] {
                        new Level("shadow", 70, "NUMBER PATTERN",
                                "19 -- 08 -- 01 -- 04 -- 15 -- 23",
                                "[HINT 1] Each number is a letter's position (A=1 … Z=26).",
                                "[HINT 2] Map: 01=A  04=D  08=H  15=O  19=S  23=W",
                                "[HINT 3] 6 numbers → 6 letters. A word meaning mystery or darkness."),
                        new Level("three", 56, "ANAGRAM",
                                "SCRAMBLED: T -- R -- H -- E -- E",
                                "[HINT 1] Rearrange ALL 5 letters to form a number word.",
                                "[HINT 2] The number is less than five. It contains two E's.",
                                "[HINT 3] Answer: THREE — the Caesar shift for Level 3." +
                                        " Shift each cipher letter BACK by 3. H→E  Q→N  L→I."),
                        new Level("enigma", 49, "CAESAR CIPHER",
                                "HQLJPD LV WKH KLGGHQ PBVWHUB",
                                "[HINT 1] Caesar Cipher. Level 2 gave you the shift value.",
                                "[HINT 2] Shift = 3. Decode: H→E  Q→N  L→I  J→G  P→M  D→A",
                                "[HINT 3] First word = ENIGMA. Checksum=5+14+9+7+13+1=49. Submit: enigma-49")
                },
                new Level[] {
                        new Level("shadow", 70, "NUMBER PATTERN",
                                "19 -- 08 -- 01 -- 04 -- 15 -- 23",
                                "[HINT 1] Each number is a letter's position (A=1 … Z=26).",
                                "[HINT 2] Map: 01=A  04=D  08=H  15=O  19=S  23=W",
                                "[HINT 3] 6 numbers → 6 letters. A word meaning mystery or darkness."),
                        new Level("three", 56, "MORSE CODE",
                                "- / .... / .-. / . / .",
                                "[HINT 1] Morse Code. Each group = one letter.",
                                "[HINT 2] -=T  ....=H  .-.=R  .=E",
                                "[HINT 3] Answer: THREE — the shift key for Level 3 Caesar cipher." +
                                        " Decode: H-3=E  Q-3=N  L-3=I  J-3=G  P-3=M  D-3=A"),
                        new Level("enigma", 49, "BINARY DECODE",
                                "00101 | 01110 | 01001 | 00111 | 01101 | 00001",
                                "[HINT 1] Binary decode. Each 5-bit group → decimal → letter.",
                                "[HINT 2] 00101=5=E  01110=14=N  01001=9=I  00111=7=G",
                                "[HINT 3] 6 groups → ENIGMA. Checksum=49. Submit: enigma-49")
                }));
    }

    // ── Spring Config ──────────────────────────────────────────────
    @Value("${twinlock.duration-minutes:30}")
    private int durationMinutes;
    @Value("${twinlock.duration-seconds:0}")
    private int durationSeconds;
    @Value("${twinlock.secret-salt:TWINLOCK_DEFAULT_SALT}")
    private String secretSalt;
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
    private final ConcurrentHashMap<String, NodeSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, String> credentials = new HashMap<>();
    private volatile boolean eventStarted = false;
    private volatile LocalDateTime eventStartTime = null;

    public TwinLockService(Environment env) {
        this.env = env;
    }

    @PostConstruct
    public void init() {
        if (teamCount > 0) {
            log.info("[INIT] Auto-generating credentials for {} teams.", teamCount);
            for (int i = 1; i <= teamCount; i++) {
                String tid = teamPrefix + String.format("%02d", i);
                credentials.put(tid + "_SYS-01", deriveKey(tid, "SYS-01"));
                credentials.put(tid + "_SYS-02", deriveKey(tid, "SYS-02"));
            }
        } else {
            try {
                ((org.springframework.core.env.AbstractEnvironment) env)
                        .getPropertySources().forEach(ps -> {
                            if (ps instanceof org.springframework.core.env.EnumerablePropertySource<?> eps) {
                                for (String name : eps.getPropertyNames()) {
                                    if (name.startsWith("twinlock.cred.")) {
                                        String rest = name.substring("twinlock.cred.".length());
                                        int dot = rest.lastIndexOf('.');
                                        if (dot > 0) {
                                            String tid = rest.substring(0, dot).toUpperCase();
                                            String nid = rest.substring(dot + 1).toUpperCase();
                                            credentials.put(tid + "_" + nid, env.getProperty(name));
                                        }
                                    }
                                }
                            }
                        });
            } catch (Exception e) {
                log.error("Failed to load credentials", e);
            }
        }
        log.info("[INIT] TwinLock ready. {} credentials, {} puzzles.", credentials.size(), PUZZLES.size());
    }

    private String deriveKey(String teamId, String nodeId) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretSalt.getBytes("UTF-8"), "HmacSHA256"));
            byte[] hash = mac.doFinal((teamId + "_" + nodeId).getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash)
                sb.append(String.format("%02X", b));
            return sb.substring(0, 8);
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
        log.info("[AUTH] {} / {}", teamId, nodeId);
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
        resp.put("attemptsRemaining", s.getLevelAttemptsRemaining());
        resp.put("eventActive", isActive());
        resp.put("level", s.getCurrentLevel());
        return resp;
    }

    // ════════════════════════════════════════════════════════════════
    // NODE STATUS
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

        int level = s.getCurrentLevel();
        resp.put("authenticated", true);
        resp.put("attemptsRemaining", s.getLevelAttemptsRemaining());
        resp.put("nodeLocked", s.isPermanentlyLocked());
        resp.put("unlocked", s.isUnlocked());
        resp.put("level", level);

        // partner info
        final String[] partnerNodeId = { "" };
        final boolean[] partnerUnlocked = { false };
        final boolean[] partnerConn = { false };
        sessions.forEach((k, sess) -> {
            if (teamId.equals(sess.getTeamId()) && !nodeId.equals(sess.getNodeId()) && sess.isAuthenticated()) {
                partnerNodeId[0] = sess.getNodeId();
                partnerUnlocked[0] = sess.isUnlocked();
                partnerConn[0] = true;
            }
        });
        resp.put("partnerConnected", partnerConn[0]);
        resp.put("partnerUnlocked", partnerUnlocked[0]);
        if (partnerConn[0])
            resp.put("partnerNodeId", partnerNodeId[0]);

        if (active) {
            Level lev = currentLevel(teamId, nodeId, s);
            resp.put("timeRemainingSeconds", getTimeRemainingSeconds());
            resp.put("cipher", lev.cipherText);
            resp.put("cipherType", lev.cipherType);
            resp.put("hints", Arrays.asList(lev.hints));
        }
        return resp;
    }

    // ════════════════════════════════════════════════════════════════
    // SUBMIT (multi-level)
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
        if (s.getLevelAttemptsRemaining() <= 0) {
            s.setPermanentlyLocked(true);
            resp.put("status", "LOCKED");
            return resp;
        }

        Level lev = currentLevel(teamId, nodeId, s);
        String[] p = payload.toLowerCase().split("-", 2);
        String kw = p[0];
        String cs = p.length > 1 ? p[1] : "";

        if (lev.keyword.equals(kw) && cs.equals(String.valueOf(lev.checksum))) {
            // ── CORRECT ────────────────────────────────────────────
            if (s.getCurrentLevel() < 3) {
                s.advanceLevel();
                Level next = currentLevel(teamId, nodeId, s);
                resp.put("status", "LEVEL_UP");
                resp.put("nextLevel", s.getCurrentLevel());
                resp.put("cipher", next.cipherText);
                resp.put("cipherType", next.cipherType);
                resp.put("hints", Arrays.asList(next.hints));
                resp.put("attemptsRemaining", s.getLevelAttemptsRemaining());
                log.info("[LEVEL_UP] {} / {} → Level {}", teamId, nodeId, s.getCurrentLevel());
            } else {
                s.setUnlocked(true);
                resp.put("status", "UNLOCK");
                resp.put("formLink", isNode1(nodeId) ? googleFormLinkNode1 : googleFormLinkNode2);
                resp.put("nodeRole", isNode1(nodeId) ? "PARTNER-A" : "PARTNER-B");
                log.info("[UNLOCK] {} / {}", teamId, nodeId);
            }
        } else {
            // ── WRONG ──────────────────────────────────────────────
            s.incrementLevelAttempts();
            log.info("[FAIL] {} / {} level {} attempt {}", teamId, nodeId, s.getCurrentLevel(), s.getLevelAttempts());
            if (s.getLevelAttemptsRemaining() <= 0) {
                s.setPermanentlyLocked(true);
                resp.put("status", "LOCKED");
                log.warn("[LOCK] {} / {}", teamId, nodeId);
            } else {
                resp.put("status", "FAIL");
                resp.put("attemptsRemaining", s.getLevelAttemptsRemaining());
            }
        }
        return resp;
    }

    // ════════════════════════════════════════════════════════════════
    // ADMIN
    // ════════════════════════════════════════════════════════════════

    public Map<String, Object> startEvent() {
        if (isActive())
            return Map.of("status", "ALREADY_RUNNING", "message", "Already running.", "timeRemainingSeconds",
                    getTimeRemainingSeconds());
        eventStarted = true;
        eventStartTime = LocalDateTime.now();
        log.info("[ADMIN] Event STARTED");
        return Map.of("status", "STARTED", "message", "Event started.");
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
            n.put("level", s.getCurrentLevel());
            n.put("attemptsUsed", s.getLevelAttempts());
            n.put("attemptsRemaining", s.getLevelAttemptsRemaining());
            n.put("unlocked", s.isUnlocked());
            n.put("locked", s.isPermanentlyLocked());
            PuzzleSet ps = PUZZLES.get(s.getTeamId());
            n.put("keyword", ps != null ? ps.keyword : "N/A");
            n.put("checksum", ps != null ? ps.checksum : 0);
            nodes.add(n);
        });
        nodes.sort(Comparator.comparing(m -> m.get("teamId").toString() + m.get("nodeId").toString()));
        resp.put("nodes", nodes);
        return resp;
    }

    public void resetNode(String teamId, String nodeId) {
        NodeSession fresh = new NodeSession(teamId, nodeId);
        fresh.setAuthenticated(true);
        sessions.put(teamId + "_" + nodeId, fresh);
        log.info("[ADMIN] Reset: {} / {}", teamId, nodeId);
    }

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
    // HELPERS
    // ════════════════════════════════════════════════════════════════

    private Level currentLevel(String teamId, String nodeId, NodeSession s) {
        PuzzleSet p = getPuzzle(teamId);
        Level[] lev = isNode1(nodeId) ? p.node1Levels : p.node2Levels;
        return lev[Math.min(s.getCurrentLevel() - 1, 2)];
    }

    private PuzzleSet getPuzzle(String teamId) {
        PuzzleSet p = PUZZLES.get(teamId);
        if (p != null)
            return p;
        List<PuzzleSet> all = new ArrayList<>(PUZZLES.values());
        int num = 0;
        try {
            num = Integer.parseInt(teamId.replaceAll("[^0-9]", ""));
        } catch (Exception ignored) {
        }
        return all.get(num % all.size());
    }

    private boolean isNode1(String nodeId) {
        return nodeId.endsWith("01") || nodeId.equals("SYS-01");
    }

    // legacy helpers (still used externally)
    public String getCipherText(String teamId, String nodeId) {
        NodeSession s = sessions.get(teamId + "_" + nodeId);
        return currentLevel(teamId, nodeId, s != null ? s : new NodeSession(teamId, nodeId)).cipherText;
    }

    public String getCipherType(String teamId, String nodeId) {
        NodeSession s = sessions.get(teamId + "_" + nodeId);
        return currentLevel(teamId, nodeId, s != null ? s : new NodeSession(teamId, nodeId)).cipherType;
    }

    public String[] getHints(String teamId, String nodeId) {
        NodeSession s = sessions.get(teamId + "_" + nodeId);
        return currentLevel(teamId, nodeId, s != null ? s : new NodeSession(teamId, nodeId)).hints;
    }

    // ── Timer ─────────────────────────────────────────────────────
    private LocalDateTime eventEnd() {
        if (eventStartTime == null)
            return LocalDateTime.MIN;
        return durationSeconds > 0 ? eventStartTime.plusSeconds(durationSeconds)
                : eventStartTime.plusMinutes(durationMinutes);
    }

    public boolean isActive() {
        return eventStarted && eventStartTime != null && LocalDateTime.now().isBefore(eventEnd());
    }

    public long getTimeRemainingSeconds() {
        return isActive() ? Math.max(0, Duration.between(LocalDateTime.now(), eventEnd()).getSeconds()) : 0;
    }
}
