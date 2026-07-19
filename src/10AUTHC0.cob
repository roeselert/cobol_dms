>>SOURCE FORMAT FREE
*> 10AUTHC0 — security: single authorization chokepoint, mirroring
*> accesscontrol.Authorization. Resolves the caller's effective role on
*> an org unit (highest role granted on the unit or any ancestor via
*> the materialized path) and evaluates it against the needed role.
*>   A-RESULT: A allow · N not-found (existence-hiding 404) · F forbidden
*> Bootstrap admins are ADMIN everywhere without existence check —
*> exactly like the as-is effectiveRole(). Decisions are audited via
*> 10AUDTE0 when A-AUDIT = "Y" (list filtering passes "N").
IDENTIFICATION DIVISION.
PROGRAM-ID. "10AUTHC0".
DATA DIVISION.
WORKING-STORAGE SECTION.
01 WS-OP                PIC X(4).
01 WS-RET               PIC X(2).
COPY "20ORGSR0.cpy" REPLACING ==:PFX:== BY ==WS-OU==.
01 WS-OU-TABLE.
   05 WS-OU-COUNT       PIC 9(4).
   05 WS-OU-ROW OCCURS 300.
      10 WS-OU-ROW-ID     PIC X(36).
      10 WS-OU-ROW-NAME   PIC X(200).
      10 WS-OU-ROW-PARENT PIC X(36).
      10 WS-OU-ROW-PATH   PIC X(512).
COPY "20MEMBR0.cpy" REPLACING ==:PFX:== BY ==WS-MB==.
01 WS-MB-TABLE.
   05 WS-MB-COUNT       PIC 9(4).
   05 WS-MB-ROW OCCURS 300.
      10 WS-MB-ROW-ID   PIC X(36).
      10 WS-MB-ROW-USER PIC X(36).
      10 WS-MB-ROW-ORG  PIC X(36).
      10 WS-MB-ROW-ROLE PIC X(10).
01 WS-RES-PATH          PIC X(512).
01 WS-GRANT-PATH        PIC X(512).
01 WS-GRANT-LEN         PIC 9(4) COMP.
01 WS-I                 PIC 9(4) COMP.
01 WS-BEST              PIC 9.
01 WS-NEED              PIC 9.
01 WS-ROLE-RANK         PIC 9.
01 WS-EFFECT            PIC X(5).
LINKAGE SECTION.
01 A-USER-ID            PIC X(36).
01 A-ADMIN              PIC X.
01 A-ORG-ID             PIC X(36).
*> the audited resource id (document id for DOCUMENT decisions, the
*> org-unit id itself for ORG_UNIT decisions)
01 A-RID                PIC X(36).
01 A-NEEDED             PIC X.
01 A-ACTION             PIC X(10).
01 A-RTYPE              PIC X(20).
01 A-AUDIT              PIC X.
01 A-RESULT             PIC X.
PROCEDURE DIVISION USING A-USER-ID A-ADMIN A-ORG-ID A-RID A-NEEDED
                         A-ACTION A-RTYPE A-AUDIT A-RESULT.
MAIN.
    IF A-ADMIN = "Y"
        MOVE "A" TO A-RESULT
        PERFORM AUDIT-DECISION
        GOBACK
    END-IF
    PERFORM NEEDED-RANK
    *> path of the requested resource; unknown unit => hidden 404
    MOVE SPACES TO WS-OU-REC
    MOVE A-ORG-ID TO WS-OU-ID
    MOVE "GET " TO WS-OP
    CALL "20ORGSE0" USING WS-OP WS-RET WS-OU-REC WS-OU-TABLE
    IF WS-RET NOT = "00"
        MOVE "N" TO A-RESULT
        PERFORM AUDIT-DECISION
        GOBACK
    END-IF
    MOVE WS-OU-PATH TO WS-RES-PATH
    *> highest role granted on the unit or an ancestor
    MOVE 0 TO WS-BEST
    MOVE SPACES TO WS-MB-REC
    MOVE A-USER-ID TO WS-MB-USER
    MOVE "USR " TO WS-OP
    CALL "20MEMBE0" USING WS-OP WS-RET WS-MB-REC WS-MB-TABLE
    PERFORM VARYING WS-I FROM 1 BY 1 UNTIL WS-I > WS-MB-COUNT
        PERFORM CHECK-GRANT
    END-PERFORM
    EVALUATE TRUE
        WHEN WS-BEST = 0
            MOVE "N" TO A-RESULT
        WHEN WS-BEST < WS-NEED
            MOVE "F" TO A-RESULT
        WHEN OTHER
            MOVE "A" TO A-RESULT
    END-EVALUATE
    PERFORM AUDIT-DECISION
    GOBACK.

CHECK-GRANT.
    MOVE SPACES TO WS-OU-REC
    MOVE WS-MB-ROW-ORG (WS-I) TO WS-OU-ID
    MOVE "GET " TO WS-OP
    CALL "20ORGSE0" USING WS-OP WS-RET WS-OU-REC WS-OU-TABLE
    IF WS-RET = "00"
        MOVE WS-OU-PATH TO WS-GRANT-PATH
        MOVE FUNCTION STORED-CHAR-LENGTH (WS-GRANT-PATH)
            TO WS-GRANT-LEN
        IF WS-GRANT-LEN > 0
            AND WS-RES-PATH (1 : WS-GRANT-LEN)
                = WS-GRANT-PATH (1 : WS-GRANT-LEN)
            PERFORM RANK-OF-GRANT
            IF WS-ROLE-RANK > WS-BEST
                MOVE WS-ROLE-RANK TO WS-BEST
            END-IF
        END-IF
    END-IF.

RANK-OF-GRANT.
    EVALUATE WS-MB-ROW-ROLE (WS-I)
        WHEN "ADMIN"  MOVE 3 TO WS-ROLE-RANK
        WHEN "EDITOR" MOVE 2 TO WS-ROLE-RANK
        WHEN "VIEWER" MOVE 1 TO WS-ROLE-RANK
        WHEN OTHER    MOVE 0 TO WS-ROLE-RANK
    END-EVALUATE.

NEEDED-RANK.
    EVALUATE A-NEEDED
        WHEN "A" MOVE 3 TO WS-NEED
        WHEN "E" MOVE 2 TO WS-NEED
        WHEN OTHER MOVE 1 TO WS-NEED
    END-EVALUATE.

AUDIT-DECISION.
    IF A-AUDIT = "Y"
        IF A-RESULT = "A"
            MOVE "ALLOW" TO WS-EFFECT
        ELSE
            MOVE "DENY" TO WS-EFFECT
        END-IF
        CALL "10AUDTE0" USING A-USER-ID A-ACTION A-RTYPE
                              A-RID WS-EFFECT
    END-IF.
END PROGRAM "10AUTHC0".
