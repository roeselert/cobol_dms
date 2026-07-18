>>SOURCE FORMAT FREE
*> 60CURLC0 — aiextraction: libcurl transport (rule 6). COBOL CALLs the
*> libcurl easy interface as a C library — curl_easy_init / _setopt /
*> _perform / _getinfo / _cleanup — to POST the OpenAI-compatible
*> chat-completions request. No shelling out to the curl binary.
*> The response body is captured with libcurl's built-in file writer
*> (CURLOPT_WRITEDATA = a FILE* from fopen, default fwrite callback),
*> then read back into the caller's buffer — so no C write-callback is
*> needed. TLS/CA and any corporate proxy are left to libcurl's system
*> defaults (the migration target speaks straight to the provider).
*> Linkage:
*>   L-URL    base URL (…/v1); "/chat/completions" is appended
*>   L-TOKEN  bearer token (Authorization header)
*>   L-BODY / L-BODYLEN   the JSON request body + its length
*>   L-RESP / L-RESPLEN   the response body + its length (out)
*>   L-STATUS HTTP status code (out)
*>   L-RET    00 ok(2xx) · 91 transport error · 92 non-2xx
IDENTIFICATION DIVISION.
PROGRAM-ID. "60CURLC0".
DATA DIVISION.
WORKING-STORAGE SECTION.
*> libcurl option / info constants (ABI-stable, from curl/curl.h)
78 C-OPT-URL            VALUE 10002.
78 C-OPT-WRITEDATA      VALUE 10001.
78 C-OPT-POSTFIELDS     VALUE 10015.
78 C-OPT-POSTFIELDSIZE  VALUE 60.
78 C-OPT-HTTPHEADER     VALUE 10023.
78 C-OPT-TIMEOUT        VALUE 13.
78 C-OPT-CONNECTTIMEOUT VALUE 78.
78 C-OPT-USERAGENT      VALUE 10018.
78 C-OPT-NOSIGNAL       VALUE 99.
78 C-INFO-RESPONSE-CODE VALUE 2097154.
01 WS-HANDLE            USAGE POINTER.
01 WS-SLIST             USAGE POINTER.
01 WS-NULLPTR           USAGE POINTER.
01 WS-FP                USAGE POINTER.
01 WS-CODE              USAGE BINARY-LONG.
01 WS-HTTP              USAGE BINARY-C-LONG.
01 WS-NREAD             USAGE BINARY-C-LONG.
01 WS-CAP               USAGE BINARY-C-LONG.
01 WS-BLEN              USAGE BINARY-C-LONG.
01 WS-OPT               USAGE BINARY-LONG.
01 WS-TMO               USAGE BINARY-C-LONG.
01 WS-CTMO              USAGE BINARY-C-LONG.
01 WS-ON               USAGE BINARY-C-LONG VALUE 1.
*> null-terminated C strings
01 WS-URL-Z             PIC X(560).
01 WS-HDR-AUTH          PIC X(240).
01 WS-HDR-CT            PIC X(40).
01 WS-UA                PIC X(24).
01 WS-MODE-WB           PIC X(3).
01 WS-MODE-RB           PIC X(3).
01 WS-PATH-Z            PIC X(560).
01 WS-DATADIR           PIC X(200).
01 WS-PID               USAGE BINARY-LONG.
01 WS-PID-Z             PIC 9(9).
01 WS-CMD               PIC X(600).
01 WS-RC                USAGE BINARY-LONG.
LINKAGE SECTION.
01 L-URL                PIC X(512).
01 L-TOKEN              PIC X(200).
01 L-BODY               PIC X(300000).
01 L-BODYLEN            PIC 9(9).
01 L-RESP               PIC X(1048576).
01 L-RESPLEN            PIC 9(9).
01 L-STATUS             PIC 9(3).
01 L-RET                PIC X(2).
PROCEDURE DIVISION USING L-URL L-TOKEN L-BODY L-BODYLEN
                        L-RESP L-RESPLEN L-STATUS L-RET.
MAIN.
    MOVE "00" TO L-RET
    MOVE 0 TO L-RESPLEN
    MOVE 0 TO L-STATUS
    MOVE SPACES TO L-RESP
    SET WS-NULLPTR TO NULL
    PERFORM BUILD-STRINGS
    CALL "curl_easy_init" RETURNING WS-HANDLE
    IF WS-HANDLE = NULL
        MOVE "91" TO L-RET
        GOBACK
    END-IF
    PERFORM OPEN-RESP-FILE
    IF WS-FP = NULL
        CALL "curl_easy_cleanup" USING BY VALUE WS-HANDLE
        MOVE "91" TO L-RET
        GOBACK
    END-IF
    PERFORM SET-OPTIONS
    CALL "curl_easy_perform" USING BY VALUE WS-HANDLE
        RETURNING WS-CODE
    CALL "fclose" USING BY VALUE WS-FP
    IF WS-CODE NOT = 0
        MOVE "91" TO L-RET
    ELSE
        MOVE C-INFO-RESPONSE-CODE TO WS-OPT
        CALL "curl_easy_getinfo" USING BY VALUE WS-HANDLE
            BY VALUE WS-OPT BY REFERENCE WS-HTTP
        MOVE WS-HTTP TO L-STATUS
        PERFORM READ-RESP-FILE
        IF L-STATUS < 200 OR L-STATUS > 299
            MOVE "92" TO L-RET
        END-IF
    END-IF
    CALL "curl_slist_free_all" USING BY VALUE WS-SLIST
    CALL "curl_easy_cleanup" USING BY VALUE WS-HANDLE
    PERFORM DELETE-RESP-FILE
    GOBACK.

BUILD-STRINGS.
    *> URL = <base>/chat/completions + NUL
    MOVE SPACES TO WS-URL-Z
    STRING FUNCTION TRIM (L-URL) "/chat/completions" X"00"
        DELIMITED BY SIZE INTO WS-URL-Z
    END-STRING
    MOVE SPACES TO WS-HDR-AUTH
    STRING "Authorization: Bearer " FUNCTION TRIM (L-TOKEN) X"00"
        DELIMITED BY SIZE INTO WS-HDR-AUTH
    END-STRING
    MOVE SPACES TO WS-HDR-CT
    STRING "Content-Type: application/json" X"00"
        DELIMITED BY SIZE INTO WS-HDR-CT
    END-STRING
    MOVE SPACES TO WS-UA
    STRING "dms-cobol/1.0" X"00" DELIMITED BY SIZE INTO WS-UA
    END-STRING
    MOVE SPACES TO WS-MODE-WB
    STRING "wb" X"00" DELIMITED BY SIZE INTO WS-MODE-WB
    END-STRING
    MOVE SPACES TO WS-MODE-RB
    STRING "rb" X"00" DELIMITED BY SIZE INTO WS-MODE-RB
    END-STRING
    MOVE L-BODYLEN TO WS-BLEN.

OPEN-RESP-FILE.
    *> response captured to a scratch file under objects/tmp
    ACCEPT WS-DATADIR FROM ENVIRONMENT "DMS_DATA_DIR"
        ON EXCEPTION MOVE SPACES TO WS-DATADIR
    END-ACCEPT
    IF WS-DATADIR = SPACES
        MOVE "/data" TO WS-DATADIR
    END-IF
    CALL "getpid" RETURNING WS-PID
    MOVE WS-PID TO WS-PID-Z
    MOVE SPACES TO WS-PATH-Z
    STRING FUNCTION TRIM (WS-DATADIR) "/objects/tmp/curl-"
           WS-PID-Z ".resp" X"00"
        DELIMITED BY SIZE INTO WS-PATH-Z
    END-STRING
    CALL "fopen" USING BY REFERENCE WS-PATH-Z
        BY REFERENCE WS-MODE-WB RETURNING WS-FP.

SET-OPTIONS.
    MOVE C-OPT-URL TO WS-OPT
    CALL "curl_easy_setopt" USING BY VALUE WS-HANDLE
        BY VALUE WS-OPT BY REFERENCE WS-URL-Z
    MOVE C-OPT-POSTFIELDSIZE TO WS-OPT
    CALL "curl_easy_setopt" USING BY VALUE WS-HANDLE
        BY VALUE WS-OPT BY VALUE WS-BLEN
    MOVE C-OPT-POSTFIELDS TO WS-OPT
    CALL "curl_easy_setopt" USING BY VALUE WS-HANDLE
        BY VALUE WS-OPT BY REFERENCE L-BODY
    *> header list: Authorization + Content-Type
    CALL "curl_slist_append" USING BY VALUE WS-NULLPTR
        BY REFERENCE WS-HDR-AUTH RETURNING WS-SLIST
    CALL "curl_slist_append" USING BY VALUE WS-SLIST
        BY REFERENCE WS-HDR-CT RETURNING WS-SLIST
    MOVE C-OPT-HTTPHEADER TO WS-OPT
    CALL "curl_easy_setopt" USING BY VALUE WS-HANDLE
        BY VALUE WS-OPT BY VALUE WS-SLIST
    MOVE C-OPT-WRITEDATA TO WS-OPT
    CALL "curl_easy_setopt" USING BY VALUE WS-HANDLE
        BY VALUE WS-OPT BY VALUE WS-FP
    MOVE C-OPT-USERAGENT TO WS-OPT
    CALL "curl_easy_setopt" USING BY VALUE WS-HANDLE
        BY VALUE WS-OPT BY REFERENCE WS-UA
    *> timeouts + no async-DNS signal (safe in a worker process)
    MOVE 120 TO WS-TMO
    MOVE C-OPT-TIMEOUT TO WS-OPT
    CALL "curl_easy_setopt" USING BY VALUE WS-HANDLE
        BY VALUE WS-OPT BY VALUE WS-TMO
    MOVE 10 TO WS-CTMO
    MOVE C-OPT-CONNECTTIMEOUT TO WS-OPT
    CALL "curl_easy_setopt" USING BY VALUE WS-HANDLE
        BY VALUE WS-OPT BY VALUE WS-CTMO
    MOVE C-OPT-NOSIGNAL TO WS-OPT
    CALL "curl_easy_setopt" USING BY VALUE WS-HANDLE
        BY VALUE WS-OPT BY VALUE WS-ON.

READ-RESP-FILE.
    CALL "fopen" USING BY REFERENCE WS-PATH-Z
        BY REFERENCE WS-MODE-RB RETURNING WS-FP
    IF WS-FP = NULL
        EXIT PARAGRAPH
    END-IF
    MOVE 1048576 TO WS-CAP
    CALL "fread" USING BY REFERENCE L-RESP
        BY VALUE 1 BY VALUE WS-CAP BY VALUE WS-FP
        RETURNING WS-NREAD
    CALL "fclose" USING BY VALUE WS-FP
    IF WS-NREAD < 0
        MOVE 0 TO L-RESPLEN
    ELSE
        MOVE WS-NREAD TO L-RESPLEN
    END-IF.

DELETE-RESP-FILE.
    *> WS-PATH-Z is already NUL-terminated for the C call
    CALL "unlink" USING BY REFERENCE WS-PATH-Z RETURNING WS-RC.
END PROGRAM "60CURLC0".
