*> 30METAR0.cpy — document metadata record (VSAM KSDS DOCMETA).
*> Primary key :PFX:-DOC. Version 0 = unconfirmed AI suggestion,
*> >= 1 = user-confirmed. Indexing flag: MANUAL_INDEXING | REVIEW |
*> spaces (none). Mirrors document_metadata.
01 :PFX:-REC.
   05 :PFX:-DOC         PIC X(36).
   05 :PFX:-DATE        PIC X(10).
   05 :PFX:-CLASS       PIC X(50).
   05 :PFX:-EXTRACTED   PIC X.
   05 :PFX:-UPDATED-BY  PIC X(36).
   05 :PFX:-FLAG        PIC X(15).
   05 :PFX:-VERSION     PIC 9(4).
   05 :PFX:-CREATED     PIC 9(13).
   05 :PFX:-UPDATED     PIC 9(13).
