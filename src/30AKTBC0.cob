>>SOURCE FORMAT FREE
*> 30AKTBC0 — documents: Aktenbildung control, mirroring
*> Aktenbildung.java. Ops:
*>   FIND find-or-create the Akte for a filePlanReference (uniqueness
*>        is GLOBAL on the reference; the first creator's org unit
*>        wins; a lost create race re-reads — 200/201, never 500)
*>   REQG read by id (404 when missing)
IDENTIFICATION DIVISION.
PROGRAM-ID. "30AKTBC0".
DATA DIVISION.
WORKING-STORAGE SECTION.
01 WS-OP                PIC X(4).
01 WS-RET               PIC X(2).
01 WS-UUID              PIC X(36).
01 WS-EPOCH             PIC 9(13).
01 WS-AK-DUMMY.
   05 WS-AK-DUMMY-COUNT PIC 9(4).
   05 WS-AK-DUMMY-ROW OCCURS 300.
      10 FILLER         PIC X(36).
      10 FILLER         PIC X(200).
      10 FILLER         PIC X(36).
LINKAGE SECTION.
01 K-OP                 PIC X(4).
01 K-STATUS             PIC X(3).
01 K-FPR                PIC X(200).
01 K-ORG                PIC X(36).
COPY "30AKTER0.cpy" REPLACING ==:PFX:== BY ==K-AK==.
PROCEDURE DIVISION USING K-OP K-STATUS K-FPR K-ORG K-AK-REC.
MAIN.
    EVALUATE K-OP
        WHEN "FIND" PERFORM DO-FIND-OR-CREATE
        WHEN "REQG" PERFORM DO-REQUIRE
        WHEN OTHER  MOVE "422" TO K-STATUS
    END-EVALUATE
    GOBACK.

DO-FIND-OR-CREATE.
    MOVE SPACES TO K-AK-REC
    MOVE K-FPR TO K-AK-FPR
    MOVE "FPR " TO WS-OP
    CALL "30AKTEE0" USING WS-OP WS-RET K-AK-REC WS-AK-DUMMY
    IF WS-RET = "00"
        MOVE "200" TO K-STATUS
        EXIT PARAGRAPH
    END-IF
    CALL "00UUIDC0" USING WS-UUID WS-EPOCH
    MOVE SPACES TO K-AK-REC
    MOVE WS-UUID TO K-AK-ID
    MOVE K-FPR TO K-AK-FPR
    MOVE K-ORG TO K-AK-ORG
    MOVE WS-EPOCH TO K-AK-CREATED
    MOVE "WRT " TO WS-OP
    CALL "30AKTEE0" USING WS-OP WS-RET K-AK-REC WS-AK-DUMMY
    IF WS-RET = "22"
        *> lost the race against a concurrent identical save: re-read
        MOVE SPACES TO K-AK-REC
        MOVE K-FPR TO K-AK-FPR
        MOVE "FPR " TO WS-OP
        CALL "30AKTEE0" USING WS-OP WS-RET K-AK-REC WS-AK-DUMMY
        MOVE "200" TO K-STATUS
    ELSE
        MOVE "201" TO K-STATUS
    END-IF.

DO-REQUIRE.
    MOVE "GET " TO WS-OP
    CALL "30AKTEE0" USING WS-OP WS-RET K-AK-REC WS-AK-DUMMY
    IF WS-RET = "00"
        MOVE "200" TO K-STATUS
    ELSE
        MOVE "404" TO K-STATUS
    END-IF.
END PROGRAM "30AKTBC0".
