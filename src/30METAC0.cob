>>SOURCE FORMAT FREE
*> 30METAC0 — documents: user-confirmed metadata save, mirroring
*> MetadataValidation.save(). Validates the strict ISO date, the class
*> against the controlled vocabulary and the required Ordnungsbegriff,
*> triggers Aktenbildung, upserts DOCMETA (new -> version 1; existing
*> -> version+1, extractedByAi=false, indexing flag CLEARED) and
*> DOCFPR (linked to the Akte). Metadata carries NO optimistic lock —
*> version is server-managed. Search reindex arrives with the search
*> iteration. M-STATUS: 200 ok · 422 validation (M-MSG verbatim).
IDENTIFICATION DIVISION.
PROGRAM-ID. "30METAC0".
DATA DIVISION.
WORKING-STORAGE SECTION.
01 WS-OP                PIC X(4).
01 WS-RET               PIC X(2).
01 WS-DATE              PIC X(64).
01 WS-DATE-LEN          PIC 9(4) COMP.
01 WS-DATE-NUM          PIC 9(8).
01 WS-TD                PIC 9(8).
*> vocabulary interface
01 WS-V-OP              PIC X(4).
01 WS-V-STATUS          PIC X(3).
01 WS-V-MSG             PIC X(200).
01 WS-V-NAME            PIC X(200).
01 WS-V-DESC            PIC X(200).
COPY "30CLASR0.cpy" REPLACING ==:PFX:== BY ==WS-VCL==.
01 WS-VCL-TABLE.
   05 WS-VCL-COUNT      PIC 9(4).
   05 WS-VCL-ROW OCCURS 100.
      10 WS-VCL-ROW-ID   PIC X(36).
      10 WS-VCL-ROW-NAME PIC X(50).
      10 WS-VCL-ROW-DESC PIC X(200).
*> Aktenbildung interface
01 WS-K-OP              PIC X(4).
01 WS-K-STATUS          PIC X(3).
01 WS-K-FPR             PIC X(200).
01 WS-K-ORG             PIC X(36).
COPY "30AKTER0.cpy" REPLACING ==:PFX:== BY ==WS-KAK==.
COPY "30METAR0.cpy" REPLACING ==:PFX:== BY ==WS-ME==.
COPY "30FPRFR0.cpy" REPLACING ==:PFX:== BY ==WS-FP==.
01 WS-FP-DUMMY.
   05 WS-FP-DUMMY-COUNT PIC 9(4).
   05 WS-FP-DUMMY-ROW OCCURS 300.
      10 FILLER         PIC X(36).
01 WS-EPOCH             PIC 9(13).
01 WS-UUID              PIC X(36).
01 WS-I                 PIC 9(4) COMP.
01 WS-PTR               PIC 9(4) COMP.
LINKAGE SECTION.
01 M-STATUS             PIC X(3).
01 M-MSG                PIC X(200).
01 M-DOC                PIC X(36).
01 M-ORG                PIC X(36).
01 M-USER               PIC X(36).
01 M-DATE               PIC X(64).
01 M-CLASS              PIC X(200).
01 M-FPR                PIC X(512).
PROCEDURE DIVISION USING M-STATUS M-MSG M-DOC M-ORG M-USER
                         M-DATE M-CLASS M-FPR.
MAIN.
    MOVE SPACES TO M-MSG
    PERFORM VALIDATE-DATE
    IF M-STATUS = "422"
        GOBACK
    END-IF
    PERFORM VALIDATE-CLASS
    IF M-STATUS = "422"
        GOBACK
    END-IF
    IF FUNCTION TRIM (M-FPR) = SPACES
        MOVE "422" TO M-STATUS
        MOVE "filePlanReference (Ordnungsbegriff) is required"
            TO M-MSG
        GOBACK
    END-IF
    PERFORM AKTENBILDUNG
    PERFORM UPSERT-METADATA
    PERFORM UPSERT-FPR
    MOVE "200" TO M-STATUS
    GOBACK.

VALIDATE-DATE.
    MOVE FUNCTION TRIM (M-DATE) TO WS-DATE
    MOVE FUNCTION STORED-CHAR-LENGTH (WS-DATE) TO WS-DATE-LEN
    IF WS-DATE-LEN = 0
        MOVE "422" TO M-STATUS
        MOVE "documentDate is required (yyyy-MM-dd)" TO M-MSG
        EXIT PARAGRAPH
    END-IF
    IF WS-DATE-LEN NOT = 10
            OR WS-DATE (5 : 1) NOT = "-"
            OR WS-DATE (8 : 1) NOT = "-"
            OR WS-DATE (1 : 4) IS NOT NUMERIC
            OR WS-DATE (6 : 2) IS NOT NUMERIC
            OR WS-DATE (9 : 2) IS NOT NUMERIC
        PERFORM BAD-DATE
        EXIT PARAGRAPH
    END-IF
    MOVE SPACES TO M-MSG
    COMPUTE WS-DATE-NUM =
        FUNCTION NUMVAL (WS-DATE (1 : 4)) * 10000
        + FUNCTION NUMVAL (WS-DATE (6 : 2)) * 100
        + FUNCTION NUMVAL (WS-DATE (9 : 2))
    COMPUTE WS-TD = FUNCTION TEST-DATE-YYYYMMDD (WS-DATE-NUM)
    IF WS-TD NOT = 0
        PERFORM BAD-DATE
    END-IF.

BAD-DATE.
    MOVE "422" TO M-STATUS
    MOVE "documentDate must be an ISO date (yyyy-MM-dd)" TO M-MSG.

VALIDATE-CLASS.
    MOVE M-CLASS TO WS-V-NAME
    MOVE "NORM" TO WS-V-OP
    CALL "30VOCAC0" USING WS-V-OP WS-V-STATUS WS-V-MSG WS-V-NAME
        WS-V-DESC WS-VCL-REC WS-VCL-TABLE
    IF WS-V-STATUS = "200"
        *> canonical upper-case code now in WS-VCL-NAME
        EXIT PARAGRAPH
    END-IF
    MOVE "422" TO M-STATUS
    MOVE "LIST" TO WS-V-OP
    CALL "30VOCAC0" USING WS-V-OP WS-V-STATUS WS-V-MSG WS-V-NAME
        WS-V-DESC WS-VCL-REC WS-VCL-TABLE
    MOVE SPACES TO M-MSG
    MOVE 1 TO WS-PTR
    STRING "documentClass outside controlled vocabulary: ["
        DELIMITED BY SIZE INTO M-MSG WITH POINTER WS-PTR
    END-STRING
    PERFORM VARYING WS-I FROM 1 BY 1 UNTIL WS-I > WS-VCL-COUNT
        IF WS-I > 1
            STRING ", " DELIMITED BY SIZE
                INTO M-MSG WITH POINTER WS-PTR
            END-STRING
        END-IF
        STRING FUNCTION TRIM (WS-VCL-ROW-NAME (WS-I))
            DELIMITED BY SIZE INTO M-MSG WITH POINTER WS-PTR
        END-STRING
    END-PERFORM
    STRING "]" DELIMITED BY SIZE INTO M-MSG WITH POINTER WS-PTR
    END-STRING.

AKTENBILDUNG.
    MOVE FUNCTION TRIM (M-FPR) TO WS-K-FPR
    MOVE M-ORG TO WS-K-ORG
    MOVE "FIND" TO WS-K-OP
    CALL "30AKTBC0" USING WS-K-OP WS-K-STATUS WS-K-FPR WS-K-ORG
        WS-KAK-REC.

UPSERT-METADATA.
    CALL "00UUIDC0" USING WS-UUID WS-EPOCH
    MOVE SPACES TO WS-ME-REC
    MOVE M-DOC TO WS-ME-DOC
    MOVE "GET " TO WS-OP
    CALL "30METAE0" USING WS-OP WS-RET WS-ME-REC
    IF WS-RET = "00"
        *> DocumentMetadata.update(): manual indexing performed —
        *> flag cleared, version bumped, extractedByAi false
        MOVE WS-DATE (1 : 10) TO WS-ME-DATE
        MOVE WS-VCL-NAME TO WS-ME-CLASS
        MOVE "N" TO WS-ME-EXTRACTED
        MOVE M-USER TO WS-ME-UPDATED-BY
        MOVE SPACES TO WS-ME-FLAG
        ADD 1 TO WS-ME-VERSION
        MOVE WS-EPOCH TO WS-ME-UPDATED
        MOVE "REW " TO WS-OP
        CALL "30METAE0" USING WS-OP WS-RET WS-ME-REC
    ELSE
        MOVE SPACES TO WS-ME-REC
        MOVE M-DOC TO WS-ME-DOC
        MOVE WS-DATE (1 : 10) TO WS-ME-DATE
        MOVE WS-VCL-NAME TO WS-ME-CLASS
        MOVE "N" TO WS-ME-EXTRACTED
        MOVE M-USER TO WS-ME-UPDATED-BY
        MOVE SPACES TO WS-ME-FLAG
        MOVE 1 TO WS-ME-VERSION
        MOVE WS-EPOCH TO WS-ME-CREATED
        MOVE WS-EPOCH TO WS-ME-UPDATED
        MOVE "WRT " TO WS-OP
        CALL "30METAE0" USING WS-OP WS-RET WS-ME-REC
    END-IF.

UPSERT-FPR.
    MOVE SPACES TO WS-FP-REC
    MOVE M-DOC TO WS-FP-DOC
    MOVE "GET " TO WS-OP
    CALL "30FPRFE0" USING WS-OP WS-RET WS-FP-REC WS-FP-DUMMY
    IF WS-RET = "00"
        MOVE FUNCTION TRIM (M-FPR) TO WS-FP-FPR
        MOVE WS-KAK-ID TO WS-FP-AKTE
        MOVE "N" TO WS-FP-EXTRACTED
        MOVE M-USER TO WS-FP-UPDATED-BY
        ADD 1 TO WS-FP-VERSION
        MOVE "REW " TO WS-OP
        CALL "30FPRFE0" USING WS-OP WS-RET WS-FP-REC WS-FP-DUMMY
    ELSE
        MOVE SPACES TO WS-FP-REC
        MOVE M-DOC TO WS-FP-DOC
        MOVE FUNCTION TRIM (M-FPR) TO WS-FP-FPR
        MOVE WS-KAK-ID TO WS-FP-AKTE
        MOVE "N" TO WS-FP-EXTRACTED
        MOVE M-USER TO WS-FP-UPDATED-BY
        MOVE 1 TO WS-FP-VERSION
        MOVE WS-EPOCH TO WS-FP-CREATED
        MOVE "WRT " TO WS-OP
        CALL "30FPRFE0" USING WS-OP WS-RET WS-FP-REC WS-FP-DUMMY
    END-IF.
END PROGRAM "30METAC0".
