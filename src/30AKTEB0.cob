>>SOURCE FORMAT FREE
*> 30AKTEB0 — documents: REST boundary for /api/v1/akten, mirroring
*> AktenController.
*>   GET /v1/akten                    visible Akten (ACL filter, no audit)
*>   GET /v1/akten/{id}               404 -> requireRead (existence-hiding)
*>   GET /v1/akten/{id}/documents     paged, ordered by ingest date,
*>                                    caller-visible documents only
IDENTIFICATION DIVISION.
PROGRAM-ID. "30AKTEB0".
DATA DIVISION.
WORKING-STORAGE SECTION.
01 WS-OP                PIC X(4).
01 WS-RET               PIC X(2).
COPY "30AKTER0.cpy" REPLACING ==:PFX:== BY ==WS-AK==.
01 WS-AK-TABLE.
   05 WS-AK-COUNT       PIC 9(4).
   05 WS-AK-ROW OCCURS 300.
      10 WS-AK-ROW-ID   PIC X(36).
      10 WS-AK-ROW-FPR  PIC X(200).
      10 WS-AK-ROW-ORG  PIC X(36).
COPY "30FPRFR0.cpy" REPLACING ==:PFX:== BY ==WS-FP==.
01 WS-FP-TABLE.
   05 WS-FP-COUNT       PIC 9(4).
   05 WS-FP-ROW OCCURS 300.
      10 WS-FP-ROW-DOC  PIC X(36).
COPY "30DOCSR0.cpy" REPLACING ==:PFX:== BY ==WS-DC==.
01 WS-DC-DUMMY.
   05 WS-DC-DUMMY-COUNT PIC 9(4).
   05 WS-DC-DUMMY-ROW OCCURS 300.
      10 FILLER         PIC X(376).
COPY "30STATR0.cpy" REPLACING ==:PFX:== BY ==WS-DS==.
*> collected + sorted akte documents
01 WS-AD-TABLE.
   05 WS-AD-COUNT       PIC 9(4).
   05 WS-AD-ROW OCCURS 300.
      10 WS-AD-ROW-DOC    PIC X(36).
      10 WS-AD-ROW-NAME   PIC X(255).
      10 WS-AD-ROW-INGEST PIC 9(13).
      10 WS-AD-ROW-STATUS PIC X(12).
01 WS-TMP-ROW.
   05 WS-TMP-DOC        PIC X(36).
   05 WS-TMP-NAME       PIC X(255).
   05 WS-TMP-INGEST     PIC 9(13).
   05 WS-TMP-STATUS     PIC X(12).
*> authorization interface
01 WS-A-USER            PIC X(36).
01 WS-A-ADMIN           PIC X.
01 WS-A-ORG             PIC X(36).
01 WS-A-RID             PIC X(36).
01 WS-A-NEEDED          PIC X.
01 WS-A-ACTION          PIC X(10).
01 WS-A-RTYPE           PIC X(20).
01 WS-A-AUDIT           PIC X.
01 WS-A-RESULT          PIC X.
*> helpers
01 WS-ESC-IN            PIC X(512).
01 WS-ESC-OUT           PIC X(1024).
01 WS-ESC-LEN           PIC 9(4).
01 WS-PTR               PIC 9(6) COMP.
01 WS-FIRST             PIC X.
01 WS-I                 PIC 9(4) COMP.
01 WS-J                 PIC 9(4) COMP.
01 WS-ISO               PIC X(20).
01 WS-QP-WANT           PIC X(32).
01 WS-QP-VAL            PIC X(64).
01 WS-PAGE              PIC S9(6) COMP.
01 WS-SIZE              PIC S9(6) COMP.
01 WS-OFFSET            PIC 9(6) COMP.
01 WS-FROM              PIC 9(6) COMP.
01 WS-TO                PIC 9(6) COMP.
LINKAGE SECTION.
COPY "00HTTPR0.cpy".
PROCEDURE DIVISION USING HTTP-EXCHANGE.
MAIN.
    EVALUATE TRUE
        WHEN HX-METHOD = "GET" AND HX-SEG (3) = SPACES
            PERFORM DO-LIST
        WHEN HX-METHOD = "GET" AND HX-SEG (3) NOT = SPACES
                AND HX-SEG (4) = SPACES
            PERFORM DO-GET
        WHEN HX-METHOD = "GET" AND HX-SEG (4) = "documents"
            PERFORM DO-DOCUMENTS
        WHEN OTHER
            MOVE 405 TO HX-STATUS
            MOVE '{"error":"method not allowed"}' TO HX-RESPONSE
    END-EVALUATE
    GOBACK.

DO-LIST.
    MOVE SPACES TO WS-AK-REC
    MOVE "ALL " TO WS-OP
    CALL "30AKTEE0" USING WS-OP WS-RET WS-AK-REC WS-AK-TABLE
    MOVE 1 TO WS-PTR
    MOVE SPACES TO HX-RESPONSE
    STRING "[" DELIMITED BY SIZE
        INTO HX-RESPONSE WITH POINTER WS-PTR
    END-STRING
    MOVE "Y" TO WS-FIRST
    PERFORM VARYING WS-I FROM 1 BY 1 UNTIL WS-I > WS-AK-COUNT
        MOVE WS-AK-ROW-ORG (WS-I) TO WS-A-ORG
        MOVE WS-AK-ROW-ID (WS-I) TO WS-A-RID
        PERFORM CHECK-VISIBLE
        IF WS-A-RESULT = "A"
            IF WS-FIRST = "N"
                STRING "," DELIMITED BY SIZE
                    INTO HX-RESPONSE WITH POINTER WS-PTR
                END-STRING
            END-IF
            MOVE "N" TO WS-FIRST
            MOVE WS-AK-ROW-FPR (WS-I) (1 : 200) TO WS-ESC-IN
            CALL "00JSONC1" USING WS-ESC-IN WS-ESC-OUT WS-ESC-LEN
            STRING '{"id":"' FUNCTION TRIM (WS-AK-ROW-ID (WS-I))
                   '","filePlanReference":"'
                   WS-ESC-OUT (1 : WS-ESC-LEN)
                   '","orgUnitId":"'
                   FUNCTION TRIM (WS-AK-ROW-ORG (WS-I)) '"}'
                DELIMITED BY SIZE INTO HX-RESPONSE
                WITH POINTER WS-PTR
            END-STRING
        END-IF
    END-PERFORM
    STRING "]" DELIMITED BY SIZE
        INTO HX-RESPONSE WITH POINTER WS-PTR
    END-STRING
    MOVE 200 TO HX-STATUS.

CHECK-VISIBLE.
    IF HX-USER-ADMIN = "Y"
        MOVE "A" TO WS-A-RESULT
    ELSE
        MOVE HX-USER-ID TO WS-A-USER
        MOVE HX-USER-ADMIN TO WS-A-ADMIN
        MOVE "V" TO WS-A-NEEDED
        MOVE "READ" TO WS-A-ACTION
        MOVE "AKTE" TO WS-A-RTYPE
        MOVE "N" TO WS-A-AUDIT
        CALL "10AUTHC0" USING WS-A-USER WS-A-ADMIN WS-A-ORG
            WS-A-RID WS-A-NEEDED WS-A-ACTION WS-A-RTYPE
            WS-A-AUDIT WS-A-RESULT
    END-IF.

LOAD-AND-AUTH.
    MOVE "N" TO WS-A-RESULT
    MOVE SPACES TO WS-AK-REC
    MOVE HX-SEG (3) (1 : 36) TO WS-AK-ID
    MOVE "GET " TO WS-OP
    CALL "30AKTEE0" USING WS-OP WS-RET WS-AK-REC WS-AK-TABLE
    IF WS-RET NOT = "00"
        MOVE 404 TO HX-STATUS
        MOVE '{"error":"not found"}' TO HX-RESPONSE
        EXIT PARAGRAPH
    END-IF
    MOVE HX-USER-ID TO WS-A-USER
    MOVE HX-USER-ADMIN TO WS-A-ADMIN
    MOVE WS-AK-ORG TO WS-A-ORG
    MOVE WS-AK-ID TO WS-A-RID
    MOVE "V" TO WS-A-NEEDED
    MOVE "READ" TO WS-A-ACTION
    MOVE "AKTE" TO WS-A-RTYPE
    MOVE "Y" TO WS-A-AUDIT
    CALL "10AUTHC0" USING WS-A-USER WS-A-ADMIN WS-A-ORG WS-A-RID
        WS-A-NEEDED WS-A-ACTION WS-A-RTYPE WS-A-AUDIT WS-A-RESULT
    EVALUATE WS-A-RESULT
        WHEN "N"
            MOVE 404 TO HX-STATUS
            MOVE '{"error":"not found"}' TO HX-RESPONSE
        WHEN "F"
            MOVE 403 TO HX-STATUS
            MOVE '{"error":"insufficient role for READ"}'
                TO HX-RESPONSE
        WHEN OTHER
            CONTINUE
    END-EVALUATE.

DO-GET.
    PERFORM LOAD-AND-AUTH
    IF WS-A-RESULT NOT = "A"
        EXIT PARAGRAPH
    END-IF
    MOVE WS-AK-FPR (1 : 200) TO WS-ESC-IN
    CALL "00JSONC1" USING WS-ESC-IN WS-ESC-OUT WS-ESC-LEN
    MOVE SPACES TO HX-RESPONSE
    STRING '{"id":"' FUNCTION TRIM (WS-AK-ID)
           '","filePlanReference":"' WS-ESC-OUT (1 : WS-ESC-LEN)
           '","orgUnitId":"' FUNCTION TRIM (WS-AK-ORG) '"}'
        DELIMITED BY SIZE INTO HX-RESPONSE
    END-STRING
    MOVE 200 TO HX-STATUS.

DO-DOCUMENTS.
    PERFORM LOAD-AND-AUTH
    IF WS-A-RESULT NOT = "A"
        EXIT PARAGRAPH
    END-IF
    PERFORM READ-PAGING
    MOVE SPACES TO WS-FP-REC
    MOVE WS-AK-ID TO WS-FP-AKTE
    MOVE "BYAK" TO WS-OP
    CALL "30FPRFE0" USING WS-OP WS-RET WS-FP-REC WS-FP-TABLE
    *> collect the caller-visible documents with status
    MOVE 0 TO WS-AD-COUNT
    PERFORM VARYING WS-I FROM 1 BY 1 UNTIL WS-I > WS-FP-COUNT
        MOVE SPACES TO WS-DC-REC
        MOVE WS-FP-ROW-DOC (WS-I) TO WS-DC-ID
        MOVE "GET " TO WS-OP
        CALL "30DOCSE0" USING WS-OP WS-RET WS-DC-REC WS-DC-DUMMY
        IF WS-RET = "00"
            MOVE WS-DC-ORG TO WS-A-ORG
            MOVE WS-DC-ID TO WS-A-RID
            PERFORM CHECK-VISIBLE
            IF WS-A-RESULT = "A" AND WS-AD-COUNT < 300
                ADD 1 TO WS-AD-COUNT
                MOVE WS-DC-ID TO WS-AD-ROW-DOC (WS-AD-COUNT)
                MOVE WS-DC-NAME TO WS-AD-ROW-NAME (WS-AD-COUNT)
                MOVE WS-DC-INGEST TO WS-AD-ROW-INGEST (WS-AD-COUNT)
                MOVE SPACES TO WS-DS-REC
                MOVE WS-DC-ID TO WS-DS-DOC
                MOVE "GET " TO WS-OP
                CALL "30STATE0" USING WS-OP WS-RET WS-DS-REC
                IF WS-RET = "00"
                    MOVE WS-DS-STATUS
                        TO WS-AD-ROW-STATUS (WS-AD-COUNT)
                ELSE
                    MOVE "RECEIVED"
                        TO WS-AD-ROW-STATUS (WS-AD-COUNT)
                END-IF
            END-IF
        END-IF
    END-PERFORM
    PERFORM SORT-BY-INGEST
    *> page window
    COMPUTE WS-FROM = WS-OFFSET + 1
    COMPUTE WS-TO = WS-OFFSET + WS-SIZE
    IF WS-TO > WS-AD-COUNT
        MOVE WS-AD-COUNT TO WS-TO
    END-IF
    MOVE 1 TO WS-PTR
    MOVE SPACES TO HX-RESPONSE
    STRING "[" DELIMITED BY SIZE
        INTO HX-RESPONSE WITH POINTER WS-PTR
    END-STRING
    MOVE "Y" TO WS-FIRST
    PERFORM VARYING WS-I FROM WS-FROM BY 1 UNTIL WS-I > WS-TO
        IF WS-FIRST = "N"
            STRING "," DELIMITED BY SIZE
                INTO HX-RESPONSE WITH POINTER WS-PTR
            END-STRING
        END-IF
        MOVE "N" TO WS-FIRST
        CALL "00TIMEC0" USING WS-AD-ROW-INGEST (WS-I) WS-ISO
        MOVE WS-AD-ROW-NAME (WS-I) (1 : 255) TO WS-ESC-IN
        CALL "00JSONC1" USING WS-ESC-IN WS-ESC-OUT WS-ESC-LEN
        STRING '{"documentId":"'
               FUNCTION TRIM (WS-AD-ROW-DOC (WS-I))
               '","name":"' WS-ESC-OUT (1 : WS-ESC-LEN)
               '","ingestDate":"' FUNCTION TRIM (WS-ISO)
               '","status":"'
               FUNCTION TRIM (WS-AD-ROW-STATUS (WS-I)) '"}'
            DELIMITED BY SIZE INTO HX-RESPONSE WITH POINTER WS-PTR
        END-STRING
    END-PERFORM
    STRING "]" DELIMITED BY SIZE
        INTO HX-RESPONSE WITH POINTER WS-PTR
    END-STRING
    MOVE 200 TO HX-STATUS.

SORT-BY-INGEST.
    *> insertion sort ascending by ingest date (as-is ORDER BY)
    PERFORM VARYING WS-I FROM 2 BY 1 UNTIL WS-I > WS-AD-COUNT
        PERFORM VARYING WS-J FROM WS-I BY -1 UNTIL WS-J < 2
            IF WS-AD-ROW-INGEST (WS-J) < WS-AD-ROW-INGEST (WS-J - 1)
                MOVE WS-AD-ROW (WS-J) TO WS-TMP-ROW
                MOVE WS-AD-ROW (WS-J - 1) TO WS-AD-ROW (WS-J)
                MOVE WS-TMP-ROW TO WS-AD-ROW (WS-J - 1)
            ELSE
                EXIT PERFORM
            END-IF
        END-PERFORM
    END-PERFORM.

READ-PAGING.
    MOVE "page" TO WS-QP-WANT
    PERFORM FIND-QP
    COMPUTE WS-PAGE = FUNCTION NUMVAL (WS-QP-VAL)
    IF WS-QP-VAL = SPACES
        MOVE 0 TO WS-PAGE
    END-IF
    IF WS-PAGE < 0
        MOVE 0 TO WS-PAGE
    END-IF
    MOVE "size" TO WS-QP-WANT
    PERFORM FIND-QP
    COMPUTE WS-SIZE = FUNCTION NUMVAL (WS-QP-VAL)
    IF WS-QP-VAL = SPACES
        MOVE 20 TO WS-SIZE
    END-IF
    IF WS-SIZE < 1
        MOVE 1 TO WS-SIZE
    END-IF
    IF WS-SIZE > 100
        MOVE 100 TO WS-SIZE
    END-IF
    COMPUTE WS-OFFSET = WS-PAGE * WS-SIZE.

FIND-QP.
    MOVE SPACES TO WS-QP-VAL
    PERFORM VARYING WS-J FROM 1 BY 1 UNTIL WS-J > HX-QP-COUNT
        IF HX-QP-NAME (WS-J) = WS-QP-WANT
            MOVE HX-QP-VALUE (WS-J) TO WS-QP-VAL
        END-IF
    END-PERFORM.
END PROGRAM "30AKTEB0".
