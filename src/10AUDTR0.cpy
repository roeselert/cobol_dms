*> 10AUDTR0.cpy — audit_log_entry record (VSAM KSDS AUDITLOG).
*> Primary key :PFX:-ID, alternate key :PFX:-TS (duplicates).
*> :PFX:-USER is deliberately NOT checked against USERS: audit rows
*> must survive user deletion (invariant, CLAUDE.md).
01 :PFX:-REC.
   05 :PFX:-ID          PIC X(36).
   05 :PFX:-USER        PIC X(36).
   05 :PFX:-ACTION      PIC X(10).
   05 :PFX:-RTYPE       PIC X(20).
   05 :PFX:-RID         PIC X(36).
   05 :PFX:-EFFECT      PIC X(5).
   05 :PFX:-TS          PIC 9(13).
