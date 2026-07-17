>>SOURCE FORMAT FREE
*> 00UUIDC0 — platform: UUID v4 generator + epoch-millis clock.
*> Every CGI request is a fresh process; the RNG is seeded once per
*> process from pid + time-of-day, so concurrent requests diverge.
IDENTIFICATION DIVISION.
PROGRAM-ID. "00UUIDC0".
DATA DIVISION.
WORKING-STORAGE SECTION.
01 WS-SEEDED            PIC X VALUE "N".
01 WS-PID               USAGE BINARY-LONG.
01 WS-SEED              PIC 9(9)  COMP.
01 WS-RAND              COMP-2.
01 WS-DIGIT             PIC 9(4)  COMP.
01 WS-HEXCHARS          PIC X(16) VALUE "0123456789abcdef".
01 WS-HEX               PIC X(32).
01 WS-I                 PIC 9(4)  COMP.
01 WS-CD.
   05 WS-CD-YEAR        PIC 9(4).
   05 WS-CD-MONTH       PIC 9(2).
   05 WS-CD-DAY         PIC 9(2).
   05 WS-CD-HH          PIC 9(2).
   05 WS-CD-MI          PIC 9(2).
   05 WS-CD-SS          PIC 9(2).
   05 WS-CD-CC          PIC 9(2).
   05 FILLER            PIC X(5).
01 WS-CD-DATE-NUM       PIC 9(8).
01 WS-DAYS              PIC S9(9) COMP.
LINKAGE SECTION.
01 L-UUID               PIC X(36).
01 L-EPOCH-MS           PIC 9(13).
PROCEDURE DIVISION USING L-UUID L-EPOCH-MS.
MAIN.
    MOVE FUNCTION CURRENT-DATE TO WS-CD
    PERFORM COMPUTE-EPOCH
    IF WS-SEEDED = "N"
        CALL "getpid" RETURNING WS-PID
        COMPUTE WS-SEED = FUNCTION MOD (
            WS-PID * 86400 + WS-CD-HH * 360000 + WS-CD-MI * 6000
            + WS-CD-SS * 100 + WS-CD-CC, 999999937)
        COMPUTE WS-RAND = FUNCTION RANDOM (WS-SEED)
        MOVE "Y" TO WS-SEEDED
    END-IF
    PERFORM VARYING WS-I FROM 1 BY 1 UNTIL WS-I > 32
        COMPUTE WS-RAND = FUNCTION RANDOM
        COMPUTE WS-DIGIT = FUNCTION MOD (
            FUNCTION INTEGER (WS-RAND * 65536), 16)
        MOVE WS-HEXCHARS (WS-DIGIT + 1 : 1) TO WS-HEX (WS-I : 1)
    END-PERFORM
    *> RFC 4122: version nibble 4, variant nibble 8..b
    MOVE "4" TO WS-HEX (13 : 1)
    COMPUTE WS-DIGIT = 8 + FUNCTION MOD (
        FUNCTION INTEGER (FUNCTION RANDOM * 65536), 4)
    MOVE WS-HEXCHARS (WS-DIGIT + 1 : 1) TO WS-HEX (17 : 1)
    STRING WS-HEX (1 : 8)  "-" WS-HEX (9 : 4)  "-"
           WS-HEX (13 : 4) "-" WS-HEX (17 : 4) "-"
           WS-HEX (21 : 12)
        DELIMITED BY SIZE INTO L-UUID
    END-STRING
    GOBACK.

COMPUTE-EPOCH.
    MOVE WS-CD (1 : 8) TO WS-CD-DATE-NUM
    COMPUTE WS-DAYS = FUNCTION INTEGER-OF-DATE (WS-CD-DATE-NUM)
                    - FUNCTION INTEGER-OF-DATE (19700101)
    COMPUTE L-EPOCH-MS =
        ((WS-DAYS * 86400)
         + WS-CD-HH * 3600 + WS-CD-MI * 60 + WS-CD-SS) * 1000
        + WS-CD-CC * 10.
END PROGRAM "00UUIDC0".
