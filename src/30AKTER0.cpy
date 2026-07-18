*> 30AKTER0.cpy — Akte record (VSAM KSDS AKTE).
*> Primary key :PFX:-ID; alternate key :PFX:-FPR (unique — Akte
*> uniqueness is GLOBAL on file_plan_reference, not per org unit;
*> the first creator's org unit wins). Mirrors table akte.
01 :PFX:-REC.
   05 :PFX:-ID          PIC X(36).
   05 :PFX:-FPR         PIC X(200).
   05 :PFX:-ORG         PIC X(36).
   05 :PFX:-CREATED     PIC 9(13).
