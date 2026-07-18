>>SOURCE FORMAT FREE
*> 50CONVC0 — conversion: OCR-only pipeline for one document (D-9).
*> Replaces the Python conversion service AND the Java JobDispatcher's
*> conversion step. Because intake is PDF-only (D-8) there is NO PDF/A
*> normalization: the sole job is to OCR the stored ORIGINAL into plain
*> text for indexing / AI. Steps (mirrors the OCR rung of convert.py):
*>   1. DOCSTAT -> CONVERTING
*>   2. read the ORIGINAL rendition, resolve its object-store path
*>   3. ocrmypdf --skip-text  (born-digital PDFs pass through; scanned
*>      pages get a text layer) -> scratch searchable PDF; on failure
*>      fall back to the original for text extraction
*>   4. pdftotext -layout      -> the plain text (empty is valid: a
*>      scanned page whose OCR yields nothing is still a success)
*>   5. store renditions/{id}/text.txt (durable rename) + upsert the
*>      TEXT rendition (producer ocrmypdf)
*>   6. DOCSTAT -> READY
*> ghostscript is present only as ocrmypdf's transitive OCR dependency,
*> never invoked directly here (no PDF/A ladder, D-9). AI extraction and
*> the search index are wired in later iterations (6 / 5); this control
*> just produces the TEXT rendition and reaches READY.
*> L-RET: 00 ok · 23 document/rendition missing · 9x storage/tool error.
IDENTIFICATION DIVISION.
PROGRAM-ID. "50CONVC0".
ENVIRONMENT DIVISION.
INPUT-OUTPUT SECTION.
FILE-CONTROL.
    SELECT META-FILE ASSIGN TO WS-META-PATH
        ORGANIZATION IS LINE SEQUENTIAL
        FILE STATUS IS WS-META-FS.
DATA DIVISION.
FILE SECTION.
FD META-FILE.
01 WS-META-REC          PIC X(200).
WORKING-STORAGE SECTION.
01 WS-OP                PIC X(4).
01 WS-RET               PIC X(2).
01 WS-UUID              PIC X(36).
01 WS-EPOCH             PIC 9(13).
COPY "30STATR0.cpy" REPLACING ==:PFX:== BY ==WS-DS==.
COPY "30RENDR0.cpy" REPLACING ==:PFX:== BY ==WS-RD==.
01 WS-RD-TABLE.
   05 WS-RD-COUNT       PIC 9(4).
   05 WS-RD-ROW OCCURS 10.
      10 WS-RD-ROW-TYPE     PIC X(10).
      10 WS-RD-ROW-MIME     PIC X(100).
      10 WS-RD-ROW-SIZE     PIC 9(13).
      10 WS-RD-ROW-SHA256   PIC X(64).
      10 WS-RD-ROW-PRODUCER PIC X(15).
*> object store interface
01 WS-S-OP              PIC X(4).
01 WS-S-RET             PIC X(2).
01 WS-S-KEY             PIC X(200).
01 WS-S-SRC             PIC X(512).
01 WS-S-PATH            PIC X(512).
*> scratch + tool plumbing
01 WS-PID               USAGE BINARY-LONG.
01 WS-PID-Z             PIC 9(9).
01 WS-INPATH            PIC X(512).
01 WS-OCRPDF            PIC X(512).
01 WS-TXTSTAGE          PIC X(512).
01 WS-TXTKEY            PIC X(200).
01 WS-SRCPDF            PIC X(512).
01 WS-LANG              PIC X(32).
01 WS-CMD               PIC X(1400).
01 WS-RC                USAGE BINARY-LONG.
01 WS-SIZE              PIC 9(13).
01 WS-SHA256            PIC X(64).
01 WS-META-PATH         PIC X(512).
01 WS-META-FS           PIC X(2).
LINKAGE SECTION.
01 L-DOC-ID             PIC X(36).
01 L-RET                PIC X(2).
PROCEDURE DIVISION USING L-DOC-ID L-RET.
MAIN.
    MOVE "00" TO L-RET
    PERFORM SET-CONVERTING
    PERFORM LOAD-ORIGINAL
    IF L-RET NOT = "00"
        GOBACK
    END-IF
    PERFORM RUN-OCR
    PERFORM STORE-TEXT
    IF L-RET NOT = "00"
        GOBACK
    END-IF
    PERFORM UPSERT-TEXT-RENDITION
    PERFORM SET-READY
    GOBACK.

SET-CONVERTING.
    MOVE SPACES TO WS-DS-REC
    MOVE L-DOC-ID TO WS-DS-DOC
    MOVE "GET " TO WS-OP
    CALL "30STATE0" USING WS-OP WS-RET WS-DS-REC
    IF WS-RET = "00"
        CALL "00UUIDC0" USING WS-UUID WS-EPOCH
        MOVE "CONVERTING" TO WS-DS-STATUS
        MOVE SPACES TO WS-DS-CHANGED-BY
        MOVE WS-EPOCH TO WS-DS-UPDATED
        MOVE "REW " TO WS-OP
        CALL "30STATE0" USING WS-OP WS-RET WS-DS-REC
    END-IF.

LOAD-ORIGINAL.
    *> the ORIGINAL rendition holds the object-store key of the upload
    MOVE SPACES TO WS-RD-REC
    MOVE L-DOC-ID TO WS-RD-DOC
    MOVE "ORIGINAL" TO WS-RD-TYPE
    MOVE "GETD" TO WS-OP
    CALL "30RENDE0" USING WS-OP WS-RET WS-RD-REC WS-RD-TABLE
    IF WS-RET NOT = "00"
        MOVE "23" TO L-RET
        EXIT PARAGRAPH
    END-IF
    MOVE WS-RD-KEY (1 : 200) TO WS-S-KEY
    MOVE "PATH" TO WS-S-OP
    CALL "00STORC0" USING WS-S-OP WS-S-RET WS-S-KEY WS-S-SRC WS-S-PATH
    MOVE WS-S-PATH TO WS-INPATH.

RUN-OCR.
    *> unique scratch names under the store's tmp dir (same filesystem
    *> as the target -> the later rename is atomic, R-1)
    CALL "getpid" RETURNING WS-PID
    MOVE WS-PID TO WS-PID-Z
    PERFORM RESOLVE-TMP-BASE
    MOVE SPACES TO WS-LANG
    ACCEPT WS-LANG FROM ENVIRONMENT "DMS_OCR_LANG"
        ON EXCEPTION MOVE SPACES TO WS-LANG
    END-ACCEPT
    IF WS-LANG = SPACES
        MOVE "eng" TO WS-LANG
    END-IF
    *> 1) OCR the PDF (skip pages that already carry text). Any ocrmypdf
    *>    failure (missing toolchain, corrupt PDF) is non-fatal: we then
    *>    extract text straight from the original. stderr is discarded.
    MOVE SPACES TO WS-CMD
    STRING "ocrmypdf --skip-text --output-type pdf -l "
           FUNCTION TRIM (WS-LANG)
           " '" FUNCTION TRIM (WS-INPATH) "' '"
           FUNCTION TRIM (WS-OCRPDF) "' >/dev/null 2>&1"
        DELIMITED BY SIZE INTO WS-CMD
    END-STRING
    CALL "SYSTEM" USING WS-CMD RETURNING WS-RC
    IF WS-RC = 0
        MOVE WS-OCRPDF TO WS-SRCPDF
    ELSE
        MOVE WS-INPATH TO WS-SRCPDF
    END-IF
    *> 2) plain-text extraction (port of textextract.py's pdftotext rung)
    MOVE SPACES TO WS-CMD
    STRING "pdftotext -layout '" FUNCTION TRIM (WS-SRCPDF)
           "' '" FUNCTION TRIM (WS-TXTSTAGE) "' 2>/dev/null"
        DELIMITED BY SIZE INTO WS-CMD
    END-STRING
    CALL "SYSTEM" USING WS-CMD RETURNING WS-RC
    IF WS-RC NOT = 0
        *> retry directly on the original, then guarantee a file exists
        MOVE SPACES TO WS-CMD
        STRING "pdftotext -layout '" FUNCTION TRIM (WS-INPATH)
               "' '" FUNCTION TRIM (WS-TXTSTAGE)
               "' 2>/dev/null || : > '"
               FUNCTION TRIM (WS-TXTSTAGE) "'"
            DELIMITED BY SIZE INTO WS-CMD
        END-STRING
        CALL "SYSTEM" USING WS-CMD RETURNING WS-RC
    END-IF.

RESOLVE-TMP-BASE.
    *> reuse the object store's tmp staging area (created by VRFY/start)
    MOVE "tmp/ocr" TO WS-S-KEY
    MOVE "PATH" TO WS-S-OP
    CALL "00STORC0" USING WS-S-OP WS-S-RET WS-S-KEY WS-S-SRC WS-S-PATH
    MOVE SPACES TO WS-OCRPDF
    STRING FUNCTION TRIM (WS-S-PATH) "-" WS-PID-Z ".pdf"
        DELIMITED BY SIZE INTO WS-OCRPDF
    END-STRING
    MOVE SPACES TO WS-TXTSTAGE
    STRING FUNCTION TRIM (WS-S-PATH) "-" WS-PID-Z ".txt"
        DELIMITED BY SIZE INTO WS-TXTSTAGE
    END-STRING.

STORE-TEXT.
    PERFORM MEASURE-TEXT
    *> durable rename of the staged text into the object store
    MOVE SPACES TO WS-TXTKEY
    STRING "renditions/" FUNCTION TRIM (L-DOC-ID) "/text.txt"
        DELIMITED BY SIZE INTO WS-TXTKEY
    END-STRING
    MOVE WS-TXTKEY TO WS-S-KEY
    MOVE WS-TXTSTAGE TO WS-S-SRC
    MOVE "PUTF" TO WS-S-OP
    CALL "00STORC0" USING WS-S-OP WS-S-RET WS-S-KEY WS-S-SRC WS-S-PATH
    IF WS-S-RET NOT = "00"
        MOVE "91" TO L-RET
    END-IF
    *> clean up the discarded scratch PDF (ignore result)
    MOVE SPACES TO WS-CMD
    STRING "rm -f '" FUNCTION TRIM (WS-OCRPDF) "'"
        DELIMITED BY SIZE INTO WS-CMD
    END-STRING
    CALL "SYSTEM" USING WS-CMD RETURNING WS-RC.

MEASURE-TEXT.
    *> size + sha256 of the staged text via CLI (mirrors 00MPARC0),
    *> both written to a scratch metadata file we then parse
    MOVE SPACES TO WS-META-PATH
    STRING FUNCTION TRIM (WS-TXTSTAGE) ".meta"
        DELIMITED BY SIZE INTO WS-META-PATH
    END-STRING
    MOVE SPACES TO WS-CMD
    STRING "{ stat -c %s '" FUNCTION TRIM (WS-TXTSTAGE)
           "'; sha256sum '" FUNCTION TRIM (WS-TXTSTAGE)
           "'; } > '" FUNCTION TRIM (WS-META-PATH) "' 2>/dev/null"
        DELIMITED BY SIZE INTO WS-CMD
    END-STRING
    CALL "SYSTEM" USING WS-CMD RETURNING WS-RC
    MOVE 0 TO WS-SIZE
    MOVE SPACES TO WS-SHA256
    OPEN INPUT META-FILE
    IF WS-META-FS = "00"
        READ META-FILE
        IF WS-META-FS = "00"
            MOVE FUNCTION NUMVAL (WS-META-REC) TO WS-SIZE
        END-IF
        READ META-FILE
        IF WS-META-FS = "00"
            MOVE WS-META-REC (1 : 64) TO WS-SHA256
        END-IF
        CLOSE META-FILE
    END-IF
    MOVE SPACES TO WS-CMD
    STRING "rm -f '" FUNCTION TRIM (WS-META-PATH) "'"
        DELIMITED BY SIZE INTO WS-CMD
    END-STRING
    CALL "SYSTEM" USING WS-CMD RETURNING WS-RC.

UPSERT-TEXT-RENDITION.
    *> idempotent (deterministic key): drop any prior TEXT row first
    MOVE SPACES TO WS-RD-REC
    MOVE L-DOC-ID TO WS-RD-DOC
    MOVE "TEXT" TO WS-RD-TYPE
    MOVE "GETD" TO WS-OP
    CALL "30RENDE0" USING WS-OP WS-RET WS-RD-REC WS-RD-TABLE
    IF WS-RET = "00"
        MOVE "DEL " TO WS-OP
        CALL "30RENDE0" USING WS-OP WS-RET WS-RD-REC WS-RD-TABLE
    END-IF
    CALL "00UUIDC0" USING WS-UUID WS-EPOCH
    MOVE SPACES TO WS-RD-REC
    MOVE WS-UUID TO WS-RD-ID
    MOVE L-DOC-ID TO WS-RD-DOC
    MOVE "TEXT" TO WS-RD-TYPE
    MOVE WS-TXTKEY TO WS-RD-KEY
    MOVE "text/plain; charset=utf-8" TO WS-RD-MIME
    MOVE WS-SIZE TO WS-RD-SIZE
    MOVE WS-SHA256 TO WS-RD-SHA256
    MOVE WS-EPOCH TO WS-RD-CREATED
    MOVE "ocrmypdf" TO WS-RD-PRODUCER
    MOVE "WRT " TO WS-OP
    CALL "30RENDE0" USING WS-OP WS-RET WS-RD-REC WS-RD-TABLE.

SET-READY.
    MOVE SPACES TO WS-DS-REC
    MOVE L-DOC-ID TO WS-DS-DOC
    MOVE "GET " TO WS-OP
    CALL "30STATE0" USING WS-OP WS-RET WS-DS-REC
    IF WS-RET = "00"
        CALL "00UUIDC0" USING WS-UUID WS-EPOCH
        MOVE "READY" TO WS-DS-STATUS
        MOVE SPACES TO WS-DS-CHANGED-BY
        MOVE WS-EPOCH TO WS-DS-UPDATED
        MOVE "REW " TO WS-OP
        CALL "30STATE0" USING WS-OP WS-RET WS-DS-REC
    END-IF
    MOVE "00" TO L-RET.
END PROGRAM "50CONVC0".
