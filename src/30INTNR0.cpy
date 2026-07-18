*> 30INTNR0.cpy — detected document intent (VSAM KSDS DOCINTNT).
*> Primary key :PFX:-DOC (one row per document, replaced on
*> reprocess). :PFX:-FIELDS is the raw JSON object string of the
*> intent's extracted fields — display-only, never queried by field.
01 :PFX:-REC.
   05 :PFX:-DOC         PIC X(36).
   05 :PFX:-NAME        PIC X(100).
   05 :PFX:-FIELDS      PIC X(2000).
   05 :PFX:-CREATED     PIC 9(13).
