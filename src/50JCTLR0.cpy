*> 50JCTLR0.cpy — control/param block for 50JOBSE0's worker ops.
*> The 4th CALL argument: header carries NOW/LEASE in and COUNT out;
*> the row table is filled by SCAN for the jobs boundary. The plain
*> ops (DGET/WRT/RSET/DONE/FAIL/RQUE) receive it but never touch it.
*>   CLAM in : CT-NOW, CT-LEASE (seconds)   out: claimed job in L-*-REC
*>   SWEP in : CT-NOW                        out: CT-COUNT re-queued
*>   SCAN                                    out: CT-COUNT + CT-ROW(*)
01 :PFX:-CTL.
   05 :PFX:-CT-NOW      PIC 9(13).
   05 :PFX:-CT-LEASE    PIC 9(9).
   05 :PFX:-CT-COUNT    PIC 9(4).
   05 :PFX:-CT-ROW OCCURS 100.
      10 :PFX:-CT-ID       PIC X(36).
      10 :PFX:-CT-DOC      PIC X(36).
      10 :PFX:-CT-STATUS   PIC X(10).
      10 :PFX:-CT-ATTEMPTS PIC 9(4).
      10 :PFX:-CT-ERROR    PIC X(500).
      10 :PFX:-CT-CREATED  PIC 9(13).
