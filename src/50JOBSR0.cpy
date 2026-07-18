*> 50JOBSR0.cpy — conversion job record (VSAM KSDS CONVJOB).
*> Primary key :PFX:-ID; alternate key :PFX:-DOC (unique — one job per
*> document; reprocess resets the row in place). Status: QUEUED |
*> RUNNING | DONE | FAILED. LEASE 0 = no lease (SQL NULL).
01 :PFX:-REC.
   05 :PFX:-ID          PIC X(36).
   05 :PFX:-DOC         PIC X(36).
   05 :PFX:-STATUS      PIC X(10).
   05 :PFX:-ATTEMPTS    PIC 9(4).
   05 :PFX:-AVAILABLE   PIC 9(13).
   05 :PFX:-LEASE       PIC 9(13).
   05 :PFX:-ERROR       PIC X(500).
   05 :PFX:-CREATED     PIC 9(13).
