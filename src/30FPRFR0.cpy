*> 30FPRFR0.cpy — document file-plan-reference record (VSAM KSDS
*> DOCFPR). Primary key :PFX:-DOC; alternate key :PFX:-AKTE (dups) for
*> the Akte document listing. Version semantics as in 30METAR0.
01 :PFX:-REC.
   05 :PFX:-DOC         PIC X(36).
   05 :PFX:-FPR         PIC X(200).
   05 :PFX:-AKTE        PIC X(36).
   05 :PFX:-EXTRACTED   PIC X.
   05 :PFX:-UPDATED-BY  PIC X(36).
   05 :PFX:-VERSION     PIC 9(4).
   05 :PFX:-CREATED     PIC 9(13).
