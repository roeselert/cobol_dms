>>SOURCE FORMAT FREE
*> 00STORC0 — platform: filesystem object store under
*> $DMS_DATA_DIR/objects, mirroring FilesystemObjectStore. The binary
*> is staged elsewhere (objects/tmp) and PUTF renames it into place —
*> durable before any metadata is written (R-1). Ops:
*>   VRFY ensure base dir      PUTF rename S-SRC -> <base>/<key>
*>   PATH resolve key -> path  EXIS existence check   DEL unlink
*> L-RET: 00 ok · 23 not found · 91 storage unavailable · 92 bad key.
IDENTIFICATION DIVISION.
PROGRAM-ID. "00STORC0".
DATA DIVISION.
WORKING-STORAGE SECTION.
01 WS-BASE              PIC X(256).
01 WS-DATADIR           PIC X(200).
01 WS-TARGET            PIC X(600).
01 WS-PARENT            PIC X(600).
01 WS-CMD               PIC X(700).
01 WS-CSRC              PIC X(600).
01 WS-CDST              PIC X(600).
01 WS-RC                USAGE BINARY-LONG.
01 WS-I                 PIC 9(4) COMP.
01 WS-SLASH             PIC 9(4) COMP.
01 WS-LEN               PIC 9(4) COMP.
LINKAGE SECTION.
01 S-OP                 PIC X(4).
01 S-RET                PIC X(2).
01 S-KEY                PIC X(200).
01 S-SRC                PIC X(512).
01 S-PATH               PIC X(512).
PROCEDURE DIVISION USING S-OP S-RET S-KEY S-SRC S-PATH.
MAIN.
    MOVE "00" TO S-RET
    PERFORM RESOLVE-BASE
    IF S-OP NOT = "VRFY"
        PERFORM RESOLVE-TARGET
        IF S-RET NOT = "00"
            GOBACK
        END-IF
    END-IF
    EVALUATE S-OP
        WHEN "VRFY" PERFORM DO-VERIFY
        WHEN "PUTF" PERFORM DO-PUT-FILE
        WHEN "PATH" MOVE WS-TARGET TO S-PATH
        WHEN "EXIS" PERFORM DO-EXISTS
        WHEN "DEL " PERFORM DO-DELETE
        WHEN OTHER  MOVE "99" TO S-RET
    END-EVALUATE
    GOBACK.

RESOLVE-BASE.
    ACCEPT WS-DATADIR FROM ENVIRONMENT "DMS_DATA_DIR"
        ON EXCEPTION MOVE SPACES TO WS-DATADIR
    END-ACCEPT
    IF WS-DATADIR = SPACES
        MOVE "/data" TO WS-DATADIR
    END-IF
    MOVE SPACES TO WS-BASE
    STRING FUNCTION TRIM (WS-DATADIR) "/objects"
        DELIMITED BY SIZE INTO WS-BASE
    END-STRING.

RESOLVE-TARGET.
    *> path-traversal guard (mirrors FilesystemObjectStore.resolve)
    MOVE FUNCTION STORED-CHAR-LENGTH (S-KEY) TO WS-LEN
    IF WS-LEN = 0
        MOVE "92" TO S-RET
        EXIT PARAGRAPH
    END-IF
    PERFORM VARYING WS-I FROM 1 BY 1 UNTIL WS-I >= WS-LEN
        IF S-KEY (WS-I : 2) = ".."
            MOVE "92" TO S-RET
            EXIT PARAGRAPH
        END-IF
    END-PERFORM
    MOVE SPACES TO WS-TARGET
    STRING FUNCTION TRIM (WS-BASE) "/" S-KEY (1 : WS-LEN)
        DELIMITED BY SIZE INTO WS-TARGET
    END-STRING.

DO-VERIFY.
    MOVE SPACES TO WS-CMD
    STRING "mkdir -p '" FUNCTION TRIM (WS-BASE) "/tmp'"
        DELIMITED BY SIZE INTO WS-CMD
    END-STRING
    CALL "SYSTEM" USING WS-CMD RETURNING WS-RC
    IF WS-RC NOT = 0
        MOVE "91" TO S-RET
    END-IF.

DO-PUT-FILE.
    *> parent dir of the target key
    MOVE FUNCTION STORED-CHAR-LENGTH (WS-TARGET) TO WS-LEN
    MOVE 0 TO WS-SLASH
    PERFORM VARYING WS-I FROM 1 BY 1 UNTIL WS-I > WS-LEN
        IF WS-TARGET (WS-I : 1) = "/"
            MOVE WS-I TO WS-SLASH
        END-IF
    END-PERFORM
    IF WS-SLASH > 1
        MOVE SPACES TO WS-PARENT
        MOVE WS-TARGET (1 : WS-SLASH - 1) TO WS-PARENT
        MOVE SPACES TO WS-CMD
        STRING "mkdir -p '" FUNCTION TRIM (WS-PARENT) "'"
            DELIMITED BY SIZE INTO WS-CMD
        END-STRING
        CALL "SYSTEM" USING WS-CMD RETURNING WS-RC
        IF WS-RC NOT = 0
            MOVE "91" TO S-RET
            EXIT PARAGRAPH
        END-IF
    END-IF
    *> rename(2): atomic within the same filesystem (staging lives
    *> under objects/tmp), replacing any previous binary for the key
    MOVE SPACES TO WS-CSRC
    STRING FUNCTION TRIM (S-SRC) X"00" DELIMITED BY SIZE
        INTO WS-CSRC
    END-STRING
    MOVE SPACES TO WS-CDST
    STRING FUNCTION TRIM (WS-TARGET) X"00" DELIMITED BY SIZE
        INTO WS-CDST
    END-STRING
    CALL "rename" USING WS-CSRC WS-CDST RETURNING WS-RC
    IF WS-RC NOT = 0
        MOVE "91" TO S-RET
    END-IF.

DO-EXISTS.
    MOVE SPACES TO WS-CDST
    STRING FUNCTION TRIM (WS-TARGET) X"00" DELIMITED BY SIZE
        INTO WS-CDST
    END-STRING
    CALL "access" USING WS-CDST BY VALUE 0 RETURNING WS-RC
    IF WS-RC NOT = 0
        MOVE "23" TO S-RET
    END-IF
    MOVE WS-TARGET TO S-PATH.

DO-DELETE.
    MOVE SPACES TO WS-CDST
    STRING FUNCTION TRIM (WS-TARGET) X"00" DELIMITED BY SIZE
        INTO WS-CDST
    END-STRING
    CALL "unlink" USING WS-CDST RETURNING WS-RC.
END PROGRAM "00STORC0".
