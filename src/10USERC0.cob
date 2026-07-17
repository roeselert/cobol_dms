>>SOURCE FORMAT FREE
*> 10USERC0 — security: resolve the current user from the exchange's
*> raw identity (set by the dispatcher from the auth environment) and
*> provision it just-in-time, mirroring UserProvisioning/CurrentUser:
*> unknown email -> new ACTIVE user; INVITED -> ACTIVE on first login.
*> Sets HX-USER-ID / normalized HX-USER-EMAIL / HX-USER-ADMIN.
*> L-RET: 00 ok · 41 unauthenticated (no identity) · 9x file error.
IDENTIFICATION DIVISION.
PROGRAM-ID. "10USERC0".
DATA DIVISION.
WORKING-STORAGE SECTION.
01 WS-EMAIL             PIC X(200).
01 WS-ADMINS            PIC X(1024).
01 WS-ADMIN-ENTRY       PIC X(200) OCCURS 20.
01 WS-I                 PIC 9(4) COMP.
01 WS-OP                PIC X(4).
01 WS-RET               PIC X(2).
COPY "20USRSR0.cpy" REPLACING ==:PFX:== BY ==WS-US==.
01 WS-UUID              PIC X(36).
01 WS-EPOCH             PIC 9(13).
LINKAGE SECTION.
COPY "00HTTPR0.cpy".
01 L-RET                PIC X(2).
PROCEDURE DIVISION USING HTTP-EXCHANGE L-RET.
MAIN.
    MOVE "00" TO L-RET
    MOVE FUNCTION LOWER-CASE (FUNCTION TRIM (HX-USER-EMAIL))
        TO WS-EMAIL
    IF WS-EMAIL = SPACES
        MOVE "41" TO L-RET
        GOBACK
    END-IF
    MOVE WS-EMAIL TO HX-USER-EMAIL

    MOVE SPACES TO WS-US-REC
    MOVE WS-EMAIL TO WS-US-EMAIL
    MOVE "EML " TO WS-OP
    CALL "20USRSE0" USING WS-OP WS-RET WS-US-REC
    EVALUATE WS-RET
        WHEN "00"
            IF WS-US-STATUS = "INVITED"
                MOVE "ACTIVE" TO WS-US-STATUS
                MOVE "REW " TO WS-OP
                CALL "20USRSE0" USING WS-OP WS-RET WS-US-REC
            END-IF
        WHEN "23"
            CALL "00UUIDC0" USING WS-UUID WS-EPOCH
            MOVE SPACES   TO WS-US-REC
            MOVE WS-UUID  TO WS-US-ID
            MOVE WS-EMAIL TO WS-US-EMAIL
            MOVE "ACTIVE" TO WS-US-STATUS
            MOVE "WRT " TO WS-OP
            CALL "20USRSE0" USING WS-OP WS-RET WS-US-REC
            IF WS-RET = "22"
                *> lost a concurrent-provisioning race: re-read
                MOVE "EML " TO WS-OP
                CALL "20USRSE0" USING WS-OP WS-RET WS-US-REC
            END-IF
        WHEN OTHER
            MOVE "91" TO L-RET
            GOBACK
    END-EVALUATE
    MOVE WS-US-ID TO HX-USER-ID

    PERFORM RESOLVE-BOOTSTRAP-ADMIN
    GOBACK.

RESOLVE-BOOTSTRAP-ADMIN.
    MOVE "N" TO HX-USER-ADMIN
    ACCEPT WS-ADMINS FROM ENVIRONMENT "DMS_BOOTSTRAP_ADMINS"
        ON EXCEPTION MOVE SPACES TO WS-ADMINS
    END-ACCEPT
    IF WS-ADMINS = SPACES
        *> mirrors the as-is default (application.yml)
        MOVE "admin@example.com" TO WS-ADMINS
    END-IF
    INITIALIZE WS-ADMIN-ENTRY (1) WS-ADMIN-ENTRY (2)
    UNSTRING WS-ADMINS DELIMITED BY ","
        INTO WS-ADMIN-ENTRY (1)  WS-ADMIN-ENTRY (2)
             WS-ADMIN-ENTRY (3)  WS-ADMIN-ENTRY (4)
             WS-ADMIN-ENTRY (5)  WS-ADMIN-ENTRY (6)
             WS-ADMIN-ENTRY (7)  WS-ADMIN-ENTRY (8)
             WS-ADMIN-ENTRY (9)  WS-ADMIN-ENTRY (10)
             WS-ADMIN-ENTRY (11) WS-ADMIN-ENTRY (12)
             WS-ADMIN-ENTRY (13) WS-ADMIN-ENTRY (14)
             WS-ADMIN-ENTRY (15) WS-ADMIN-ENTRY (16)
             WS-ADMIN-ENTRY (17) WS-ADMIN-ENTRY (18)
             WS-ADMIN-ENTRY (19) WS-ADMIN-ENTRY (20)
    END-UNSTRING
    PERFORM VARYING WS-I FROM 1 BY 1 UNTIL WS-I > 20
        IF FUNCTION LOWER-CASE (FUNCTION TRIM (WS-ADMIN-ENTRY (WS-I)))
                = WS-EMAIL
            MOVE "Y" TO HX-USER-ADMIN
        END-IF
    END-PERFORM.
END PROGRAM "10USERC0".
