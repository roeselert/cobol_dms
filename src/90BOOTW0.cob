>>SOURCE FORMAT FREE
*> 90BOOTW0 — operations: container bootstrap. Ensures every VSAM
*> indexed file exists before Apache starts serving CGI requests (the
*> entity programs auto-create on status 35; probing them here removes
*> the concurrent-first-request race) and writes one system audit row
*> marking the boot. Runs as the same user as the CGI processes.
IDENTIFICATION DIVISION.
PROGRAM-ID. "90BOOTW0".
DATA DIVISION.
WORKING-STORAGE SECTION.
01 WS-OP                PIC X(4).
01 WS-RET               PIC X(2).
01 WS-PROBE-ID          PIC X(36)
       VALUE "00000000-0000-0000-0000-000000000000".
COPY "20ORGSR0.cpy" REPLACING ==:PFX:== BY ==WS-OU==.
01 WS-OU-TABLE.
   05 WS-OU-COUNT       PIC 9(4).
   05 WS-OU-ROW OCCURS 300.
      10 WS-OU-ROW-ID     PIC X(36).
      10 WS-OU-ROW-NAME   PIC X(200).
      10 WS-OU-ROW-PARENT PIC X(36).
      10 WS-OU-ROW-PATH   PIC X(512).
COPY "20USRSR0.cpy" REPLACING ==:PFX:== BY ==WS-US==.
COPY "20MEMBR0.cpy" REPLACING ==:PFX:== BY ==WS-MB==.
01 WS-MB-TABLE.
   05 WS-MB-COUNT       PIC 9(4).
   05 WS-MB-ROW OCCURS 300.
      10 WS-MB-ROW-ID   PIC X(36).
      10 WS-MB-ROW-USER PIC X(36).
      10 WS-MB-ROW-ORG  PIC X(36).
      10 WS-MB-ROW-ROLE PIC X(10).
01 WS-AUD-USER          PIC X(36) VALUE "system".
01 WS-AUD-ACTION        PIC X(10) VALUE "BOOT".
01 WS-AUD-RTYPE         PIC X(20) VALUE "system".
01 WS-AUD-RID           PIC X(36) VALUE SPACES.
01 WS-AUD-EFFECT        PIC X(5)  VALUE "ALLOW".
01 WS-FAILED            PIC X VALUE "N".
PROCEDURE DIVISION.
MAIN.
    DISPLAY "90BOOTW0: ensuring VSAM files"
    MOVE "GET " TO WS-OP

    MOVE SPACES TO WS-OU-REC
    MOVE WS-PROBE-ID TO WS-OU-ID
    CALL "20ORGSE0" USING WS-OP WS-RET WS-OU-REC WS-OU-TABLE
    PERFORM CHECK-PROBE

    MOVE SPACES TO WS-US-REC
    MOVE WS-PROBE-ID TO WS-US-ID
    CALL "20USRSE0" USING WS-OP WS-RET WS-US-REC
    PERFORM CHECK-PROBE

    MOVE SPACES TO WS-MB-REC
    MOVE WS-PROBE-ID TO WS-MB-ID
    CALL "20MEMBE0" USING WS-OP WS-RET WS-MB-REC WS-MB-TABLE
    PERFORM CHECK-PROBE

    CALL "10AUDTE0" USING WS-AUD-USER WS-AUD-ACTION WS-AUD-RTYPE
                          WS-AUD-RID WS-AUD-EFFECT

    IF WS-FAILED = "Y"
        DISPLAY "90BOOTW0: FAILED — data directory not writable?"
        MOVE 1 TO RETURN-CODE
    ELSE
        DISPLAY "90BOOTW0: ok"
    END-IF
    STOP RUN.

CHECK-PROBE.
    IF WS-RET NOT = "00" AND WS-RET NOT = "23"
        DISPLAY "90BOOTW0: probe failed, ret=" WS-RET
        MOVE "Y" TO WS-FAILED
    END-IF.
END PROGRAM "90BOOTW0".
