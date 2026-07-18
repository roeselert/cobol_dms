>>SOURCE FORMAT FREE
*> 50JOBSW0 — conversion: the in-process ingest worker daemon (§7.5).
*> A long-running GnuCOBOL process that replaces the Java Spring
*> scheduler + JobDispatcher: it polls the durable CONVJOB queue,
*> drives OCR-only conversion (50CONVC0) per claimed job, and applies
*> the same claim/lease/retry/backoff with a terminal FAILED after N
*> attempts (R-1/R-2). One poll round:
*>   1. SWEP — re-queue jobs whose lease expired (crashed worker)
*>   2. up to BATCH times: CLAM the next due job, run conversion,
*>      DONE on success else retry-with-backoff / terminal FAIL
*>   3. touch a heartbeat file, then nanosleep POLL milliseconds
*> Config via environment (mirrors application.yml dms.worker.*):
*>   DMS_WORKER_ENABLED (default true) · DMS_WORKER_POLL_MILLIS 2000 ·
*>   DMS_WORKER_BATCH_SIZE 2 · DMS_WORKER_MAX_ATTEMPTS 5 ·
*>   DMS_WORKER_BACKOFF_BASE_MILLIS 5000 · DMS_WORKER_LEASE_SECONDS 300.
IDENTIFICATION DIVISION.
PROGRAM-ID. "50JOBSW0".
DATA DIVISION.
WORKING-STORAGE SECTION.
01 WS-OP                PIC X(4).
01 WS-RET               PIC X(2).
01 WS-CONV-RET          PIC X(2).
01 WS-UUID              PIC X(36).
01 WS-NOW               PIC 9(13).
COPY "50JOBSR0.cpy" REPLACING ==:PFX:== BY ==WS-JB==.
COPY "50JCTLR0.cpy" REPLACING ==:PFX:== BY ==WS-JB==.
COPY "30STATR0.cpy" REPLACING ==:PFX:== BY ==WS-DS==.
*> config
01 WS-ENABLED           PIC X(8).
01 WS-ENV-X             PIC X(16).
01 WS-POLL-MS           PIC 9(9).
01 WS-BATCH             PIC 9(4).
01 WS-MAX-ATTEMPTS      PIC 9(4).
01 WS-BACKOFF-BASE      PIC 9(13).
01 WS-LEASE-SECONDS     PIC 9(9).
*> loop state
01 WS-I                 PIC 9(4) COMP.
01 WS-SHIFT             PIC 9(4) COMP.
01 WS-BACKOFF           PIC 9(18).
01 WS-NANOS             PIC 9(18) COMP-5.
01 WS-CLAIMED           PIC X VALUE "N".
*> heartbeat
01 WS-HB-BASE           PIC X(200).
01 WS-CMD               PIC X(700).
01 WS-RC                USAGE BINARY-LONG.
PROCEDURE DIVISION.
MAIN.
    PERFORM LOAD-CONFIG
    IF FUNCTION LOWER-CASE (FUNCTION TRIM (WS-ENABLED)) = "false"
        DISPLAY "50JOBSW0: worker disabled (DMS_WORKER_ENABLED=false)"
        GOBACK
    END-IF
    ACCEPT WS-HB-BASE FROM ENVIRONMENT "DMS_DATA_DIR"
        ON EXCEPTION MOVE SPACES TO WS-HB-BASE
    END-ACCEPT
    IF WS-HB-BASE = SPACES
        MOVE "/data" TO WS-HB-BASE
    END-IF
    DISPLAY "50JOBSW0: ingest worker started"
    PERFORM FOREVER
        PERFORM POLL-ROUND
        PERFORM SLEEP-POLL
    END-PERFORM
    GOBACK.

LOAD-CONFIG.
    MOVE "true" TO WS-ENABLED
    ACCEPT WS-ENABLED FROM ENVIRONMENT "DMS_WORKER_ENABLED"
        ON EXCEPTION MOVE "true" TO WS-ENABLED
    END-ACCEPT
    MOVE SPACES TO WS-ENV-X
    ACCEPT WS-ENV-X FROM ENVIRONMENT "DMS_WORKER_POLL_MILLIS"
        ON EXCEPTION MOVE SPACES TO WS-ENV-X
    END-ACCEPT
    COMPUTE WS-POLL-MS = FUNCTION NUMVAL (WS-ENV-X)
    IF WS-POLL-MS = 0
        MOVE 2000 TO WS-POLL-MS
    END-IF
    MOVE SPACES TO WS-ENV-X
    ACCEPT WS-ENV-X FROM ENVIRONMENT "DMS_WORKER_BATCH_SIZE"
        ON EXCEPTION MOVE SPACES TO WS-ENV-X
    END-ACCEPT
    COMPUTE WS-BATCH = FUNCTION NUMVAL (WS-ENV-X)
    IF WS-BATCH = 0
        MOVE 2 TO WS-BATCH
    END-IF
    MOVE SPACES TO WS-ENV-X
    ACCEPT WS-ENV-X FROM ENVIRONMENT "DMS_WORKER_MAX_ATTEMPTS"
        ON EXCEPTION MOVE SPACES TO WS-ENV-X
    END-ACCEPT
    COMPUTE WS-MAX-ATTEMPTS = FUNCTION NUMVAL (WS-ENV-X)
    IF WS-MAX-ATTEMPTS = 0
        MOVE 5 TO WS-MAX-ATTEMPTS
    END-IF
    MOVE SPACES TO WS-ENV-X
    ACCEPT WS-ENV-X FROM ENVIRONMENT "DMS_WORKER_BACKOFF_BASE_MILLIS"
        ON EXCEPTION MOVE SPACES TO WS-ENV-X
    END-ACCEPT
    COMPUTE WS-BACKOFF-BASE = FUNCTION NUMVAL (WS-ENV-X)
    IF WS-BACKOFF-BASE = 0
        MOVE 5000 TO WS-BACKOFF-BASE
    END-IF
    MOVE SPACES TO WS-ENV-X
    ACCEPT WS-ENV-X FROM ENVIRONMENT "DMS_WORKER_LEASE_SECONDS"
        ON EXCEPTION MOVE SPACES TO WS-ENV-X
    END-ACCEPT
    COMPUTE WS-LEASE-SECONDS = FUNCTION NUMVAL (WS-ENV-X)
    IF WS-LEASE-SECONDS = 0
        MOVE 300 TO WS-LEASE-SECONDS
    END-IF.

*> --- one poll round -------------------------------------------------
POLL-ROUND.
    PERFORM SWEEP-LEASES
    MOVE 1 TO WS-I
    PERFORM UNTIL WS-I > WS-BATCH
        PERFORM CLAIM-ONE
        IF WS-CLAIMED = "N"
            EXIT PERFORM
        END-IF
        PERFORM PROCESS-JOB
        ADD 1 TO WS-I
    END-PERFORM
    PERFORM HEARTBEAT.

SWEEP-LEASES.
    PERFORM NOW-MS
    MOVE WS-NOW TO WS-JB-CT-NOW
    MOVE "SWEP" TO WS-OP
    CALL "50JOBSE0" USING WS-OP WS-RET WS-JB-REC WS-JB-CTL
    IF WS-RET = "00" AND WS-JB-CT-COUNT > 0
        DISPLAY "50JOBSW0: re-queued " WS-JB-CT-COUNT
                " job(s) with expired leases"
    END-IF.

CLAIM-ONE.
    MOVE "N" TO WS-CLAIMED
    PERFORM NOW-MS
    MOVE WS-NOW TO WS-JB-CT-NOW
    MOVE WS-LEASE-SECONDS TO WS-JB-CT-LEASE
    MOVE SPACES TO WS-JB-REC
    MOVE "CLAM" TO WS-OP
    CALL "50JOBSE0" USING WS-OP WS-RET WS-JB-REC WS-JB-CTL
    IF WS-RET = "00"
        MOVE "Y" TO WS-CLAIMED
    END-IF.

PROCESS-JOB.
    *> WS-JB-REC now holds the claimed (RUNNING) job. Convert; on any
    *> non-"00" return retry with backoff or fail terminally.
    CALL "50CONVC0" USING WS-JB-DOC WS-CONV-RET
    IF WS-CONV-RET = "00"
        MOVE "DONE" TO WS-OP
        CALL "50JOBSE0" USING WS-OP WS-RET WS-JB-REC WS-JB-CTL
        DISPLAY "50JOBSW0: document " WS-JB-DOC
                " READY (attempt " WS-JB-ATTEMPTS ")"
    ELSE
        PERFORM RETRY-OR-FAIL
    END-IF.

RETRY-OR-FAIL.
    MOVE SPACES TO WS-JB-ERROR
    STRING "conversion failed (ret=" WS-CONV-RET ")"
        DELIMITED BY SIZE INTO WS-JB-ERROR
    END-STRING
    IF WS-JB-ATTEMPTS >= WS-MAX-ATTEMPTS
        *> terminal FAILED (R-1): fail the job and the document
        MOVE "FAIL" TO WS-OP
        CALL "50JOBSE0" USING WS-OP WS-RET WS-JB-REC WS-JB-CTL
        PERFORM MARK-DOC-FAILED
        DISPLAY "50JOBSW0: document " WS-JB-DOC
                " FAILED after " WS-JB-ATTEMPTS " attempt(s)"
    ELSE
        *> exponential backoff: base * 2^min(attempts,16)
        MOVE WS-JB-ATTEMPTS TO WS-SHIFT
        IF WS-SHIFT > 16
            MOVE 16 TO WS-SHIFT
        END-IF
        COMPUTE WS-BACKOFF = WS-BACKOFF-BASE * (2 ** WS-SHIFT)
        PERFORM NOW-MS
        COMPUTE WS-JB-AVAILABLE = WS-NOW + WS-BACKOFF
        MOVE "RQUE" TO WS-OP
        CALL "50JOBSE0" USING WS-OP WS-RET WS-JB-REC WS-JB-CTL
        DISPLAY "50JOBSW0: document " WS-JB-DOC
                " re-queued (attempt " WS-JB-ATTEMPTS ")"
    END-IF.

MARK-DOC-FAILED.
    MOVE SPACES TO WS-DS-REC
    MOVE WS-JB-DOC TO WS-DS-DOC
    MOVE "GET " TO WS-OP
    CALL "30STATE0" USING WS-OP WS-RET WS-DS-REC
    IF WS-RET = "00"
        PERFORM NOW-MS
        MOVE "FAILED" TO WS-DS-STATUS
        MOVE SPACES TO WS-DS-CHANGED-BY
        MOVE WS-NOW TO WS-DS-UPDATED
        MOVE "REW " TO WS-OP
        CALL "30STATE0" USING WS-OP WS-RET WS-DS-REC
    END-IF.

*> --- helpers --------------------------------------------------------
NOW-MS.
    CALL "00UUIDC0" USING WS-UUID WS-NOW.

SLEEP-POLL.
    COMPUTE WS-NANOS = WS-POLL-MS * 1000000
    CALL "CBL_OC_NANOSLEEP" USING WS-NANOS.

HEARTBEAT.
    *> a liveness marker under the data dir (readable by ops / probes)
    PERFORM NOW-MS
    MOVE SPACES TO WS-CMD
    STRING "echo " WS-NOW " > '"
           FUNCTION TRIM (WS-HB-BASE) "/worker.heartbeat' 2>/dev/null"
        DELIMITED BY SIZE INTO WS-CMD
    END-STRING
    CALL "SYSTEM" USING WS-CMD RETURNING WS-RC.
END PROGRAM "50JOBSW0".
