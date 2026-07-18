>>SOURCE FORMAT FREE
*> 30CONFB0 — documents: REST boundary for /api/v1/config, mirroring
*> ConfigController. Any authenticated user. documentClasses is the
*> list of class names (sorted); aiEnabled reflects whether an AI
*> provider is configured — in the COBOL target that is the presence
*> of DMS_AI_TOKEN (the extraction service disappears; the LLM is
*> called in-process via libcurl, iteration 6), the adaptation of the
*> as-is "extraction URL configured" flag (noted in TARGET-ARCHITECTURE).
IDENTIFICATION DIVISION.
PROGRAM-ID. "30CONFB0".
DATA DIVISION.
WORKING-STORAGE SECTION.
01 WS-V-OP              PIC X(4).
01 WS-V-STATUS          PIC X(3).
01 WS-V-MSG             PIC X(200).
01 WS-V-NAME            PIC X(200).
01 WS-V-DESC            PIC X(200).
COPY "30CLASR0.cpy" REPLACING ==:PFX:== BY ==WS-CL==.
01 WS-CL-TABLE.
   05 WS-CL-COUNT       PIC 9(4).
   05 WS-CL-ROW OCCURS 100.
      10 WS-CL-ROW-ID   PIC X(36).
      10 WS-CL-ROW-NAME PIC X(50).
      10 WS-CL-ROW-DESC PIC X(200).
01 WS-AI-TOKEN          PIC X(200).
01 WS-PTR               PIC 9(6) COMP.
01 WS-I                 PIC 9(4) COMP.
LINKAGE SECTION.
COPY "00HTTPR0.cpy".
PROCEDURE DIVISION USING HTTP-EXCHANGE.
MAIN.
    IF HX-METHOD NOT = "GET"
        MOVE 405 TO HX-STATUS
        MOVE '{"error":"method not allowed"}' TO HX-RESPONSE
        GOBACK
    END-IF
    MOVE "LIST" TO WS-V-OP
    CALL "30VOCAC0" USING WS-V-OP WS-V-STATUS WS-V-MSG WS-V-NAME
        WS-V-DESC WS-CL-REC WS-CL-TABLE
    MOVE 1 TO WS-PTR
    MOVE SPACES TO HX-RESPONSE
    STRING '{"documentClasses":[' DELIMITED BY SIZE
        INTO HX-RESPONSE WITH POINTER WS-PTR
    END-STRING
    PERFORM VARYING WS-I FROM 1 BY 1 UNTIL WS-I > WS-CL-COUNT
        IF WS-I > 1
            STRING "," DELIMITED BY SIZE
                INTO HX-RESPONSE WITH POINTER WS-PTR
            END-STRING
        END-IF
        STRING '"' FUNCTION TRIM (WS-CL-ROW-NAME (WS-I)) '"'
            DELIMITED BY SIZE INTO HX-RESPONSE WITH POINTER WS-PTR
        END-STRING
    END-PERFORM
    STRING '],"aiEnabled":' DELIMITED BY SIZE
        INTO HX-RESPONSE WITH POINTER WS-PTR
    END-STRING
    ACCEPT WS-AI-TOKEN FROM ENVIRONMENT "DMS_AI_TOKEN"
        ON EXCEPTION MOVE SPACES TO WS-AI-TOKEN
    END-ACCEPT
    IF FUNCTION TRIM (WS-AI-TOKEN) = SPACES
        STRING "false}" DELIMITED BY SIZE
            INTO HX-RESPONSE WITH POINTER WS-PTR
        END-STRING
    ELSE
        STRING "true}" DELIMITED BY SIZE
            INTO HX-RESPONSE WITH POINTER WS-PTR
        END-STRING
    END-IF
    MOVE 200 TO HX-STATUS
    GOBACK.
END PROGRAM "30CONFB0".
