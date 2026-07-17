>>SOURCE FORMAT FREE
*> 00HTTPC0 — platform: the generic CGI/HTTP layer and API dispatcher.
*> The ONLY program that touches the CGI contract: environment
*> variables, the request body on stdin and the response on stdout.
*> Routes /api/v1/... (Apache ScriptAlias /api -> PATH_INFO "/v1/...")
*> to business boundary programs via the HTTP-EXCHANGE copybook and
*> emits "Status:"/"Content-Type:" headers plus the JSON body.
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
01 WS-CLEN-X            PIC X(12).
01 WS-CLEN              PIC 9(8) COMP.
01 WS-TOTAL             PIC 9(8) COMP.
01 WS-REMAIN            USAGE BINARY-C-LONG.
01 WS-READ-N            USAGE BINARY-C-LONG.
01 WS-RET               PIC X(2).
01 WS-OP                PIC X(4).
COPY "20USRSR0.cpy" REPLACING ==:PFX:== BY ==WS-US==.
01 WS-OUT               PIC X(263000).
01 WS-PTR               PIC 9(6) COMP.
01 WS-BODYLEN           PIC 9(6) COMP.
01 WS-LF                PIC X VALUE X"0A".
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

    PERFORM READ-BODY
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
        WHEN OTHER
            PERFORM NOT-FOUND
    END-EVALUATE
    PERFORM EMIT
    STOP RUN.

DO-HEALTH.
    *> probing the USERS file exercises libcob + the indexed-file
    *> backend + the data directory in one go
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

READ-BODY.
    IF HX-METHOD NOT = "POST" AND HX-METHOD NOT = "PUT"
        EXIT PARAGRAPH
    END-IF
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

NOT-FOUND.
    MOVE 404 TO HX-STATUS
    MOVE '{"error":"not found"}' TO HX-RESPONSE.

EMIT.
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
    COMPUTE WS-BODYLEN = WS-PTR - 1
    DISPLAY WS-OUT (1 : WS-BODYLEN) WITH NO ADVANCING.
END PROGRAM "00HTTPC0".
