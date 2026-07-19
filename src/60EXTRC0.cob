>>SOURCE FORMAT FREE
*> 60EXTRC0 — aiextraction: metadata extraction control. The COBOL port
*> of MetadataExtraction + ExtractionPrompt + the parse half of
*> AiExtractionClient, plus MetadataValidation.applySuggestions /
*> flagForIndexing. Text-only (D-9): the document is sent as its OCR
*> text (renditions/{id}/text.txt), never a PDF/image.
*> Flow, per document id:
*>   1. unconfigured (no DMS_AI_TOKEN) or blank text -> skip, flag the
*>      document MANUAL_INDEXING; it still reaches READY (QG-1).
*>   2. build the system prompt from the catalogs (classes + active
*>      Ordnungsbegriff types + intents/fields), POST a chat-completions
*>      request via 60CURLC0 (libcurl), and leniently parse the answer.
*>   3. a transport/HTTP error flags REVIEW; a good answer prefills empty
*>      metadata (extractedByAi, never overwriting user rows, never
*>      forming Akten) and sets the indexing flag from the Ordnungsbegriff
*>      section (malformed -> REVIEW, none -> MANUAL_INDEXING).
*> L-OUTCOME: S success · K skipped(unconfigured/no text) · F failure.
IDENTIFICATION DIVISION.
PROGRAM-ID. "60EXTRC0".
ENVIRONMENT DIVISION.
DATA DIVISION.
WORKING-STORAGE SECTION.
01 WS-OP                PIC X(4).
01 WS-RET               PIC X(2).
01 WS-UUID              PIC X(36).
01 WS-EPOCH             PIC 9(13).
01 WS-CONFIGURED        PIC X VALUE "N".
01 WS-FLAG              PIC X(15).
*> provider config
01 WS-AI-URL            PIC X(512).
01 WS-AI-TOKEN          PIC X(200).
01 WS-AI-MODEL          PIC X(100).
*> request logging (DMS_AI_LOG_REQUEST) — off unless explicitly enabled
01 WS-LOG-REQ           PIC X VALUE "N".
01 WS-AI-LOG            PIC X(20).
01 WS-TOKLEN            PIC 9(4).
*> OCR text
01 WS-S-OP              PIC X(4).
01 WS-S-RET             PIC X(2).
01 WS-S-KEY             PIC X(200).
01 WS-S-SRC             PIC X(512).
01 WS-S-PATH            PIC X(512).
01 WS-PATH-Z            PIC X(520).
01 WS-MODE-RB           PIC X(3).
01 WS-FP                USAGE POINTER.
01 WS-NREAD             USAGE BINARY-C-LONG.
01 WS-TEXT              PIC X(100000).
01 WS-TEXTLEN           PIC 9(9).
*> prompt + body
01 WS-SYS               PIC X(8000).
01 WS-SP                PIC 9(9) COMP.
01 WS-BODY              PIC X(300000).
01 WS-BP                PIC 9(9) COMP.
01 WS-BODYLEN           PIC 9(9).
01 WS-ESRC              PIC X(100000).
01 WS-ESRCLEN           PIC 9(9).
01 WS-EI                PIC 9(9) COMP.
01 WS-EC                PIC X.
*> curl I/O
01 WS-RESP              PIC X(1048576).
01 WS-RESPLEN           PIC 9(9).
01 WS-STATUS            PIC 9(3).
01 WS-CRET              PIC X(2).
*> answer parsing
01 WS-ANSWER            PIC X(4096).
01 WS-ANSWER-LEN        PIC 9(9).
01 WS-A4K               PIC X(4096).
01 WS-KEY               PIC X(64).
01 WS-VAL               PIC X(512).
01 WS-FOUND             PIC X.
01 WS-SUG-CLASS         PIC X(512).
01 WS-SUG-DATE          PIC X(512).
01 WS-SUG-FPR           PIC X(512).
01 WS-SUG-INTENT        PIC X(512).
01 WS-NORM-CLASS        PIC X(50).
01 WS-INT-FIELDS        PIC X(2000).
01 WS-FP2               PIC 9(9) COMP.
01 WS-DATE-OK           PIC X.
01 WS-FIRSTF            PIC X.
01 WS-MATCH             PIC X.
*> ordnungsbegriffe
01 WS-ORDN-STATE        PIC X(5).
01 WS-ORDN-COUNT        PIC 9(4).
01 WS-ORDN-ENTRY OCCURS 20.
   05 WS-ORDN-TYPE      PIC X(100).
   05 WS-ORDN-VALUE     PIC X(200).
01 WS-OBJ               PIC X(512).
01 WS-OBJLEN            PIC 9(9) COMP.
01 WS-SCAN              PIC 9(9) COMP.
01 WS-DEPTH             PIC 9(4) COMP.
01 WS-OSTART            PIC 9(9) COMP.
01 WS-CH                PIC X.
01 WS-K                 PIC 9(4) COMP.
01 WS-CANON             PIC X(100).
*> catalog tables
COPY "30CLASR0.cpy" REPLACING ==:PFX:== BY ==WS-CL==.
01 WS-CL-TABLE.
   05 WS-CL-COUNT       PIC 9(4).
   05 WS-CL-ROW OCCURS 100.
      10 WS-CL-ROW-ID   PIC X(36).
      10 WS-CL-ROW-NAME PIC X(50).
      10 WS-CL-ROW-DESC PIC X(200).
COPY "60INTNR0.cpy" REPLACING ==:PFX:== BY ==WS-IN==.
01 WS-IN-TABLE.
   05 WS-IN-COUNT       PIC 9(4).
   05 WS-IN-ROW OCCURS 50.
      10 WS-IN-ROW-ID   PIC X(36).
      10 WS-IN-ROW-NAME PIC X(100).
      10 WS-IN-ROW-DESC PIC X(300).
COPY "60INFDR0.cpy" REPLACING ==:PFX:== BY ==WS-IF==.
01 WS-IF-TABLE.
   05 WS-IF-COUNT       PIC 9(4).
   05 WS-IF-ROW OCCURS 30.
      10 WS-IF-ROW-NAME PIC X(100).
      10 WS-IF-ROW-DESC PIC X(300).
COPY "60ORDTR0.cpy" REPLACING ==:PFX:== BY ==WS-OT==.
01 WS-OT-TABLE.
   05 WS-OT-COUNT       PIC 9(4).
   05 WS-OT-ROW OCCURS 50.
      10 WS-OT-ROW-ID     PIC X(36).
      10 WS-OT-ROW-NAME   PIC X(100).
      10 WS-OT-ROW-DESC   PIC X(300).
      10 WS-OT-ROW-ACTIVE PIC X.
01 WS-I                 PIC 9(4) COMP.
01 WS-J                 PIC 9(4) COMP.
*> metadata records for apply
COPY "30METAR0.cpy" REPLACING ==:PFX:== BY ==WS-ME==.
COPY "30FPRFR0.cpy" REPLACING ==:PFX:== BY ==WS-FR==.
01 WS-FR-TABLE.
   05 WS-FR-COUNT       PIC 9(4).
   05 WS-FR-ROW OCCURS 50.
      10 FILLER         PIC X(36).
COPY "30ORDBR0.cpy" REPLACING ==:PFX:== BY ==WS-OB==.
01 WS-OB-TABLE.
   05 WS-OB-COUNT       PIC 9(4).
   05 WS-OB-ROW OCCURS 50.
      10 FILLER         PIC X(300).
COPY "30INTNR0.cpy" REPLACING ==:PFX:== BY ==WS-DI==.
LINKAGE SECTION.
01 L-DOC-ID             PIC X(36).
01 L-FILENAME           PIC X(255).
01 L-OUTCOME            PIC X.
PROCEDURE DIVISION USING L-DOC-ID L-FILENAME L-OUTCOME.
MAIN.
    MOVE "K" TO L-OUTCOME
    DISPLAY "60EXTRC0: AI extraction requested for document "
        FUNCTION TRIM (L-DOC-ID)
    PERFORM CHECK-CONFIG
    IF WS-CONFIGURED = "N"
        DISPLAY "60EXTRC0: " FUNCTION TRIM (L-DOC-ID)
            " skipped — AI unconfigured (no DMS_AI_TOKEN) -> "
            "MANUAL_INDEXING"
        MOVE "MANUAL_INDEXING" TO WS-FLAG
        PERFORM FLAG-FOR-INDEXING
        GOBACK
    END-IF
    PERFORM READ-TEXT
    IF WS-TEXTLEN = 0
        DISPLAY "60EXTRC0: " FUNCTION TRIM (L-DOC-ID)
            " skipped — no OCR text -> MANUAL_INDEXING"
        MOVE "MANUAL_INDEXING" TO WS-FLAG
        PERFORM FLAG-FOR-INDEXING
        GOBACK
    END-IF
    PERFORM LOAD-CATALOGS
    PERFORM BUILD-PROMPT
    PERFORM BUILD-BODY
    DISPLAY "60EXTRC0: calling " FUNCTION TRIM (WS-AI-URL)
        "/chat/completions model " FUNCTION TRIM (WS-AI-MODEL)
        " for " FUNCTION TRIM (L-DOC-ID)
        " (" WS-TEXTLEN " text bytes, " WS-BODYLEN " body bytes)"
    IF WS-LOG-REQ = "Y"
        PERFORM LOG-REQUEST
    END-IF
    CALL "60CURLC0" USING WS-AI-URL WS-AI-TOKEN WS-BODY WS-BODYLEN
        WS-RESP WS-RESPLEN WS-STATUS WS-CRET
    IF WS-CRET NOT = "00"
        DISPLAY "60EXTRC0: " FUNCTION TRIM (L-DOC-ID)
            " AI call FAILED (curl ret=" WS-CRET
            " http=" WS-STATUS ") -> REVIEW"
        MOVE "REVIEW" TO WS-FLAG
        PERFORM FLAG-FOR-INDEXING
        MOVE "F" TO L-OUTCOME
        GOBACK
    END-IF
    PERFORM EXTRACT-CONTENT
    IF WS-ANSWER-LEN = 0
        DISPLAY "60EXTRC0: " FUNCTION TRIM (L-DOC-ID)
            " unusable AI response (http=" WS-STATUS ") -> REVIEW"
        MOVE "REVIEW" TO WS-FLAG
        PERFORM FLAG-FOR-INDEXING
        MOVE "F" TO L-OUTCOME
        GOBACK
    END-IF
    PERFORM PARSE-ANSWER
    PERFORM APPLY-SUGGESTIONS
    DISPLAY "60EXTRC0: " FUNCTION TRIM (L-DOC-ID)
        " extraction OK (http=" WS-STATUS
        " class=" FUNCTION TRIM (WS-NORM-CLASS)
        " intent=" FUNCTION TRIM (WS-SUG-INTENT)
        " ordnungsbegriffe=" WS-ORDN-COUNT " flag=" FUNCTION TRIM (WS-FLAG)
        ")"
    MOVE "S" TO L-OUTCOME
    GOBACK.

CHECK-CONFIG.
    MOVE SPACES TO WS-AI-TOKEN
    ACCEPT WS-AI-TOKEN FROM ENVIRONMENT "DMS_AI_TOKEN"
        ON EXCEPTION MOVE SPACES TO WS-AI-TOKEN
    END-ACCEPT
    IF FUNCTION TRIM (WS-AI-TOKEN) = SPACES
        MOVE "N" TO WS-CONFIGURED
    ELSE
        MOVE "Y" TO WS-CONFIGURED
    END-IF
    MOVE SPACES TO WS-AI-URL
    ACCEPT WS-AI-URL FROM ENVIRONMENT "DMS_AI_URL"
        ON EXCEPTION MOVE SPACES TO WS-AI-URL
    END-ACCEPT
    IF FUNCTION TRIM (WS-AI-URL) = SPACES
        MOVE "https://api.openai.com/v1" TO WS-AI-URL
    END-IF
    MOVE SPACES TO WS-AI-MODEL
    ACCEPT WS-AI-MODEL FROM ENVIRONMENT "DMS_AI_MODEL"
        ON EXCEPTION MOVE SPACES TO WS-AI-MODEL
    END-ACCEPT
    IF FUNCTION TRIM (WS-AI-MODEL) = SPACES
        MOVE "gpt-5-mini" TO WS-AI-MODEL
    END-IF
    *> request logging toggle: enabled for 1/true/yes/on (any case)
    MOVE "N" TO WS-LOG-REQ
    MOVE SPACES TO WS-AI-LOG
    ACCEPT WS-AI-LOG FROM ENVIRONMENT "DMS_AI_LOG_REQUEST"
        ON EXCEPTION MOVE SPACES TO WS-AI-LOG
    END-ACCEPT
    MOVE FUNCTION LOWER-CASE (FUNCTION TRIM (WS-AI-LOG)) TO WS-AI-LOG
    EVALUATE WS-AI-LOG
        WHEN "1"
        WHEN "true"
        WHEN "yes"
        WHEN "on"
            MOVE "Y" TO WS-LOG-REQ
    END-EVALUATE.

READ-TEXT.
    MOVE 0 TO WS-TEXTLEN
    MOVE SPACES TO WS-TEXT
    MOVE SPACES TO WS-S-KEY
    STRING "renditions/" FUNCTION TRIM (L-DOC-ID) "/text.txt"
        DELIMITED BY SIZE INTO WS-S-KEY
    END-STRING
    MOVE "PATH" TO WS-S-OP
    CALL "00STORC0" USING WS-S-OP WS-S-RET WS-S-KEY WS-S-SRC WS-S-PATH
    MOVE SPACES TO WS-PATH-Z
    STRING FUNCTION TRIM (WS-S-PATH) X"00" DELIMITED BY SIZE
        INTO WS-PATH-Z
    END-STRING
    MOVE SPACES TO WS-MODE-RB
    STRING "rb" X"00" DELIMITED BY SIZE INTO WS-MODE-RB
    END-STRING
    CALL "fopen" USING BY REFERENCE WS-PATH-Z BY REFERENCE WS-MODE-RB
        RETURNING WS-FP
    IF WS-FP = NULL
        EXIT PARAGRAPH
    END-IF
    CALL "fread" USING BY REFERENCE WS-TEXT BY VALUE 1
        BY VALUE 100000 BY VALUE WS-FP RETURNING WS-NREAD
    CALL "fclose" USING BY VALUE WS-FP
    IF WS-NREAD > 0
        MOVE WS-NREAD TO WS-TEXTLEN
    END-IF
    IF FUNCTION TRIM (WS-TEXT (1 : WS-TEXTLEN)) = SPACES
        MOVE 0 TO WS-TEXTLEN
    END-IF.

LOAD-CATALOGS.
    MOVE "ALL " TO WS-OP
    CALL "30CLASE0" USING WS-OP WS-RET WS-CL-REC WS-CL-TABLE
    MOVE "ALL " TO WS-OP
    CALL "60INTNE0" USING WS-OP WS-RET WS-IN-REC WS-IN-TABLE
    MOVE "ALL " TO WS-OP
    CALL "60ORDTE0" USING WS-OP WS-RET WS-OT-REC WS-OT-TABLE.

*> --- system prompt (port of prompt.system) ------------------------
BUILD-PROMPT.
    MOVE 1 TO WS-SP
    MOVE SPACES TO WS-SYS
    STRING
      "You are the document analysis service of a German document "
      "management system. You receive one document. Classify it and "
      "extract filing metadata." X"0A" X"0A"
      "Rules:" X"0A"
      "- Respond with a single JSON object only - no markdown, no "
      "code fences, no explanations." X"0A"
      "- Use exactly the keys listed below. Use null for any value "
      "that cannot be determined from the document." X"0A"
      "- Dates always in ISO format yyyy-MM-dd." X"0A" X"0A"
      "Keys to extract:" X"0A"
      "- documentDate: The date the document was issued, ISO format "
      "yyyy-MM-dd." X"0A"
      "- documentClass: The document category."
        DELIMITED BY SIZE INTO WS-SYS WITH POINTER WS-SP
    END-STRING
    IF WS-CL-COUNT > 0
        STRING " Must be exactly one of the following codes:" X"0A"
            DELIMITED BY SIZE INTO WS-SYS WITH POINTER WS-SP
        END-STRING
        PERFORM VARYING WS-I FROM 1 BY 1 UNTIL WS-I > WS-CL-COUNT
            STRING "  - " FUNCTION TRIM (WS-CL-ROW-NAME (WS-I)) ": "
                   FUNCTION TRIM (WS-CL-ROW-DESC (WS-I)) X"0A"
                DELIMITED BY SIZE INTO WS-SYS WITH POINTER WS-SP
            END-STRING
        END-PERFORM
    ELSE
        STRING X"0A" DELIMITED BY SIZE INTO WS-SYS WITH POINTER WS-SP
        END-STRING
    END-IF
    STRING
      "- filePlanReference: The file plan reference assigning the "
      "document to its Akte, e.g. 2026/PER/001." X"0A"
        DELIMITED BY SIZE INTO WS-SYS WITH POINTER WS-SP
    END-STRING
    PERFORM PROMPT-ORDN
    PERFORM PROMPT-INTENTS
    COMPUTE WS-SP = WS-SP - 1.

PROMPT-ORDN.
    IF WS-OT-COUNT = 0
        EXIT PARAGRAPH
    END-IF
    STRING
      "- ordnungsbegriffe: JSON array of business reference "
      "identifiers (Ordnungsbegriffe) found in the document. Each "
      "element is an object {""type"": ""<type name>"", ""value"": "
      """<identifier exactly as it appears in the document>""}. "
      "Extract only values matching one of the types listed below; a "
      "document can contain several entries, also several of the same "
      "type. Return an empty array [] when the document contains "
      "none:" X"0A"
        DELIMITED BY SIZE INTO WS-SYS WITH POINTER WS-SP
    END-STRING
    PERFORM VARYING WS-I FROM 1 BY 1 UNTIL WS-I > WS-OT-COUNT
        IF WS-OT-ROW-ACTIVE (WS-I) = "Y"
            STRING "  - " FUNCTION TRIM (WS-OT-ROW-NAME (WS-I)) ": "
                   FUNCTION TRIM (WS-OT-ROW-DESC (WS-I)) X"0A"
                DELIMITED BY SIZE INTO WS-SYS WITH POINTER WS-SP
            END-STRING
        END-IF
    END-PERFORM.

PROMPT-INTENTS.
    IF WS-IN-COUNT = 0
        EXIT PARAGRAPH
    END-IF
    STRING
      "- intent: The processing intent that matches the document "
      "best - exactly one of the intent names listed below, or null "
      "when none fits." X"0A" X"0A"
      "Intents - pick the single best match, return its name under "
      """intent"", and additionally extract only the chosen intent's "
      "fields as top-level JSON keys:" X"0A"
        DELIMITED BY SIZE INTO WS-SYS WITH POINTER WS-SP
    END-STRING
    PERFORM VARYING WS-I FROM 1 BY 1 UNTIL WS-I > WS-IN-COUNT
        STRING "- " FUNCTION TRIM (WS-IN-ROW-NAME (WS-I)) ": "
               FUNCTION TRIM (WS-IN-ROW-DESC (WS-I)) X"0A"
            DELIMITED BY SIZE INTO WS-SYS WITH POINTER WS-SP
        END-STRING
        MOVE WS-IN-ROW-ID (WS-I) TO WS-IF-INTENT
        MOVE "BYIN" TO WS-OP
        CALL "60INFDE0" USING WS-OP WS-RET WS-IF-REC WS-IF-TABLE
        PERFORM VARYING WS-J FROM 1 BY 1 UNTIL WS-J > WS-IF-COUNT
            STRING "  - " FUNCTION TRIM (WS-IF-ROW-NAME (WS-J)) ": "
                   FUNCTION TRIM (WS-IF-ROW-DESC (WS-J)) X"0A"
                DELIMITED BY SIZE INTO WS-SYS WITH POINTER WS-SP
            END-STRING
        END-PERFORM
    END-PERFORM.

*> --- request body -------------------------------------------------
BUILD-BODY.
    MOVE 1 TO WS-BP
    MOVE SPACES TO WS-BODY
    STRING '{"model":"' FUNCTION TRIM (WS-AI-MODEL)
           '","response_format":{"type":"json_object"},'
           '"messages":[{"role":"system","content":"'
        DELIMITED BY SIZE INTO WS-BODY WITH POINTER WS-BP
    END-STRING
    MOVE WS-SYS (1 : WS-SP) TO WS-ESRC
    MOVE WS-SP TO WS-ESRCLEN
    PERFORM APPEND-ESCAPED
    STRING '"},{"role":"user","content":"Analyze the attached '
           'document \"'
        DELIMITED BY SIZE INTO WS-BODY WITH POINTER WS-BP
    END-STRING
    MOVE L-FILENAME TO WS-ESRC
    MOVE FUNCTION STORED-CHAR-LENGTH (L-FILENAME) TO WS-ESRCLEN
    PERFORM APPEND-ESCAPED
    STRING '\" and return the metadata JSON.\n\nDocument content:\n'
        DELIMITED BY SIZE INTO WS-BODY WITH POINTER WS-BP
    END-STRING
    MOVE WS-TEXT (1 : WS-TEXTLEN) TO WS-ESRC
    MOVE WS-TEXTLEN TO WS-ESRCLEN
    PERFORM APPEND-ESCAPED
    STRING '"}]}' DELIMITED BY SIZE
        INTO WS-BODY WITH POINTER WS-BP
    END-STRING
    COMPUTE WS-BODYLEN = WS-BP - 1.

*> append WS-ESRC(1:WS-ESRCLEN) into WS-BODY, JSON-escaping
APPEND-ESCAPED.
    PERFORM VARYING WS-EI FROM 1 BY 1 UNTIL WS-EI > WS-ESRCLEN
        MOVE WS-ESRC (WS-EI : 1) TO WS-EC
        EVALUATE TRUE
            WHEN WS-EC = '"'
                STRING '\"' DELIMITED BY SIZE
                    INTO WS-BODY WITH POINTER WS-BP
                END-STRING
            WHEN WS-EC = "\"
                STRING "\\" DELIMITED BY SIZE
                    INTO WS-BODY WITH POINTER WS-BP
                END-STRING
            WHEN WS-EC = X"0A"
                STRING "\n" DELIMITED BY SIZE
                    INTO WS-BODY WITH POINTER WS-BP
                END-STRING
            WHEN WS-EC = X"0D"
                CONTINUE
            WHEN WS-EC = X"09"
                STRING "\t" DELIMITED BY SIZE
                    INTO WS-BODY WITH POINTER WS-BP
                END-STRING
            WHEN WS-EC < SPACE
                STRING " " DELIMITED BY SIZE
                    INTO WS-BODY WITH POINTER WS-BP
                END-STRING
            WHEN OTHER
                MOVE WS-EC TO WS-BODY (WS-BP : 1)
                ADD 1 TO WS-BP
        END-EVALUATE
    END-PERFORM.

*> --- request logging (DMS_AI_LOG_REQUEST) -------------------------
*> Emit the outgoing chat-completions request in human-readable form:
*> the resolved endpoint/model/headers (bearer token redacted) plus the
*> system and user messages with their real line breaks — i.e. the same
*> content as WS-BODY, but un-escaped and un-minified so it can be read.
LOG-REQUEST.
    DISPLAY "60EXTRC0: ===== AI request (human-readable) for "
        FUNCTION TRIM (L-DOC-ID) " ====="
    DISPLAY "60EXTRC0:   POST " FUNCTION TRIM (WS-AI-URL)
        "/chat/completions"
    DISPLAY "60EXTRC0:   model: " FUNCTION TRIM (WS-AI-MODEL)
    DISPLAY "60EXTRC0:   response_format: json_object"
    DISPLAY "60EXTRC0:   header Content-Type: application/json"
    COMPUTE WS-TOKLEN =
        FUNCTION LENGTH (FUNCTION TRIM (WS-AI-TOKEN))
    DISPLAY "60EXTRC0:   header Authorization: Bearer <redacted, "
        WS-TOKLEN " chars>"
    DISPLAY "60EXTRC0:   body bytes: " WS-BODYLEN
        " (OCR text " WS-TEXTLEN " bytes)"
    DISPLAY "60EXTRC0:   --- message[0] role=system ---"
    DISPLAY WS-SYS (1 : WS-SP)
    DISPLAY "60EXTRC0:   --- message[1] role=user ---"
    DISPLAY 'Analyze the attached document "'
        FUNCTION TRIM (L-FILENAME)
        '" and return the metadata JSON.'
    DISPLAY " "
    DISPLAY "Document content:"
    DISPLAY WS-TEXT (1 : WS-TEXTLEN)
    DISPLAY "60EXTRC0: ===== end AI request for "
        FUNCTION TRIM (L-DOC-ID) " =====".

*> --- response: pull choices[0].message.content --------------------
EXTRACT-CONTENT.
    MOVE 0 TO WS-ANSWER-LEN
    MOVE SPACES TO WS-ANSWER
    *> locate the "content" key in the raw response
    MOVE 0 TO WS-SCAN
    PERFORM VARYING WS-EI FROM 1 BY 1
            UNTIL WS-EI + 9 > WS-RESPLEN
        IF WS-RESP (WS-EI : 9) = '"content"'
            COMPUTE WS-SCAN = WS-EI + 9
            EXIT PERFORM
        END-IF
    END-PERFORM
    IF WS-SCAN = 0
        EXIT PARAGRAPH
    END-IF
    *> skip to the opening quote of the value
    PERFORM UNTIL WS-SCAN > WS-RESPLEN
            OR WS-RESP (WS-SCAN : 1) = '"'
        ADD 1 TO WS-SCAN
    END-PERFORM
    IF WS-SCAN > WS-RESPLEN
        EXIT PARAGRAPH
    END-IF
    ADD 1 TO WS-SCAN
    *> copy with unescaping until the closing quote
    PERFORM UNTIL WS-SCAN > WS-RESPLEN
            OR WS-RESP (WS-SCAN : 1) = '"'
            OR WS-ANSWER-LEN >= 4096
        MOVE WS-RESP (WS-SCAN : 1) TO WS-CH
        IF WS-CH = "\" AND WS-SCAN < WS-RESPLEN
            ADD 1 TO WS-SCAN
            MOVE WS-RESP (WS-SCAN : 1) TO WS-CH
            EVALUATE WS-CH
                WHEN "n" MOVE X"0A" TO WS-CH
                WHEN "t" MOVE X"09" TO WS-CH
                WHEN "r" MOVE X"0D" TO WS-CH
                WHEN OTHER CONTINUE
            END-EVALUATE
        END-IF
        ADD 1 TO WS-ANSWER-LEN
        MOVE WS-CH TO WS-ANSWER (WS-ANSWER-LEN : 1)
        ADD 1 TO WS-SCAN
    END-PERFORM.

*> --- parse the answer JSON ----------------------------------------
PARSE-ANSWER.
    MOVE WS-ANSWER TO WS-A4K
    MOVE "documentClass" TO WS-KEY
    PERFORM JSON-GET
    MOVE WS-VAL TO WS-SUG-CLASS
    MOVE "documentDate" TO WS-KEY
    PERFORM JSON-GET
    MOVE WS-VAL TO WS-SUG-DATE
    MOVE "filePlanReference" TO WS-KEY
    PERFORM JSON-GET
    MOVE WS-VAL TO WS-SUG-FPR
    MOVE "intent" TO WS-KEY
    PERFORM JSON-GET
    MOVE WS-VAL TO WS-SUG-INTENT
    PERFORM BUILD-INTENT-FIELDS
    PERFORM PARSE-ORDN.

JSON-GET.
    MOVE SPACES TO WS-VAL
    CALL "00JSONC0" USING WS-A4K WS-KEY WS-VAL WS-FOUND.

*> the chosen intent's fields, as a compact JSON object for DOCINTNT
BUILD-INTENT-FIELDS.
    MOVE SPACES TO WS-DI-FIELDS
    IF FUNCTION TRIM (WS-SUG-INTENT) = SPACES
        EXIT PARAGRAPH
    END-IF
    *> find the intent id by name
    MOVE SPACES TO WS-IF-INTENT
    PERFORM VARYING WS-I FROM 1 BY 1 UNTIL WS-I > WS-IN-COUNT
        IF FUNCTION TRIM (WS-IN-ROW-NAME (WS-I))
                = FUNCTION TRIM (WS-SUG-INTENT)
            MOVE WS-IN-ROW-ID (WS-I) TO WS-IF-INTENT
        END-IF
    END-PERFORM
    IF WS-IF-INTENT = SPACES
        EXIT PARAGRAPH
    END-IF
    MOVE "BYIN" TO WS-OP
    CALL "60INFDE0" USING WS-OP WS-RET WS-IF-REC WS-IF-TABLE
    MOVE 1 TO WS-FP2
    MOVE "{" TO WS-INT-FIELDS (WS-FP2 : 1)
    ADD 1 TO WS-FP2
    MOVE "N" TO WS-FIRSTF
    PERFORM VARYING WS-J FROM 1 BY 1 UNTIL WS-J > WS-IF-COUNT
        MOVE FUNCTION TRIM (WS-IF-ROW-NAME (WS-J)) TO WS-KEY
        PERFORM JSON-GET
        IF WS-FOUND = "Y"
            IF WS-FIRSTF = "Y"
                MOVE "," TO WS-INT-FIELDS (WS-FP2 : 1)
                ADD 1 TO WS-FP2
            END-IF
            MOVE "Y" TO WS-FIRSTF
            STRING '"' FUNCTION TRIM (WS-IF-ROW-NAME (WS-J)) '":"'
                   FUNCTION TRIM (WS-VAL) '"'
                DELIMITED BY SIZE INTO WS-INT-FIELDS
                WITH POINTER WS-FP2
            END-STRING
        END-IF
    END-PERFORM
    MOVE "}" TO WS-INT-FIELDS (WS-FP2 : 1)
    IF WS-FIRSTF = "N"
        MOVE SPACES TO WS-INT-FIELDS
    END-IF.

*> three-valued ordnungsbegriffe: LIST / EMPTY / BAD (malformed)
PARSE-ORDN.
    MOVE "EMPTY" TO WS-ORDN-STATE
    MOVE 0 TO WS-ORDN-COUNT
    MOVE 0 TO WS-SCAN
    PERFORM VARYING WS-EI FROM 1 BY 1
            UNTIL WS-EI + 18 > WS-ANSWER-LEN
        IF WS-ANSWER (WS-EI : 18) = '"ordnungsbegriffe"'
            COMPUTE WS-SCAN = WS-EI + 18
            EXIT PERFORM
        END-IF
    END-PERFORM
    IF WS-SCAN = 0
        EXIT PARAGRAPH
    END-IF
    PERFORM UNTIL WS-SCAN > WS-ANSWER-LEN
            OR WS-ANSWER (WS-SCAN : 1) NOT = SPACE
        ADD 1 TO WS-SCAN
    END-PERFORM
    IF WS-SCAN > WS-ANSWER-LEN OR WS-ANSWER (WS-SCAN : 1) = ":"
        ADD 1 TO WS-SCAN
    END-IF
    PERFORM UNTIL WS-SCAN > WS-ANSWER-LEN
            OR WS-ANSWER (WS-SCAN : 1) NOT = SPACE
        ADD 1 TO WS-SCAN
    END-PERFORM
    IF WS-SCAN > WS-ANSWER-LEN
        EXIT PARAGRAPH
    END-IF
    IF WS-ANSWER (WS-SCAN : 1) = "["
        MOVE "LIST" TO WS-ORDN-STATE
        PERFORM PARSE-ORDN-ARRAY
    ELSE
        IF WS-ANSWER (WS-SCAN : 4) = "null"
            MOVE "EMPTY" TO WS-ORDN-STATE
        ELSE
            MOVE "BAD" TO WS-ORDN-STATE
        END-IF
    END-IF.

PARSE-ORDN-ARRAY.
    *> WS-SCAN points at '['; walk objects until matching ']'
    ADD 1 TO WS-SCAN
    PERFORM UNTIL WS-SCAN > WS-ANSWER-LEN
            OR WS-ANSWER (WS-SCAN : 1) = "]"
            OR WS-ORDN-COUNT >= 20
        IF WS-ANSWER (WS-SCAN : 1) = "{"
            PERFORM CAPTURE-OBJECT
            PERFORM PARSE-ONE-ORDN
        ELSE
            ADD 1 TO WS-SCAN
        END-IF
    END-PERFORM.

CAPTURE-OBJECT.
    *> copy WS-ANSWER from the '{' to its matching '}' into WS-OBJ
    MOVE SPACES TO WS-OBJ
    MOVE 0 TO WS-OBJLEN
    MOVE 0 TO WS-DEPTH
    PERFORM UNTIL WS-SCAN > WS-ANSWER-LEN
        MOVE WS-ANSWER (WS-SCAN : 1) TO WS-CH
        IF WS-OBJLEN < 512
            ADD 1 TO WS-OBJLEN
            MOVE WS-CH TO WS-OBJ (WS-OBJLEN : 1)
        END-IF
        IF WS-CH = "{"
            ADD 1 TO WS-DEPTH
        END-IF
        IF WS-CH = "}"
            SUBTRACT 1 FROM WS-DEPTH
            IF WS-DEPTH = 0
                ADD 1 TO WS-SCAN
                EXIT PERFORM
            END-IF
        END-IF
        ADD 1 TO WS-SCAN
    END-PERFORM.

PARSE-ONE-ORDN.
    MOVE WS-OBJ TO WS-A4K
    MOVE "type" TO WS-KEY
    PERFORM JSON-GET
    IF WS-FOUND NOT = "Y" OR FUNCTION TRIM (WS-VAL) = SPACES
        EXIT PARAGRAPH
    END-IF
    MOVE WS-VAL TO WS-CANON
    *> canonicalize against an active catalog type (case-insensitive)
    MOVE "N" TO WS-MATCH
    PERFORM VARYING WS-K FROM 1 BY 1 UNTIL WS-K > WS-OT-COUNT
        IF WS-OT-ROW-ACTIVE (WS-K) = "Y"
          AND FUNCTION UPPER-CASE (FUNCTION TRIM (WS-OT-ROW-NAME (WS-K)))
              = FUNCTION UPPER-CASE (FUNCTION TRIM (WS-CANON))
            MOVE WS-OT-ROW-NAME (WS-K) TO WS-CANON
            MOVE "Y" TO WS-MATCH
        END-IF
    END-PERFORM
    IF WS-MATCH NOT = "Y"
        EXIT PARAGRAPH
    END-IF
    MOVE "value" TO WS-KEY
    PERFORM JSON-GET
    IF WS-FOUND NOT = "Y" OR FUNCTION TRIM (WS-VAL) = SPACES
        EXIT PARAGRAPH
    END-IF
    ADD 1 TO WS-ORDN-COUNT
    MOVE WS-CANON TO WS-ORDN-TYPE (WS-ORDN-COUNT)
    MOVE FUNCTION TRIM (WS-VAL) TO WS-ORDN-VALUE (WS-ORDN-COUNT).

*> --- apply (port of applySuggestions) -----------------------------
APPLY-SUGGESTIONS.
    CALL "00UUIDC0" USING WS-UUID WS-EPOCH
    PERFORM COMPUTE-FLAG
    PERFORM APPLY-METADATA
    PERFORM APPLY-ORDN
    PERFORM APPLY-FPR
    PERFORM APPLY-INTENT.

COMPUTE-FLAG.
    EVALUATE TRUE
        WHEN WS-ORDN-STATE = "BAD"
            MOVE "REVIEW" TO WS-FLAG
        WHEN WS-ORDN-COUNT = 0
            MOVE "MANUAL_INDEXING" TO WS-FLAG
        WHEN OTHER
            MOVE SPACES TO WS-FLAG
    END-EVALUATE.

APPLY-METADATA.
    *> prefill only when no metadata row exists yet
    MOVE SPACES TO WS-ME-REC
    MOVE L-DOC-ID TO WS-ME-DOC
    MOVE "GET " TO WS-OP
    CALL "30METAE0" USING WS-OP WS-RET WS-ME-REC
    IF WS-RET = "00"
        EXIT PARAGRAPH
    END-IF
    PERFORM NORMALIZE-CLASS
    MOVE "N" TO WS-DATE-OK
    IF FUNCTION TRIM (WS-SUG-DATE) NOT = SPACES
        MOVE "Y" TO WS-DATE-OK
    END-IF
    IF WS-DATE-OK = "N" AND WS-NORM-CLASS = SPACES
            AND WS-FLAG = SPACES
        EXIT PARAGRAPH
    END-IF
    MOVE SPACES TO WS-ME-REC
    MOVE L-DOC-ID TO WS-ME-DOC
    IF WS-DATE-OK = "Y"
        MOVE WS-SUG-DATE (1 : 10) TO WS-ME-DATE
    END-IF
    MOVE WS-NORM-CLASS TO WS-ME-CLASS
    MOVE "Y" TO WS-ME-EXTRACTED
    MOVE WS-FLAG TO WS-ME-FLAG
    MOVE 0 TO WS-ME-VERSION
    MOVE WS-EPOCH TO WS-ME-CREATED
    MOVE WS-EPOCH TO WS-ME-UPDATED
    MOVE "WRT " TO WS-OP
    CALL "30METAE0" USING WS-OP WS-RET WS-ME-REC.

NORMALIZE-CLASS.
    MOVE SPACES TO WS-NORM-CLASS
    IF FUNCTION TRIM (WS-SUG-CLASS) = SPACES
        EXIT PARAGRAPH
    END-IF
    MOVE SPACES TO WS-CL-REC
    MOVE FUNCTION UPPER-CASE (FUNCTION TRIM (WS-SUG-CLASS))
        TO WS-CL-NAME
    MOVE "NAME" TO WS-OP
    CALL "30CLASE0" USING WS-OP WS-RET WS-CL-REC WS-CL-TABLE
    IF WS-RET = "00"
        MOVE WS-CL-NAME TO WS-NORM-CLASS
    END-IF.

APPLY-ORDN.
    IF WS-ORDN-COUNT = 0
        EXIT PARAGRAPH
    END-IF
    MOVE SPACES TO WS-OB-REC
    MOVE L-DOC-ID TO WS-OB-DOC
    MOVE "BYDC" TO WS-OP
    CALL "30ORDBE0" USING WS-OP WS-RET WS-OB-REC WS-OB-TABLE
    IF WS-OB-COUNT > 0
        EXIT PARAGRAPH
    END-IF
    PERFORM VARYING WS-I FROM 1 BY 1 UNTIL WS-I > WS-ORDN-COUNT
        CALL "00UUIDC0" USING WS-UUID WS-EPOCH
        MOVE SPACES TO WS-OB-REC
        MOVE WS-UUID TO WS-OB-ID
        MOVE L-DOC-ID TO WS-OB-DOC
        MOVE WS-ORDN-TYPE (WS-I) TO WS-OB-TYPE
        MOVE WS-ORDN-VALUE (WS-I) TO WS-OB-VALUE
        MOVE "Y" TO WS-OB-EXTRACTED
        MOVE WS-EPOCH TO WS-OB-CREATED
        MOVE "WRT " TO WS-OP
        CALL "30ORDBE0" USING WS-OP WS-RET WS-OB-REC WS-OB-TABLE
    END-PERFORM.

APPLY-FPR.
    IF FUNCTION TRIM (WS-SUG-FPR) = SPACES
        EXIT PARAGRAPH
    END-IF
    MOVE SPACES TO WS-FR-REC
    MOVE L-DOC-ID TO WS-FR-DOC
    MOVE "GET " TO WS-OP
    CALL "30FPRFE0" USING WS-OP WS-RET WS-FR-REC WS-FR-TABLE
    IF WS-RET = "00"
        EXIT PARAGRAPH
    END-IF
    MOVE SPACES TO WS-FR-REC
    MOVE L-DOC-ID TO WS-FR-DOC
    MOVE FUNCTION TRIM (WS-SUG-FPR) TO WS-FR-FPR
    MOVE SPACES TO WS-FR-AKTE
    MOVE "Y" TO WS-FR-EXTRACTED
    MOVE 0 TO WS-FR-VERSION
    MOVE WS-EPOCH TO WS-FR-CREATED
    MOVE "WRT " TO WS-OP
    CALL "30FPRFE0" USING WS-OP WS-RET WS-FR-REC WS-FR-TABLE.

APPLY-INTENT.
    *> AI-only data: replace any existing row (or remove when none)
    MOVE SPACES TO WS-DI-REC
    MOVE L-DOC-ID TO WS-DI-DOC
    MOVE "DEL " TO WS-OP
    CALL "30INTNE0" USING WS-OP WS-RET WS-DI-REC
    IF FUNCTION TRIM (WS-SUG-INTENT) = SPACES
        EXIT PARAGRAPH
    END-IF
    MOVE SPACES TO WS-DI-REC
    MOVE L-DOC-ID TO WS-DI-DOC
    MOVE FUNCTION TRIM (WS-SUG-INTENT) TO WS-DI-NAME
    MOVE WS-INT-FIELDS TO WS-DI-FIELDS
    MOVE WS-EPOCH TO WS-DI-CREATED
    MOVE "WRT " TO WS-OP
    CALL "30INTNE0" USING WS-OP WS-RET WS-DI-REC.

*> --- flag-only path (unconfigured / no text / errored) ------------
FLAG-FOR-INDEXING.
    CALL "00UUIDC0" USING WS-UUID WS-EPOCH
    MOVE SPACES TO WS-ME-REC
    MOVE L-DOC-ID TO WS-ME-DOC
    MOVE "GET " TO WS-OP
    CALL "30METAE0" USING WS-OP WS-RET WS-ME-REC
    IF WS-RET = "00"
        IF WS-ME-VERSION = 0 AND FUNCTION TRIM (WS-ME-FLAG) = SPACES
            MOVE WS-FLAG TO WS-ME-FLAG
            MOVE "REW " TO WS-OP
            CALL "30METAE0" USING WS-OP WS-RET WS-ME-REC
        END-IF
    ELSE
        MOVE SPACES TO WS-ME-REC
        MOVE L-DOC-ID TO WS-ME-DOC
        MOVE "N" TO WS-ME-EXTRACTED
        MOVE WS-FLAG TO WS-ME-FLAG
        MOVE 0 TO WS-ME-VERSION
        MOVE WS-EPOCH TO WS-ME-CREATED
        MOVE WS-EPOCH TO WS-ME-UPDATED
        MOVE "WRT " TO WS-OP
        CALL "30METAE0" USING WS-OP WS-RET WS-ME-REC
    END-IF.
END PROGRAM "60EXTRC0".
