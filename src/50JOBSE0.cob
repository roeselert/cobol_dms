>>SOURCE FORMAT FREE
*> 50JOBSE0 — conversion: file access for the CONVJOB indexed file
*> (durable job queue; one job per document). Iteration-3 ops: DGET by
*> unique document-id · WRT · RSET (reset the document's job for
*> reprocess: QUEUED, attempts 0, lease/error cleared, available now).
*> The worker's claim/lease/sweep ops arrive in iteration 4.
*> L-RET: 00 · 23 · 22 · 9x.
IDENTIFICATION DIVISION.
PROGRAM-ID. "50JOBSE0".
ENVIRONMENT DIVISION.
INPUT-OUTPUT SECTION.
FILE-CONTROL.
    SELECT JOB-FILE ASSIGN TO "CONVJOB"
        ORGANIZATION IS INDEXED
        ACCESS MODE IS DYNAMIC
        RECORD KEY IS JB-ID
        ALTERNATE RECORD KEY IS JB-DOC
        LOCK MODE IS AUTOMATIC
        FILE STATUS IS WS-FS.
DATA DIVISION.
FILE SECTION.
FD JOB-FILE.
COPY "50JOBSR0.cpy" REPLACING ==:PFX:== BY ==JB==.
WORKING-STORAGE SECTION.
01 WS-FS                PIC X(2).
LINKAGE SECTION.
01 L-OP                 PIC X(4).
01 L-RET                PIC X(2).
COPY "50JOBSR0.cpy" REPLACING ==:PFX:== BY ==L-JB==.
PROCEDURE DIVISION USING L-OP L-RET L-JB-REC.
MAIN.
    PERFORM OPEN-FILE
    IF L-RET NOT = "00"
        GOBACK
    END-IF
    EVALUATE L-OP
        WHEN "DGET" PERFORM DO-GET-BY-DOC
        WHEN "WRT " PERFORM DO-WRITE
        WHEN "RSET" PERFORM DO-RESET
        WHEN OTHER  MOVE "99" TO L-RET
    END-EVALUATE
    CLOSE JOB-FILE
    GOBACK.

OPEN-FILE.
    MOVE "00" TO L-RET
    OPEN I-O JOB-FILE
    IF WS-FS = "35"
        OPEN OUTPUT JOB-FILE
        CLOSE JOB-FILE
        OPEN I-O JOB-FILE
    END-IF
    IF WS-FS NOT = "00" AND WS-FS NOT = "05"
        MOVE "91" TO L-RET
    END-IF.

DO-GET-BY-DOC.
    MOVE L-JB-DOC TO JB-DOC
    READ JOB-FILE KEY IS JB-DOC
    IF WS-FS = "00" OR WS-FS = "02"
        MOVE JB-REC TO L-JB-REC
        MOVE "00" TO L-RET
    ELSE
        MOVE "23" TO L-RET
    END-IF.

DO-WRITE.
    MOVE L-JB-REC TO JB-REC
    WRITE JB-REC
    EVALUATE WS-FS
        WHEN "00" MOVE "00" TO L-RET
        WHEN "02" MOVE "00" TO L-RET
        WHEN "22" MOVE "22" TO L-RET
        WHEN OTHER MOVE "92" TO L-RET
    END-EVALUATE.

DO-RESET.
    *> mirrors ConversionJobRepository.resetForRetry
    MOVE L-JB-DOC TO JB-DOC
    READ JOB-FILE KEY IS JB-DOC
    IF WS-FS NOT = "00" AND WS-FS NOT = "02"
        MOVE "23" TO L-RET
    ELSE
        MOVE "QUEUED" TO JB-STATUS
        MOVE 0 TO JB-ATTEMPTS
        MOVE 0 TO JB-LEASE
        MOVE SPACES TO JB-ERROR
        MOVE L-JB-AVAILABLE TO JB-AVAILABLE
        REWRITE JB-REC
        IF WS-FS = "00" OR WS-FS = "02"
            MOVE JB-REC TO L-JB-REC
            MOVE "00" TO L-RET
        ELSE
            MOVE "92" TO L-RET
        END-IF
    END-IF.
END PROGRAM "50JOBSE0".
