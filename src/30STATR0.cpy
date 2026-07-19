*> 30STATR0.cpy — document status record (VSAM KSDS DOCSTAT).
*> Primary key :PFX:-DOC (one row per document). Status values:
*> RECEIVED | CONVERTING | READY | FAILED. Mirrors document_status.
01 :PFX:-REC.
   05 :PFX:-DOC         PIC X(36).
   05 :PFX:-STATUS      PIC X(12).
   05 :PFX:-CHANGED-BY  PIC X(36).
   05 :PFX:-UPDATED     PIC 9(13).
   05 :PFX:-CREATED     PIC 9(13).
