>>SOURCE FORMAT FREE
*> 00TIMEC0 — platform: epoch-milliseconds -> ISO-8601 instant string
*> ("YYYY-MM-DDThh:mm:ssZ"), the wire format of ingestDate/createdAt
*> fields (Instant.toString in the as-is system; seconds precision).
IDENTIFICATION DIVISION.
PROGRAM-ID. "00TIMEC0".
DATA DIVISION.
WORKING-STORAGE SECTION.
01 WS-EPOCH-SEC         PIC 9(13).
01 WS-DAYS              PIC S9(9) COMP.
01 WS-SECS              PIC 9(6).
01 WS-DATE-INT          PIC 9(8).
01 WS-DATE-R REDEFINES WS-DATE-INT.
   05 WS-YYYY           PIC 9(4).
   05 WS-MM             PIC 9(2).
   05 WS-DD             PIC 9(2).
01 WS-HH                PIC 9(2).
01 WS-MI                PIC 9(2).
01 WS-SS                PIC 9(2).
LINKAGE SECTION.
01 L-EPOCH-MS           PIC 9(13).
01 L-ISO                PIC X(20).
PROCEDURE DIVISION USING L-EPOCH-MS L-ISO.
MAIN.
    COMPUTE WS-EPOCH-SEC = L-EPOCH-MS / 1000
    COMPUTE WS-DAYS = WS-EPOCH-SEC / 86400
    COMPUTE WS-SECS = WS-EPOCH-SEC - WS-DAYS * 86400
    COMPUTE WS-DATE-INT = FUNCTION DATE-OF-INTEGER (
        WS-DAYS + FUNCTION INTEGER-OF-DATE (19700101))
    COMPUTE WS-HH = WS-SECS / 3600
    COMPUTE WS-MI = FUNCTION MOD (WS-SECS / 60, 60)
    COMPUTE WS-SS = FUNCTION MOD (WS-SECS, 60)
    STRING WS-YYYY "-" WS-MM "-" WS-DD "T"
           WS-HH ":" WS-MI ":" WS-SS "Z"
        DELIMITED BY SIZE INTO L-ISO
    END-STRING
    GOBACK.
END PROGRAM "00TIMEC0".
