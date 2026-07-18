>>SOURCE FORMAT FREE
*> 50JOBSE0 — conversion: file access for the CONVJOB indexed file
*> (durable job queue; one job per document). Ops:
*>   DGET by unique document-id · WRT · RSET (reprocess reset).
*>   Worker ops (iteration 4, single in-process consumer §7.5):
*>     CLAM find+claim the earliest eligible QUEUED job (RUNNING,
*>          attempts+1, lease = now + lease*1000) — atomic enough
*>          with one consumer (guarded re-read before REWRITE).
*>     SWEP re-queue jobs whose lease expired while RUNNING (R-2).
*>     DONE / FAIL / RQUE  finish, terminal-fail, or retry-with-backoff
*>          a claimed job by id.
*>     SCAN fill the control table with every job (jobs boundary).
*> The 4th arg (50JCTLR0) carries now/lease in and count/rows out.
*> L-RET: 00 · 23 not found/none-eligible · 22 · 9x.
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
01 WS-EOF               PIC X VALUE "N".
01 WS-FOUND             PIC X VALUE "N".
01 WS-BEST-ID           PIC X(36).
01 WS-BEST-CREATED      PIC 9(13).
01 WS-NEXP              PIC 9(4) COMP.
01 WS-K                 PIC 9(4) COMP.
01 WS-EXP-ID            PIC X(36) OCCURS 100.
LINKAGE SECTION.
01 L-OP                 PIC X(4).
01 L-RET                PIC X(2).
COPY "50JOBSR0.cpy" REPLACING ==:PFX:== BY ==L-JB==.
COPY "50JCTLR0.cpy" REPLACING ==:PFX:== BY ==L-JB==.
PROCEDURE DIVISION USING L-OP L-RET L-JB-REC L-JB-CTL.
MAIN.
    PERFORM OPEN-FILE
    IF L-RET NOT = "00"
        GOBACK
    END-IF
    EVALUATE L-OP
        WHEN "DGET" PERFORM DO-GET-BY-DOC
        WHEN "WRT " PERFORM DO-WRITE
        WHEN "RSET" PERFORM DO-RESET
        WHEN "CLAM" PERFORM DO-CLAIM
        WHEN "SWEP" PERFORM DO-SWEEP
        WHEN "DONE" PERFORM DO-DONE
        WHEN "FAIL" PERFORM DO-FAIL
        WHEN "RQUE" PERFORM DO-REQUEUE
        WHEN "SCAN" PERFORM DO-SCAN
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

DO-CLAIM.
    *> pass 1: earliest-created QUEUED job that is due (available<=now).
    *> mirrors findNextQueued ORDER BY created_at (§JobQueue.claim)
    MOVE "N" TO WS-FOUND
    MOVE 9999999999999 TO WS-BEST-CREATED
    PERFORM SCAN-START
    PERFORM UNTIL WS-EOF = "Y"
        READ JOB-FILE NEXT
        IF WS-FS NOT = "00" AND WS-FS NOT = "02"
            MOVE "Y" TO WS-EOF
        ELSE
            IF JB-STATUS = "QUEUED"
                    AND JB-AVAILABLE <= L-JB-CT-NOW
                    AND JB-CREATED < WS-BEST-CREATED
                MOVE JB-CREATED TO WS-BEST-CREATED
                MOVE JB-ID TO WS-BEST-ID
                MOVE "Y" TO WS-FOUND
            END-IF
        END-IF
    END-PERFORM
    IF WS-FOUND = "N"
        MOVE "23" TO L-RET
        EXIT PARAGRAPH
    END-IF
    *> pass 2: guarded claim of that row (still QUEUED?)
    MOVE WS-BEST-ID TO JB-ID
    READ JOB-FILE KEY IS JB-ID
    IF (WS-FS = "00" OR WS-FS = "02") AND JB-STATUS = "QUEUED"
        MOVE "RUNNING" TO JB-STATUS
        ADD 1 TO JB-ATTEMPTS
        COMPUTE JB-LEASE = L-JB-CT-NOW + L-JB-CT-LEASE * 1000
        REWRITE JB-REC
        IF WS-FS = "00" OR WS-FS = "02"
            MOVE JB-REC TO L-JB-REC
            MOVE "00" TO L-RET
        ELSE
            MOVE "92" TO L-RET
        END-IF
    ELSE
        MOVE "23" TO L-RET
    END-IF.

DO-SWEEP.
    *> mirrors requeueExpiredLeases (crashed worker recovery, R-2)
    MOVE 0 TO L-JB-CT-COUNT
    MOVE 0 TO WS-NEXP
    PERFORM SCAN-START
    PERFORM UNTIL WS-EOF = "Y"
        READ JOB-FILE NEXT
        IF WS-FS NOT = "00" AND WS-FS NOT = "02"
            MOVE "Y" TO WS-EOF
        ELSE
            IF JB-STATUS = "RUNNING" AND JB-LEASE > 0
                    AND JB-LEASE < L-JB-CT-NOW
                    AND WS-NEXP < 100
                ADD 1 TO WS-NEXP
                MOVE JB-ID TO WS-EXP-ID (WS-NEXP)
            END-IF
        END-IF
    END-PERFORM
    PERFORM VARYING WS-K FROM 1 BY 1 UNTIL WS-K > WS-NEXP
        MOVE WS-EXP-ID (WS-K) TO JB-ID
        READ JOB-FILE KEY IS JB-ID
        IF (WS-FS = "00" OR WS-FS = "02") AND JB-STATUS = "RUNNING"
            MOVE "QUEUED" TO JB-STATUS
            MOVE 0 TO JB-LEASE
            MOVE L-JB-CT-NOW TO JB-AVAILABLE
            REWRITE JB-REC
            IF WS-FS = "00" OR WS-FS = "02"
                ADD 1 TO L-JB-CT-COUNT
            END-IF
        END-IF
    END-PERFORM
    MOVE "00" TO L-RET.

DO-DONE.
    MOVE L-JB-ID TO JB-ID
    READ JOB-FILE KEY IS JB-ID
    IF WS-FS NOT = "00" AND WS-FS NOT = "02"
        MOVE "23" TO L-RET
    ELSE
        MOVE "DONE" TO JB-STATUS
        MOVE 0 TO JB-LEASE
        MOVE SPACES TO JB-ERROR
        REWRITE JB-REC
        PERFORM RET-FROM-FS
    END-IF.

DO-FAIL.
    MOVE L-JB-ID TO JB-ID
    READ JOB-FILE KEY IS JB-ID
    IF WS-FS NOT = "00" AND WS-FS NOT = "02"
        MOVE "23" TO L-RET
    ELSE
        MOVE "FAILED" TO JB-STATUS
        MOVE 0 TO JB-LEASE
        MOVE L-JB-ERROR TO JB-ERROR
        REWRITE JB-REC
        PERFORM RET-FROM-FS
    END-IF.

DO-REQUEUE.
    MOVE L-JB-ID TO JB-ID
    READ JOB-FILE KEY IS JB-ID
    IF WS-FS NOT = "00" AND WS-FS NOT = "02"
        MOVE "23" TO L-RET
    ELSE
        MOVE "QUEUED" TO JB-STATUS
        MOVE 0 TO JB-LEASE
        MOVE L-JB-ERROR TO JB-ERROR
        MOVE L-JB-AVAILABLE TO JB-AVAILABLE
        REWRITE JB-REC
        PERFORM RET-FROM-FS
    END-IF.

DO-SCAN.
    MOVE 0 TO L-JB-CT-COUNT
    PERFORM SCAN-START
    PERFORM UNTIL WS-EOF = "Y" OR L-JB-CT-COUNT >= 100
        READ JOB-FILE NEXT
        IF WS-FS NOT = "00" AND WS-FS NOT = "02"
            MOVE "Y" TO WS-EOF
        ELSE
            ADD 1 TO L-JB-CT-COUNT
            MOVE JB-ID       TO L-JB-CT-ID (L-JB-CT-COUNT)
            MOVE JB-DOC      TO L-JB-CT-DOC (L-JB-CT-COUNT)
            MOVE JB-STATUS   TO L-JB-CT-STATUS (L-JB-CT-COUNT)
            MOVE JB-ATTEMPTS TO L-JB-CT-ATTEMPTS (L-JB-CT-COUNT)
            MOVE JB-ERROR    TO L-JB-CT-ERROR (L-JB-CT-COUNT)
            MOVE JB-CREATED  TO L-JB-CT-CREATED (L-JB-CT-COUNT)
        END-IF
    END-PERFORM
    MOVE "00" TO L-RET.

SCAN-START.
    *> position at the first record by primary key for a full scan
    MOVE "N" TO WS-EOF
    MOVE LOW-VALUES TO JB-ID
    START JOB-FILE KEY IS >= JB-ID
    IF WS-FS NOT = "00"
        MOVE "Y" TO WS-EOF
    END-IF.

RET-FROM-FS.
    IF WS-FS = "00" OR WS-FS = "02"
        MOVE "00" TO L-RET
    ELSE
        MOVE "92" TO L-RET
    END-IF.
END PROGRAM "50JOBSE0".
