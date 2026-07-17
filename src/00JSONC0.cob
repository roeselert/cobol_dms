>>SOURCE FORMAT FREE
*> 00JSONC0 — platform: extract a string value by key from a flat JSON
*> object (GnuCOBOL 3 has JSON GENERATE but no JSON PARSE — the parser
*> is our own, see TARGET-ARCHITECTURE.md §2). Returns L-FOUND = "Y"
*> with the unescaped value, or "N" for absent / null / non-string.
IDENTIFICATION DIVISION.
PROGRAM-ID. "00JSONC0".
DATA DIVISION.
WORKING-STORAGE SECTION.
01 WS-PATTERN           PIC X(66).
01 WS-PATLEN            PIC 9(4) COMP.
01 WS-BODYLEN           PIC 9(4) COMP.
01 WS-KEYLEN            PIC 9(4) COMP.
01 WS-I                 PIC 9(4) COMP.
01 WS-J                 PIC 9(4) COMP.
01 WS-O                 PIC 9(4) COMP.
01 WS-C                 PIC X.
01 WS-LIT               PIC X(64).
01 WS-LITLEN            PIC 9(4) COMP.
LINKAGE SECTION.
01 L-BODY               PIC X(4096).
01 L-KEY                PIC X(64).
01 L-VALUE              PIC X(512).
01 L-FOUND              PIC X.
PROCEDURE DIVISION USING L-BODY L-KEY L-VALUE L-FOUND.
MAIN.
    MOVE "N" TO L-FOUND
    MOVE SPACES TO L-VALUE
    MOVE FUNCTION STORED-CHAR-LENGTH (L-KEY) TO WS-KEYLEN
    MOVE FUNCTION STORED-CHAR-LENGTH (L-BODY) TO WS-BODYLEN
    IF WS-KEYLEN = 0 OR WS-BODYLEN = 0
        GOBACK
    END-IF
    MOVE SPACES TO WS-PATTERN
    STRING '"' L-KEY (1 : WS-KEYLEN) '"' DELIMITED BY SIZE
        INTO WS-PATTERN
    END-STRING
    COMPUTE WS-PATLEN = WS-KEYLEN + 2
    PERFORM VARYING WS-I FROM 1 BY 1
            UNTIL WS-I + WS-PATLEN > WS-BODYLEN + 1
        IF L-BODY (WS-I : WS-PATLEN) = WS-PATTERN (1 : WS-PATLEN)
            COMPUTE WS-J = WS-I + WS-PATLEN
            PERFORM SKIP-SPACES
            IF WS-J <= WS-BODYLEN AND L-BODY (WS-J : 1) = ":"
                ADD 1 TO WS-J
                PERFORM SKIP-SPACES
                PERFORM READ-VALUE
                GOBACK
            END-IF
        END-IF
    END-PERFORM
    GOBACK.

SKIP-SPACES.
    PERFORM UNTIL WS-J > WS-BODYLEN
            OR (L-BODY (WS-J : 1) NOT = SPACE
                AND L-BODY (WS-J : 1) NOT = X"09")
        ADD 1 TO WS-J
    END-PERFORM.

READ-VALUE.
    IF WS-J > WS-BODYLEN
        GOBACK
    END-IF
    IF L-BODY (WS-J : 1) = '"'
        PERFORM READ-STRING-VALUE
    ELSE
        PERFORM READ-LITERAL-VALUE
    END-IF.

READ-STRING-VALUE.
    ADD 1 TO WS-J
    MOVE 1 TO WS-O
    PERFORM UNTIL WS-J > WS-BODYLEN OR L-BODY (WS-J : 1) = '"'
        MOVE L-BODY (WS-J : 1) TO WS-C
        IF WS-C = "\" AND WS-J < WS-BODYLEN
            ADD 1 TO WS-J
            MOVE L-BODY (WS-J : 1) TO WS-C
            EVALUATE WS-C
                WHEN "n" MOVE SPACE TO WS-C
                WHEN "r" MOVE SPACE TO WS-C
                WHEN "t" MOVE SPACE TO WS-C
                WHEN OTHER CONTINUE
            END-EVALUATE
        END-IF
        IF WS-O <= 512
            MOVE WS-C TO L-VALUE (WS-O : 1)
            ADD 1 TO WS-O
        END-IF
        ADD 1 TO WS-J
    END-PERFORM
    MOVE "Y" TO L-FOUND.

READ-LITERAL-VALUE.
    *> number / true / false / null up to the next delimiter
    MOVE SPACES TO WS-LIT
    MOVE 1 TO WS-LITLEN
    PERFORM UNTIL WS-J > WS-BODYLEN
            OR L-BODY (WS-J : 1) = ","
            OR L-BODY (WS-J : 1) = "}"
            OR L-BODY (WS-J : 1) = "]"
            OR L-BODY (WS-J : 1) = SPACE
        IF WS-LITLEN <= 64
            MOVE L-BODY (WS-J : 1) TO WS-LIT (WS-LITLEN : 1)
            ADD 1 TO WS-LITLEN
        END-IF
        ADD 1 TO WS-J
    END-PERFORM
    IF WS-LIT = "null" OR WS-LITLEN = 1
        MOVE "N" TO L-FOUND
    ELSE
        MOVE WS-LIT TO L-VALUE
        MOVE "Y" TO L-FOUND
    END-IF.
END PROGRAM "00JSONC0".
