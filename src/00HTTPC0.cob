>>SOURCE FORMAT FREE
*> 00HTTPC0 — platform: the generic CGI/HTTP layer and API dispatcher.
*> The ONLY program that touches the CGI contract: environment
*> variables, the request body on stdin and the response on stdout.
*> Routes /api/v1/... (Apache ScriptAlias /api -> PATH_INFO "/v1/...")
*> to business boundary programs via the HTTP-EXCHANGE copybook and
*> emits "Status:"/"Content-Type:" headers plus the JSON body via
*> write(2) — a boundary may set HX-EMITTED and stream the response
*> itself (file downloads). Multipart uploads are parsed by 00MPARC0
*> before the boundary runs; JSON bodies are read from fd 0 directly
*> (mod_cgid hands the CGI a unix socket as stdin).
*> Identity: DMS_SECURITY_MODE=dev trusts the X-Dev-User header
*> (HTTP_X_DEV_USER); any other mode reads what Apache's auth layer
*> exports (OIDC_CLAIM_email from mod_auth_openidc, else REMOTE_USER).
IDENTIFICATION DIVISION.
PROGRAM-ID. "00HTTPC0".
DATA DIVISION.
WORKING-STORAGE SECTION.
COPY "00HTTPR0.cpy".
01 WS-DUMMY             PIC X(8).
01 WS-MODE              PIC X(10).
01 WS-CTYPE             PIC X(300).
01 WS-CLEN-X            PIC X(16).
01 WS-CLEN              PIC 9(10) COMP.
01 WS-MAXUP-X           PIC X(16).
01 WS-MAXUP             PIC 9(10) COMP.
01 WS-TOTAL             PIC 9(8) COMP.
01 WS-REMAIN            USAGE BINARY-C-LONG.
01 WS-READ-N            USAGE BINARY-C-LONG.
01 WS-RET               PIC X(2).
01 WS-OP                PIC X(4).
COPY "20USRSR0.cpy" REPLACING ==:PFX:== BY ==WS-US==.
01 WS-QS                PIC X(512).
01 WS-QPAIR             PIC X(128) OCCURS 8.
01 WS-QI                PIC 9(2) COMP.
01 WS-RAW-NAME          PIC X(64).
01 WS-RAW-VALUE         PIC X(64).
01 WS-OUT               PIC X(263000).
01 WS-PTR               PIC 9(6) COMP.
01 WS-BODYLEN           PIC 9(6) COMP.
01 WS-OUTLEN            USAGE BINARY-C-LONG.
01 WS-LF                PIC X VALUE X"0A".
01 WS-CPATH             PIC X(520).
01 WS-RC                USAGE BINARY-LONG.
PROCEDURE DIVISION.
MAIN.
    INITIALIZE HTTP-EXCHANGE
    ACCEPT HX-METHOD FROM ENVIRONMENT "REQUEST_METHOD"
        ON EXCEPTION MOVE SPACES TO HX-METHOD
    END-ACCEPT
    ACCEPT HX-PATH FROM ENVIRONMENT "PATH_INFO"
        ON EXCEPTION MOVE SPACES TO HX-PATH
    END-ACCEPT
    UNSTRING HX-PATH DELIMITED BY "/"
        INTO WS-DUMMY HX-SEG (1) HX-SEG (2) HX-SEG (3)
             HX-SEG (4) HX-SEG (5) HX-SEG (6)
    END-UNSTRING
    PERFORM PARSE-QUERY-STRING

    IF HX-SEG (1) NOT = "v1"
        PERFORM NOT-FOUND
        PERFORM EMIT
        STOP RUN
    END-IF

    *> health is unauthenticated (readiness probe): storage reachable?
    IF HX-METHOD = "GET" AND HX-SEG (2) = "health"
        PERFORM DO-HEALTH
        PERFORM EMIT
        STOP RUN
    END-IF

    PERFORM READ-REQUEST-BODY
    IF HX-STATUS NOT = 0
        PERFORM EMIT
        STOP RUN
    END-IF

    PERFORM RESOLVE-IDENTITY
    IF HX-STATUS NOT = 0
        PERFORM EMIT
        STOP RUN
    END-IF

    EVALUATE TRUE
        WHEN HX-SEG (2) = "orgs" AND HX-SEG (4) = "members"
            CALL "20MEMBB0" USING HTTP-EXCHANGE
        WHEN HX-SEG (2) = "orgs"
            CALL "20ORGSB0" USING HTTP-EXCHANGE
        WHEN HX-SEG (2) = "users" AND HX-SEG (3) = "me"
                AND HX-SEG (4) = SPACES
            CALL "20USRSB0" USING HTTP-EXCHANGE
        WHEN HX-SEG (2) = "documents" AND HX-SEG (4) = "metadata"
            CALL "30METAB0" USING HTTP-EXCHANGE
        WHEN HX-SEG (2) = "documents"
            CALL "30DOCSB0" USING HTTP-EXCHANGE
        WHEN HX-SEG (2) = "akten"
            CALL "30AKTEB0" USING HTTP-EXCHANGE
        WHEN HX-SEG (2) = "document-classes"
            CALL "30CLASB0" USING HTTP-EXCHANGE
        WHEN HX-SEG (2) = "config" AND HX-SEG (3) = SPACES
            CALL "30CONFB0" USING HTTP-EXCHANGE
        WHEN OTHER
            PERFORM NOT-FOUND
    END-EVALUATE
    PERFORM CLEANUP-STAGED
    PERFORM EMIT
    STOP RUN.

PARSE-QUERY-STRING.
    MOVE 0 TO HX-QP-COUNT
    ACCEPT WS-QS FROM ENVIRONMENT "QUERY_STRING"
        ON EXCEPTION MOVE SPACES TO WS-QS
    END-ACCEPT
    IF WS-QS = SPACES
        EXIT PARAGRAPH
    END-IF
    INITIALIZE WS-QPAIR (1) WS-QPAIR (2) WS-QPAIR (3) WS-QPAIR (4)
               WS-QPAIR (5) WS-QPAIR (6) WS-QPAIR (7) WS-QPAIR (8)
    UNSTRING WS-QS DELIMITED BY "&"
        INTO WS-QPAIR (1) WS-QPAIR (2) WS-QPAIR (3) WS-QPAIR (4)
             WS-QPAIR (5) WS-QPAIR (6) WS-QPAIR (7) WS-QPAIR (8)
    END-UNSTRING
    PERFORM VARYING WS-QI FROM 1 BY 1 UNTIL WS-QI > 8
        IF WS-QPAIR (WS-QI) NOT = SPACES
            MOVE SPACES TO WS-RAW-NAME WS-RAW-VALUE
            UNSTRING WS-QPAIR (WS-QI) DELIMITED BY "="
                INTO WS-RAW-NAME WS-RAW-VALUE
            END-UNSTRING
            IF HX-QP-COUNT < 8
                ADD 1 TO HX-QP-COUNT
                MOVE WS-RAW-NAME TO HX-QP-NAME (HX-QP-COUNT)
                *> '+' means space in query encoding; %XX left as-is
                *> (page/size/type values never need it)
                INSPECT WS-RAW-VALUE REPLACING ALL "+" BY " "
                MOVE WS-RAW-VALUE TO HX-QP-VALUE (HX-QP-COUNT)
            END-IF
        END-IF
    END-PERFORM.

DO-HEALTH.
    MOVE SPACES TO WS-US-REC
    MOVE "00000000-0000-0000-0000-000000000000" TO WS-US-ID
    MOVE "GET " TO WS-OP
    CALL "20USRSE0" USING WS-OP WS-RET WS-US-REC
    IF WS-RET = "00" OR WS-RET = "23"
        MOVE 200 TO HX-STATUS
        MOVE '{"status":"ok"}' TO HX-RESPONSE
    ELSE
        MOVE 503 TO HX-STATUS
        MOVE '{"status":"down","error":"storage unavailable"}'
            TO HX-RESPONSE
    END-IF.

READ-REQUEST-BODY.
    IF HX-METHOD NOT = "POST" AND HX-METHOD NOT = "PUT"
        EXIT PARAGRAPH
    END-IF
    ACCEPT WS-CTYPE FROM ENVIRONMENT "CONTENT_TYPE"
        ON EXCEPTION MOVE SPACES TO WS-CTYPE
    END-ACCEPT
    IF WS-CTYPE (1 : 19) = "multipart/form-data"
        PERFORM READ-MULTIPART
    ELSE
        PERFORM READ-JSON-BODY
    END-IF.

READ-MULTIPART.
    ACCEPT WS-MAXUP-X FROM ENVIRONMENT "DMS_UPLOAD_MAX_BYTES"
        ON EXCEPTION MOVE SPACES TO WS-MAXUP-X
    END-ACCEPT
    COMPUTE WS-MAXUP = FUNCTION NUMVAL (WS-MAXUP-X)
    IF WS-MAXUP = 0
        MOVE 104857600 TO WS-MAXUP
    END-IF
    ACCEPT WS-CLEN-X FROM ENVIRONMENT "CONTENT_LENGTH"
        ON EXCEPTION MOVE SPACES TO WS-CLEN-X
    END-ACCEPT
    COMPUTE WS-CLEN = FUNCTION NUMVAL (WS-CLEN-X)
    IF WS-CLEN > WS-MAXUP + 16384
        MOVE 413 TO HX-STATUS
        MOVE '{"error":"upload too large"}' TO HX-RESPONSE
        EXIT PARAGRAPH
    END-IF
    CALL "00MPARC0" USING HTTP-EXCHANGE WS-RET
    EVALUATE WS-RET
        WHEN "00"
            CONTINUE
        WHEN "13"
            MOVE 413 TO HX-STATUS
            MOVE '{"error":"upload too large"}' TO HX-RESPONSE
        WHEN "91"
            MOVE 503 TO HX-STATUS
            MOVE '{"error":"storage unavailable"}' TO HX-RESPONSE
        WHEN OTHER
            MOVE 400 TO HX-STATUS
            MOVE '{"error":"malformed multipart request"}'
                TO HX-RESPONSE
    END-EVALUATE.

READ-JSON-BODY.
    ACCEPT WS-CLEN-X FROM ENVIRONMENT "CONTENT_LENGTH"
        ON EXCEPTION MOVE SPACES TO WS-CLEN-X
    END-ACCEPT
    COMPUTE WS-CLEN = FUNCTION NUMVAL (WS-CLEN-X)
    IF WS-CLEN = 0
        EXIT PARAGRAPH
    END-IF
    IF WS-CLEN > 4096
        MOVE 413 TO HX-STATUS
        MOVE '{"error":"upload too large"}' TO HX-RESPONSE
        EXIT PARAGRAPH
    END-IF
    *> read(2) on fd 0: works for both mod_cgi pipes and the unix
    *> socket mod_cgid hands the CGI process as stdin
    MOVE 0 TO WS-TOTAL
    PERFORM UNTIL WS-TOTAL >= WS-CLEN
        COMPUTE WS-REMAIN = WS-CLEN - WS-TOTAL
        CALL "read" USING
            BY VALUE 0
            BY REFERENCE HX-BODY (WS-TOTAL + 1 : WS-CLEN - WS-TOTAL)
            BY VALUE WS-REMAIN
            RETURNING WS-READ-N
        END-CALL
        IF WS-READ-N <= 0
            EXIT PERFORM
        END-IF
        ADD WS-READ-N TO WS-TOTAL
    END-PERFORM
    *> flatten control characters so the JSON scanner sees one line
    INSPECT HX-BODY CONVERTING X"090A0D" TO "   ".

RESOLVE-IDENTITY.
    ACCEPT WS-MODE FROM ENVIRONMENT "DMS_SECURITY_MODE"
        ON EXCEPTION MOVE SPACES TO WS-MODE
    END-ACCEPT
    IF WS-MODE = "dev"
        ACCEPT HX-USER-EMAIL FROM ENVIRONMENT "HTTP_X_DEV_USER"
            ON EXCEPTION MOVE SPACES TO HX-USER-EMAIL
        END-ACCEPT
    ELSE
        ACCEPT HX-USER-EMAIL FROM ENVIRONMENT "OIDC_CLAIM_email"
            ON EXCEPTION MOVE SPACES TO HX-USER-EMAIL
        END-ACCEPT
        IF HX-USER-EMAIL = SPACES
            ACCEPT HX-USER-EMAIL FROM ENVIRONMENT "REMOTE_USER"
                ON EXCEPTION MOVE SPACES TO HX-USER-EMAIL
            END-ACCEPT
        END-IF
    END-IF
    CALL "10USERC0" USING HTTP-EXCHANGE WS-RET
    EVALUATE WS-RET
        WHEN "00"
            CONTINUE
        WHEN "41"
            MOVE 401 TO HX-STATUS
            MOVE '{"error":"unauthorized"}' TO HX-RESPONSE
        WHEN OTHER
            MOVE 503 TO HX-STATUS
            MOVE '{"error":"storage unavailable"}' TO HX-RESPONSE
    END-EVALUATE.

CLEANUP-STAGED.
    *> whatever the boundary did not move into the store is garbage
    IF HX-UP-PRESENT = "Y" AND HX-UP-PATH NOT = SPACES
        MOVE SPACES TO WS-CPATH
        STRING FUNCTION TRIM (HX-UP-PATH) X"00" DELIMITED BY SIZE
            INTO WS-CPATH
        END-STRING
        CALL "unlink" USING WS-CPATH RETURNING WS-RC
    END-IF.

NOT-FOUND.
    MOVE 404 TO HX-STATUS
    MOVE '{"error":"not found"}' TO HX-RESPONSE.

EMIT.
    IF HX-EMITTED = "Y"
        EXIT PARAGRAPH
    END-IF
    IF HX-STATUS = 0
        MOVE 500 TO HX-STATUS
        MOVE '{"error":"internal error"}' TO HX-RESPONSE
    END-IF
    MOVE 1 TO WS-PTR
    STRING "Status: " HX-STATUS WS-LF
        DELIMITED BY SIZE INTO WS-OUT WITH POINTER WS-PTR
    END-STRING
    STRING "Content-Type: application/json" WS-LF WS-LF
        DELIMITED BY SIZE INTO WS-OUT WITH POINTER WS-PTR
    END-STRING
    IF HX-STATUS NOT = 204
        MOVE FUNCTION STORED-CHAR-LENGTH (HX-RESPONSE)
            TO WS-BODYLEN
        IF WS-BODYLEN > 0
            STRING HX-RESPONSE (1 : WS-BODYLEN) WS-LF
                DELIMITED BY SIZE INTO WS-OUT WITH POINTER WS-PTR
            END-STRING
        END-IF
    END-IF
    COMPUTE WS-OUTLEN = WS-PTR - 1
    CALL "write" USING
        BY VALUE 1
        BY REFERENCE WS-OUT (1 : WS-PTR - 1)
        BY VALUE WS-OUTLEN
        RETURNING WS-READ-N
    END-CALL.
END PROGRAM "00HTTPC0".
