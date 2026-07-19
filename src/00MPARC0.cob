>>SOURCE FORMAT FREE
*> 00MPARC0 — platform: multipart/form-data request parser. Called only
*> by the CGI dispatcher; business boundaries never see multipart. Reads
*> the whole body from fd 0 (read(2) — works under mod_cgid's socket
*> stdin), splits it on the boundary from CONTENT_TYPE, stages the file
*> part to $DMS_DATA_DIR/objects/tmp/up-<pid>.part (same filesystem as
*> the object store, so the later rename is atomic) and collects small
*> form fields into the exchange. SHA-256 via the sha256sum CLI —
*> pragmatic for this iteration, D-4 (libcrypto) stays open.
*> L-RET: 00 ok · 13 too large · 40 malformed · 91 storage error.
IDENTIFICATION DIVISION.
PROGRAM-ID. "00MPARC0".
ENVIRONMENT DIVISION.
INPUT-OUTPUT SECTION.
FILE-CONTROL.
    SELECT SHA-FILE ASSIGN TO WS-SHA-PATH
        ORGANIZATION IS LINE SEQUENTIAL
        FILE STATUS IS WS-SFS.
DATA DIVISION.
FILE SECTION.
FD SHA-FILE.
01 SHA-LINE             PIC X(200).
WORKING-STORAGE SECTION.
*> 100 MB upload cap + parser headroom
01 WS-BUF               PIC X(104875008).
01 WS-BUFMAX            PIC 9(9) COMP VALUE 104875008.
01 WS-BODYLEN           PIC 9(9) COMP.
01 WS-CLEN-X            PIC X(16).
01 WS-CTYPE             PIC X(300).
01 WS-BOUNDARY          PIC X(100).
01 WS-DELIM             PIC X(104).
01 WS-DELIMLEN          PIC 9(4) COMP.
01 WS-CRLF-DELIM        PIC X(106).
01 WS-CDLEN             PIC 9(4) COMP.
01 WS-TOTAL             PIC 9(9) COMP.
01 WS-REMAIN            USAGE BINARY-C-LONG.
01 WS-READ-N            USAGE BINARY-C-LONG.
01 WS-P                 PIC 9(9) COMP.
01 WS-I                 PIC 9(9) COMP.
01 WS-J                 PIC 9(9) COMP.
01 WS-K                 PIC 9(9) COMP.
01 WS-KSTART            PIC 9(9) COMP.
01 WS-FOUND             PIC 9(9) COMP.
01 WS-LINE-END          PIC 9(9) COMP.
01 WS-CONTENT-START     PIC 9(9) COMP.
01 WS-CONTENT-LEN       PIC 9(9) COMP.
01 WS-HDRLINE           PIC X(600).
01 WS-HDRLEN            PIC 9(4) COMP.
01 WS-HDRLOW            PIC X(600).
01 WS-PART-NAME         PIC X(64).
01 WS-PART-FILE         PIC X(255).
01 WS-PART-MIME         PIC X(100).
01 WS-HAS-FILENAME      PIC X.
01 WS-DATADIR           PIC X(200).
01 WS-PID               USAGE BINARY-LONG.
01 WS-PID-9              PIC 9(9).
01 WS-STAGE-PATH        PIC X(512).
01 WS-SHA-PATH          PIC X(520).
01 WS-CPATH             PIC X(520).
01 WS-CMD               PIC X(1200).
01 WS-FD                USAGE BINARY-LONG.
01 WS-WOFF              PIC 9(9) COMP.
01 WS-WLEN              USAGE BINARY-C-LONG.
01 WS-RC                USAGE BINARY-LONG.
01 WS-SFS               PIC X(2).
01 WS-CRLF              PIC X(2).
LINKAGE SECTION.
COPY "00HTTPR0.cpy".
01 L-RET                PIC X(2).
PROCEDURE DIVISION USING HTTP-EXCHANGE L-RET.
MAIN.
    MOVE "00" TO L-RET
    MOVE X"0D0A" TO WS-CRLF
    MOVE "N" TO HX-UP-PRESENT
    MOVE 0 TO HX-FF-COUNT

    PERFORM PARSE-BOUNDARY
    IF L-RET NOT = "00"
        GOBACK
    END-IF
    PERFORM READ-ALL-BODY
    IF L-RET NOT = "00"
        GOBACK
    END-IF
    PERFORM PARSE-PARTS
    GOBACK.

PARSE-BOUNDARY.
    ACCEPT WS-CTYPE FROM ENVIRONMENT "CONTENT_TYPE"
        ON EXCEPTION MOVE SPACES TO WS-CTYPE
    END-ACCEPT
    MOVE 0 TO WS-FOUND
    PERFORM VARYING WS-I FROM 1 BY 1 UNTIL WS-I > 290
        IF WS-CTYPE (WS-I : 9) = "boundary="
            COMPUTE WS-FOUND = WS-I + 9
            EXIT PERFORM
        END-IF
    END-PERFORM
    IF WS-FOUND = 0
        MOVE "40" TO L-RET
        EXIT PARAGRAPH
    END-IF
    MOVE SPACES TO WS-BOUNDARY
    MOVE 0 TO WS-J
    PERFORM VARYING WS-I FROM WS-FOUND BY 1
            UNTIL WS-I > 300
            OR WS-CTYPE (WS-I : 1) = ";"
            OR WS-CTYPE (WS-I : 1) = SPACE
        IF WS-CTYPE (WS-I : 1) NOT = '"'
            ADD 1 TO WS-J
            MOVE WS-CTYPE (WS-I : 1) TO WS-BOUNDARY (WS-J : 1)
        END-IF
    END-PERFORM
    IF WS-J = 0
        MOVE "40" TO L-RET
        EXIT PARAGRAPH
    END-IF
    MOVE SPACES TO WS-DELIM
    STRING "--" WS-BOUNDARY (1 : WS-J) DELIMITED BY SIZE
        INTO WS-DELIM
    END-STRING
    COMPUTE WS-DELIMLEN = WS-J + 2
    MOVE SPACES TO WS-CRLF-DELIM
    STRING WS-CRLF WS-DELIM (1 : WS-DELIMLEN) DELIMITED BY SIZE
        INTO WS-CRLF-DELIM
    END-STRING
    COMPUTE WS-CDLEN = WS-DELIMLEN + 2.

READ-ALL-BODY.
    ACCEPT WS-CLEN-X FROM ENVIRONMENT "CONTENT_LENGTH"
        ON EXCEPTION MOVE SPACES TO WS-CLEN-X
    END-ACCEPT
    COMPUTE WS-BODYLEN = FUNCTION NUMVAL (WS-CLEN-X)
    IF WS-BODYLEN = 0
        MOVE "40" TO L-RET
        EXIT PARAGRAPH
    END-IF
    IF WS-BODYLEN > WS-BUFMAX
        MOVE "13" TO L-RET
        EXIT PARAGRAPH
    END-IF
    MOVE 0 TO WS-TOTAL
    PERFORM UNTIL WS-TOTAL >= WS-BODYLEN
        COMPUTE WS-REMAIN = WS-BODYLEN - WS-TOTAL
        IF WS-REMAIN > 1048576
            MOVE 1048576 TO WS-REMAIN
        END-IF
        CALL "read" USING
            BY VALUE 0
            BY REFERENCE WS-BUF (WS-TOTAL + 1 : WS-REMAIN)
            BY VALUE WS-REMAIN
            RETURNING WS-READ-N
        END-CALL
        IF WS-READ-N <= 0
            EXIT PERFORM
        END-IF
        ADD WS-READ-N TO WS-TOTAL
    END-PERFORM
    IF WS-TOTAL < WS-BODYLEN
        MOVE "40" TO L-RET
    END-IF.

PARSE-PARTS.
    *> first delimiter opens the body
    MOVE 1 TO WS-P
    PERFORM FIND-DELIM-AT-P
    IF WS-FOUND = 0
        MOVE "40" TO L-RET
        EXIT PARAGRAPH
    END-IF
    COMPUTE WS-P = WS-FOUND + WS-DELIMLEN
    PERFORM UNTIL L-RET NOT = "00"
        IF WS-P + 1 <= WS-BODYLEN AND WS-BUF (WS-P : 2) = "--"
            EXIT PERFORM
        END-IF
        IF WS-P + 1 <= WS-BODYLEN AND WS-BUF (WS-P : 2) = WS-CRLF
            ADD 2 TO WS-P
        END-IF
        PERFORM PARSE-ONE-PART
    END-PERFORM.

FIND-DELIM-AT-P.
    MOVE 0 TO WS-FOUND
    PERFORM VARYING WS-I FROM WS-P BY 1
            UNTIL WS-I + WS-DELIMLEN - 1 > WS-BODYLEN
        IF WS-BUF (WS-I : 1) = "-"
                AND WS-BUF (WS-I : WS-DELIMLEN)
                    = WS-DELIM (1 : WS-DELIMLEN)
            MOVE WS-I TO WS-FOUND
            EXIT PERFORM
        END-IF
    END-PERFORM.

PARSE-ONE-PART.
    MOVE SPACES TO WS-PART-NAME WS-PART-FILE WS-PART-MIME
    MOVE "N" TO WS-HAS-FILENAME
    *> headers until the empty line
    PERFORM UNTIL L-RET NOT = "00"
        PERFORM READ-HEADER-LINE
        IF L-RET NOT = "00"
            EXIT PERFORM
        END-IF
        IF WS-HDRLEN = 0
            EXIT PERFORM
        END-IF
        PERFORM PARSE-HEADER-LINE
    END-PERFORM
    IF L-RET NOT = "00"
        EXIT PARAGRAPH
    END-IF
    MOVE WS-P TO WS-CONTENT-START
    *> content runs until CRLF + delimiter
    PERFORM FIND-CRLF-DELIM
    IF WS-FOUND = 0
        MOVE "40" TO L-RET
        EXIT PARAGRAPH
    END-IF
    COMPUTE WS-CONTENT-LEN = WS-FOUND - WS-CONTENT-START
    IF WS-HAS-FILENAME = "Y"
        PERFORM STAGE-FILE-PART
    ELSE
        PERFORM STORE-FORM-FIELD
    END-IF
    COMPUTE WS-P = WS-FOUND + WS-CDLEN.

READ-HEADER-LINE.
    MOVE 0 TO WS-LINE-END
    PERFORM VARYING WS-I FROM WS-P BY 1
            UNTIL WS-I + 1 > WS-BODYLEN
        IF WS-BUF (WS-I : 2) = WS-CRLF
            MOVE WS-I TO WS-LINE-END
            EXIT PERFORM
        END-IF
        IF WS-I - WS-P > 590
            EXIT PERFORM
        END-IF
    END-PERFORM
    IF WS-LINE-END = 0
        MOVE "40" TO L-RET
        EXIT PARAGRAPH
    END-IF
    COMPUTE WS-HDRLEN = WS-LINE-END - WS-P
    MOVE SPACES TO WS-HDRLINE
    IF WS-HDRLEN > 0
        MOVE WS-BUF (WS-P : WS-HDRLEN) TO WS-HDRLINE
    END-IF
    COMPUTE WS-P = WS-LINE-END + 2.

PARSE-HEADER-LINE.
    MOVE FUNCTION LOWER-CASE (WS-HDRLINE) TO WS-HDRLOW
    IF WS-HDRLOW (1 : 20) = "content-disposition:"
        PERFORM EXTRACT-NAME
        PERFORM EXTRACT-FILENAME
    END-IF
    IF WS-HDRLOW (1 : 13) = "content-type:"
        MOVE SPACES TO WS-PART-MIME
        MOVE 0 TO WS-J
        PERFORM VARYING WS-I FROM 14 BY 1 UNTIL WS-I > WS-HDRLEN
            IF WS-HDRLINE (WS-I : 1) NOT = SPACE
                    AND WS-HDRLINE (WS-I : 1) NOT = ";"
                ADD 1 TO WS-J
                IF WS-J <= 100
                    MOVE WS-HDRLINE (WS-I : 1)
                        TO WS-PART-MIME (WS-J : 1)
                END-IF
            END-IF
            IF WS-HDRLINE (WS-I : 1) = ";"
                EXIT PERFORM
            END-IF
        END-PERFORM
    END-IF.

EXTRACT-NAME.
    *> name="..." — must not match the tail of filename="..."
    PERFORM VARYING WS-I FROM 2 BY 1 UNTIL WS-I + 5 > WS-HDRLEN
        IF WS-HDRLINE (WS-I : 6) = 'name="'
                AND (WS-HDRLINE (WS-I - 1 : 1) = SPACE
                     OR WS-HDRLINE (WS-I - 1 : 1) = ";")
            MOVE SPACES TO WS-PART-NAME
            MOVE 0 TO WS-J
            COMPUTE WS-KSTART = WS-I + 6
            PERFORM VARYING WS-K FROM WS-KSTART BY 1
                    UNTIL WS-K > WS-HDRLEN
                    OR WS-HDRLINE (WS-K : 1) = '"'
                ADD 1 TO WS-J
                IF WS-J <= 64
                    MOVE WS-HDRLINE (WS-K : 1)
                        TO WS-PART-NAME (WS-J : 1)
                END-IF
            END-PERFORM
            EXIT PERFORM
        END-IF
    END-PERFORM.

EXTRACT-FILENAME.
    PERFORM VARYING WS-I FROM 1 BY 1 UNTIL WS-I + 9 > WS-HDRLEN
        IF WS-HDRLINE (WS-I : 10) = 'filename="'
            MOVE "Y" TO WS-HAS-FILENAME
            MOVE SPACES TO WS-PART-FILE
            MOVE 0 TO WS-J
            COMPUTE WS-KSTART = WS-I + 10
            PERFORM VARYING WS-K FROM WS-KSTART BY 1
                    UNTIL WS-K > WS-HDRLEN
                    OR WS-HDRLINE (WS-K : 1) = '"'
                ADD 1 TO WS-J
                IF WS-J <= 255
                    MOVE WS-HDRLINE (WS-K : 1)
                        TO WS-PART-FILE (WS-J : 1)
                END-IF
            END-PERFORM
            EXIT PERFORM
        END-IF
    END-PERFORM.

FIND-CRLF-DELIM.
    MOVE 0 TO WS-FOUND
    PERFORM VARYING WS-I FROM WS-P BY 1
            UNTIL WS-I + WS-CDLEN - 1 > WS-BODYLEN
        IF WS-BUF (WS-I : 1) = X"0D"
                AND WS-BUF (WS-I : WS-CDLEN)
                    = WS-CRLF-DELIM (1 : WS-CDLEN)
            MOVE WS-I TO WS-FOUND
            EXIT PERFORM
        END-IF
    END-PERFORM.

STAGE-FILE-PART.
    ACCEPT WS-DATADIR FROM ENVIRONMENT "DMS_DATA_DIR"
        ON EXCEPTION MOVE SPACES TO WS-DATADIR
    END-ACCEPT
    IF WS-DATADIR = SPACES
        MOVE "/data" TO WS-DATADIR
    END-IF
    CALL "getpid" RETURNING WS-PID
    MOVE WS-PID TO WS-PID-9
    MOVE SPACES TO WS-STAGE-PATH
    STRING FUNCTION TRIM (WS-DATADIR) "/objects/tmp/up-"
           WS-PID-9 ".part"
        DELIMITED BY SIZE INTO WS-STAGE-PATH
    END-STRING
    MOVE SPACES TO WS-CPATH
    STRING FUNCTION TRIM (WS-STAGE-PATH) X"00" DELIMITED BY SIZE
        INTO WS-CPATH
    END-STRING
    *> open(2) O_WRONLY|O_CREAT|O_TRUNC, mode 0644
    CALL "open" USING WS-CPATH BY VALUE 577 BY VALUE 420
        RETURNING WS-FD
    END-CALL
    IF WS-FD < 0
        MOVE "91" TO L-RET
        EXIT PARAGRAPH
    END-IF
    MOVE 0 TO WS-WOFF
    PERFORM UNTIL WS-WOFF >= WS-CONTENT-LEN
        COMPUTE WS-WLEN = WS-CONTENT-LEN - WS-WOFF
        IF WS-WLEN > 1048576
            MOVE 1048576 TO WS-WLEN
        END-IF
        CALL "write" USING
            BY VALUE WS-FD
            BY REFERENCE WS-BUF (WS-CONTENT-START + WS-WOFF : WS-WLEN)
            BY VALUE WS-WLEN
            RETURNING WS-READ-N
        END-CALL
        IF WS-READ-N <= 0
            CALL "close" USING BY VALUE WS-FD RETURNING WS-RC
            MOVE "91" TO L-RET
            EXIT PARAGRAPH
        END-IF
        ADD WS-READ-N TO WS-WOFF
    END-PERFORM
    CALL "close" USING BY VALUE WS-FD RETURNING WS-RC
    MOVE "Y" TO HX-UP-PRESENT
    MOVE WS-STAGE-PATH TO HX-UP-PATH
    MOVE WS-PART-FILE TO HX-UP-FILENAME
    MOVE WS-PART-MIME TO HX-UP-MIME
    MOVE WS-CONTENT-LEN TO HX-UP-SIZE
    PERFORM COMPUTE-SHA256.

COMPUTE-SHA256.
    MOVE SPACES TO WS-SHA-PATH
    STRING FUNCTION TRIM (WS-STAGE-PATH) ".sha" DELIMITED BY SIZE
        INTO WS-SHA-PATH
    END-STRING
    MOVE SPACES TO WS-CMD
    STRING "sha256sum '" FUNCTION TRIM (WS-STAGE-PATH) "' > '"
           FUNCTION TRIM (WS-SHA-PATH) "' 2>/dev/null"
        DELIMITED BY SIZE INTO WS-CMD
    END-STRING
    CALL "SYSTEM" USING WS-CMD RETURNING WS-RC
    MOVE SPACES TO HX-UP-SHA256
    IF WS-RC = 0
        OPEN INPUT SHA-FILE
        IF WS-SFS = "00"
            READ SHA-FILE
            IF WS-SFS = "00"
                MOVE SHA-LINE (1 : 64) TO HX-UP-SHA256
            END-IF
            CLOSE SHA-FILE
        END-IF
    END-IF
    MOVE SPACES TO WS-CMD
    STRING "rm -f '" FUNCTION TRIM (WS-SHA-PATH) "'"
        DELIMITED BY SIZE INTO WS-CMD
    END-STRING
    CALL "SYSTEM" USING WS-CMD RETURNING WS-RC.

STORE-FORM-FIELD.
    IF HX-FF-COUNT < 8 AND WS-PART-NAME NOT = SPACES
        ADD 1 TO HX-FF-COUNT
        MOVE WS-PART-NAME TO HX-FF-NAME (HX-FF-COUNT)
        MOVE SPACES TO HX-FF-VALUE (HX-FF-COUNT)
        IF WS-CONTENT-LEN > 0 AND WS-CONTENT-LEN <= 200
            MOVE WS-BUF (WS-CONTENT-START : WS-CONTENT-LEN)
                TO HX-FF-VALUE (HX-FF-COUNT)
        END-IF
    END-IF.
END PROGRAM "00MPARC0".
