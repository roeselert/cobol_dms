>>SOURCE FORMAT FREE
*> 00JSONC1 — platform: escape a string for embedding in a JSON
*> document ('"' and '\'; control chars become spaces). The counterpart
*> of 00JSONC0's unescape.
IDENTIFICATION DIVISION.
PROGRAM-ID. "00JSONC1".
DATA DIVISION.
WORKING-STORAGE SECTION.
01 WS-I                 PIC 9(4) COMP.
01 WS-O                 PIC 9(4) COMP.
01 WS-LEN               PIC 9(4) COMP.
01 WS-C                 PIC X.
LINKAGE SECTION.
01 L-IN                 PIC X(512).
01 L-OUT                PIC X(1024).
01 L-OUT-LEN            PIC 9(4).
PROCEDURE DIVISION USING L-IN L-OUT L-OUT-LEN.
MAIN.
    MOVE SPACES TO L-OUT
    MOVE FUNCTION STORED-CHAR-LENGTH (L-IN) TO WS-LEN
    MOVE 1 TO WS-O
    PERFORM VARYING WS-I FROM 1 BY 1 UNTIL WS-I > WS-LEN
        MOVE L-IN (WS-I : 1) TO WS-C
        IF WS-C = '"' OR WS-C = "\"
            IF WS-O < 1023
                MOVE "\" TO L-OUT (WS-O : 1)
                ADD 1 TO WS-O
                MOVE WS-C TO L-OUT (WS-O : 1)
                ADD 1 TO WS-O
            END-IF
        ELSE
            IF WS-C < SPACE
                MOVE SPACE TO WS-C
            END-IF
            IF WS-O < 1024
                MOVE WS-C TO L-OUT (WS-O : 1)
                ADD 1 TO WS-O
            END-IF
        END-IF
    END-PERFORM
    COMPUTE L-OUT-LEN = WS-O - 1
    GOBACK.
END PROGRAM "00JSONC1".
