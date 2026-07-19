*> 30DOCSR0.cpy — document record (VSAM KSDS DOCUMENT).
*> Primary key :PFX:-ID; alternate key :PFX:-INGEST (duplicates) for
*> the newest-first list scan. Mirrors table document.
01 :PFX:-REC.
   05 :PFX:-ID          PIC X(36).
   05 :PFX:-NAME        PIC X(255).
   05 :PFX:-UPLOADER    PIC X(36).
   05 :PFX:-ORG         PIC X(36).
   05 :PFX:-INGEST      PIC 9(13).
   05 :PFX:-CREATED     PIC 9(13).
